package com.example.plccontroller.data.plc

import android.util.Log
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcSerialDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class SerialModbusTransport(
    initialConfig: PlcCommunicationConfig,
) : ModbusTransport,
    AutoCloseable {
    @Volatile
    private var config: PlcCommunicationConfig = initialConfig

    @Volatile
    private var serialDiagnostic: PlcSerialDiagnostic = PlcSerialDiagnostic()

    private val ioLock = Any()
    private val mockDelegate = MockModbusTransport()

    private var serialFile: RandomAccessFile? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var openedPath: String? = null

    override fun updateConfig(config: PlcCommunicationConfig) {
        synchronized(ioLock) {
            if (this.config == config) {
                return
            }
            this.config = config
            closeLocked()
        }
    }

    override suspend fun transact(request: ByteArray): ByteArray {
        val snapshotConfig = config
        if (snapshotConfig.serialPortPath.isMockPath()) {
            return mockDelegate.transact(request)
        }
        return withContext(Dispatchers.IO) {
            synchronized(ioLock) {
                ensureOpenLocked(snapshotConfig)
                discardPendingInputLocked()
                logFrame("TX", request, snapshotConfig.serialPortPath)
                outputStream!!.write(request)
                outputStream!!.flush()
                if (snapshotConfig.serialPortPath.isSendOnlyPath()) {
                    val synthetic = synthesizeResponse(request)
                    logFrame("RX-SYNTH", synthetic, snapshotConfig.serialPortPath)
                    return@withContext synthetic
                }
                val response =
                    readResponseLocked(
                        request = request,
                        timeoutMs = snapshotConfig.normalizedReadTimeoutMs,
                    )
                logFrame("RX", response, snapshotConfig.serialPortPath)
                response
            }
        }
    }

    override fun serialDiagnostic(): PlcSerialDiagnostic {
        val snapshotConfig = config
        return if (snapshotConfig.serialPortPath.isMockPath()) {
            PlcSerialDiagnostic(
                configured = true,
                rawModeOk = true,
                method = "mock",
                summary = "Mock 串口",
                detail = "当前使用模拟 Modbus，不经过真实 tty/termios 层。",
                updatedAtMs = System.currentTimeMillis(),
            )
        } else {
            serialDiagnostic
        }
    }

    override fun close() {
        synchronized(ioLock) {
            closeLocked()
        }
    }

    override fun resetConnection() {
        synchronized(ioLock) {
            closeLocked()
        }
    }

    private fun ensureOpenLocked(activeConfig: PlcCommunicationConfig) {
        if (openedPath == activeConfig.serialPortPath &&
            serialFile != null &&
            inputStream != null &&
            outputStream != null
        ) {
            return
        }
        closeLocked()
        val targetFile = File(activeConfig.serialPortPath.devicePath())
        require(targetFile.exists()) {
            "Serial device not found: ${activeConfig.serialPortPath.devicePath()}"
        }
        val file = RandomAccessFile(targetFile, "rw")
        serialFile = file
        inputStream = FileInputStream(file.fd)
        outputStream = FileOutputStream(file.fd)
        openedPath = activeConfig.serialPortPath
        bestEffortConfigurePort(activeConfig, file.fd)
    }

    private fun closeLocked() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { serialFile?.close() }
        inputStream = null
        outputStream = null
        serialFile = null
        openedPath = null
        serialDiagnostic =
            PlcSerialDiagnostic(
                configured = false,
                rawModeOk = null,
                method = "closed",
                summary = "串口已关闭",
                detail = "等待下一次 Modbus 通讯重新打开并配置 RAW 模式。",
                updatedAtMs = System.currentTimeMillis(),
            )
    }

    private fun discardPendingInputLocked() {
        val input = inputStream ?: return
        runCatching {
            var totalDrained = 0
            val scratch = ByteArray(256)
            while (input.available() > 0) {
                val drained = input.read(scratch, 0, minOf(input.available(), scratch.size))
                if (drained <= 0) break
                totalDrained += drained
            }
            if (totalDrained > 0) {
                Log.d(TAG_SERIAL, "Discarded $totalDrained bytes of stale input data.")
                Thread.sleep(5) // Allow the line to settle after clearing noise
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun readResponseLocked(
        request: ByteArray,
        timeoutMs: Long,
    ): ByteArray {
        val expectedSlaveId = request[0].toInt() and 0xFF
        val expectedFunction = request[1].toInt() and 0xFF

        // Read first 2 bytes to determine if it's an exception or normal response
        val prefix = readExactLocked(2, timeoutMs)
        val responseSlaveId = prefix[0].toInt() and 0xFF
        val function = prefix[1].toInt() and 0xFF

        if (responseSlaveId != expectedSlaveId) {
            throw IllegalArgumentException("Modbus slave ID mismatch: expected $expectedSlaveId, got $responseSlaveId")
        }

        if ((function and 0x80) != 0) {
            val baseFunction = function and 0x7F
            if (baseFunction != expectedFunction) {
                throw IllegalArgumentException("Modbus function mismatch in exception: expected $expectedFunction, got $baseFunction")
            }
            // Exception response: Slave(1) + Func|80(1) + ExceptionCode(1) + CRC(2)
            // We already have 2 bytes, need 3 more
            return prefix + readExactLocked(3, timeoutMs)
        }

        if (function != expectedFunction) {
            throw IllegalArgumentException("Modbus function mismatch: expected $expectedFunction, got $function")
        }

        return when (function) {
            FUNCTION_READ_COILS,
            FUNCTION_READ_HOLDING_REGISTERS,
            -> {
                // Success: Slave(1) + Func(1) + ByteCount(1) + Data(N) + CRC(2)
                // We already have 2 bytes, need byte count (1) first
                val byteCountHeader = readExactLocked(1, timeoutMs)
                val byteCount = byteCountHeader[0].toInt() and 0xFF
                // Read the data payload (byteCount) + CRC (2 bytes)
                prefix + byteCountHeader + readExactLocked(byteCount + 2, timeoutMs)
            }
            FUNCTION_WRITE_SINGLE_COIL,
            FUNCTION_WRITE_SINGLE_REGISTER,
            FUNCTION_WRITE_MULTIPLE_COILS,
            FUNCTION_WRITE_MULTIPLE_REGISTERS,
            -> {
                // Success: Slave(1) + Func(1) + Data(4) + CRC(2)
                // We already have 2 bytes, need 6 more
                prefix + readExactLocked(6, timeoutMs)
            }
            else -> {
                // Fallback to 8 bytes total for unknown functions
                prefix + readExactLocked(6, timeoutMs)
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun readExactLocked(
        length: Int,
        timeoutMs: Long,
    ): ByteArray {
        val input = inputStream ?: throw IllegalStateException("Serial port is not open")
        val deadlineAt = System.currentTimeMillis() + timeoutMs.coerceAtLeast(100L)
        val buffer = ByteArray(length)
        var offset = 0

        while (offset < length) {
            val remaining = deadlineAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                val partialHex = buffer.copyOfRange(0, offset).toHexString()
                throw SocketTimeoutException("Timed out while reading $length bytes (got $offset bytes: $partialHex)")
            }

            // Use short sleep only if no data is available to avoid CPU hogging
            // but keep it responsive. At 19200 baud, 1 byte takes ~0.5ms.
            val available = input.available()
            if (available > 0) {
                val read = input.read(buffer, offset, minOf(available, length - offset))
                when {
                    read > 0 -> offset += read
                    read < 0 -> throw EOFException("Serial stream closed while reading response")
                    else -> { /* No data yet, loop again */ }
                }
            } else {
                Thread.sleep(minOf(remaining, 2L))
            }
        }
        return buffer
    }

    private fun bestEffortConfigurePort(
        activeConfig: PlcCommunicationConfig,
        fileDescriptor: FileDescriptor,
    ) {
        val nativeResult = NativeSerialPortConfigurator.configureBinary(fileDescriptor, activeConfig)
        if (nativeResult.isSuccess) {
            val nativeState = nativeResult.getOrThrow()
            val sttyState = logCurrentSttyState(activeConfig)
            updateSerialDiagnostic(
                rawModeOk = true,
                method = "native termios",
                summary = "RAW 正常",
                detail = "native 已关闭 IUCLC/ISTRIP/IXON/IXOFF；$nativeState；${summarizeSttyRawFlags(sttyState)}",
            )
            return
        }

        val nativeFailureMessage = nativeResult.exceptionOrNull()?.message ?: "unknown native failure"
        Log.w(
            TAG_SERIAL,
            "Falling back to stty serial configuration for ${activeConfig.serialPortPath}: " +
                nativeFailureMessage,
        )
        buildSttyCommands(activeConfig).forEach { command ->
            runSttyCommand(activeConfig, command, "Configuring port")
        }
        val sttyState = logCurrentSttyState(activeConfig)
        val rawModeOk = sttyRawModeOk(sttyState)
        updateSerialDiagnostic(
            rawModeOk = rawModeOk,
            method = "stty fallback",
            summary =
                when (rawModeOk) {
                    true -> "RAW 可能正常"
                    false -> "RAW 可疑"
                    null -> "RAW 未确认"
                },
            detail = "native 配置失败：$nativeFailureMessage；已尝试 stty 兜底；${summarizeSttyRawFlags(sttyState)}",
        )
    }

    private fun updateSerialDiagnostic(
        rawModeOk: Boolean?,
        method: String,
        summary: String,
        detail: String,
    ) {
        val next =
            PlcSerialDiagnostic(
                configured = true,
                rawModeOk = rawModeOk,
                method = method,
                summary = summary,
                detail = detail,
                updatedAtMs = System.currentTimeMillis(),
            )
        serialDiagnostic = next
        Log.d(TAG_SERIAL, "Serial RAW diagnostic: ${next.summary}; method=${next.method}; detail=${next.detail}")
    }

    private fun runSttyCommand(
        activeConfig: PlcCommunicationConfig,
        command: String,
        label: String,
    ) {
        Log.d(TAG_SERIAL, "$label with command: $command")
        runCatching {
            val process =
                ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(STTY_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG_SERIAL, "$label timed out for ${activeConfig.serialPortPath}")
                return
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Log.w(TAG_SERIAL, "$label exited with $exitCode for ${activeConfig.serialPortPath}: $output")
            }
        }.onFailure { error ->
            Log.w(TAG_SERIAL, "$label skipped for ${activeConfig.serialPortPath}: ${error.message}")
        }
    }

    private fun logCurrentSttyState(activeConfig: PlcCommunicationConfig): String? {
        val directState =
            logSttyStateCommand(
                activeConfig = activeConfig,
                command = "stty -F ${activeConfig.serialPortPath.devicePath()} -a",
                label = "Current stty state",
            )
        val redirectedState =
            logSttyStateCommand(
                activeConfig = activeConfig,
                command = "stty -a < ${activeConfig.serialPortPath.devicePath()}",
                label = "Current redirected stty state",
            )
        return directState ?: redirectedState
    }

    private fun logSttyStateCommand(
        activeConfig: PlcCommunicationConfig,
        command: String,
        label: String,
    ): String? {
        Log.d(TAG_SERIAL, "$label command: $command")
        return runCatching {
            val process =
                ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(STTY_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG_SERIAL, "$label timed out for ${activeConfig.serialPortPath}")
                null
            } else {
                val output =
                    process.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                        .trim()
                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    Log.d(TAG_SERIAL, "$label for ${activeConfig.serialPortPath}: $output")
                    output
                } else {
                    Log.w(TAG_SERIAL, "$label exited with $exitCode for ${activeConfig.serialPortPath}: $output")
                    null
                }
            }
        }.getOrElse { error ->
            Log.w(TAG_SERIAL, "$label skipped for ${activeConfig.serialPortPath}: ${error.message}")
            null
        }
    }

    private fun sttyRawModeOk(sttyState: String?): Boolean? {
        val tokens = sttyState?.sttyTokens() ?: return null
        return CRITICAL_RAW_FLAGS.none { it in tokens } && CRITICAL_RAW_FLAGS.all { "-$it" in tokens }
    }

    private fun summarizeSttyRawFlags(sttyState: String?): String {
        val tokens = sttyState?.sttyTokens() ?: return "stty 状态不可读"
        val enabledFlags = CRITICAL_RAW_FLAGS.filter { it in tokens }
        return if (enabledFlags.isEmpty()) {
            "stty 关键转换均已关闭：${CRITICAL_RAW_FLAGS.joinToString("/") { "-$it" }}"
        } else {
            "stty 仍开启高风险项：${enabledFlags.joinToString("/")}"
        }
    }

    private fun buildSttyCommands(activeConfig: PlcCommunicationConfig): List<String> {
        val cs =
            when (activeConfig.dataBits) {
                7 -> "cs7"
                else -> "cs8"
            }
        val stop = if (activeConfig.stopBits >= 2) "cstopb" else "-cstopb"

        val parity =
            when (activeConfig.parityName.uppercase()) {
                "EVEN" -> "parenb -parodd"
                "ODD" -> "parenb parodd"
                else -> "-parenb"
            }

        // Android industrial boards often have IUCLC (uppercase to lowercase)
        // enabled by default which flips bit 0x20 on bytes matching 'A'-'Z' or 'Ö' etc.
        // We explicitly disable it and ensure a truly raw connection.
        val devicePath = activeConfig.serialPortPath.devicePath()
        val baseCommand =
            buildString {
                append("stty -F ")
                append(devicePath)
                append(' ')
                append(activeConfig.baudRate)
                append(' ')
                append(cs)
                append(' ')
                append(stop)
                append(' ')
                append(parity)
                append(
                    " raw -echo clocal cread" +
                        " -ixon -ixoff -iuclc -ixany -imaxbel" +
                        " -parmrk -inpck -istrip -ignpar -brkint" +
                        " -isig -icanon -iexten" +
                        " -opost -onlcr -ocrnl -onocr -onlret" +
                        " -icrnl -inlcr -igncr",
                )
            }
        val binarySafetyCommand =
            buildString {
                append("stty -F ")
                append(devicePath)
                append(
                    " -istrip -inpck -ignpar -ixon -ixoff -iuclc -ixany -imaxbel -iutf8" +
                        " -parmrk -brkint -icrnl -inlcr -igncr -opost",
                )
            }
        val redirectedBinarySafetyCommand =
            buildString {
                append("stty")
                append(
                    " -istrip -inpck -ignpar -ixon -ixoff -iuclc -ixany -imaxbel -iutf8" +
                        " -parmrk -brkint -icrnl -inlcr -igncr -opost",
                )
                append(" < ")
                append(devicePath)
            }
        return listOf(baseCommand, binarySafetyCommand, redirectedBinarySafetyCommand)
    }

    private fun logFrame(
        direction: String,
        frame: ByteArray,
        path: String,
    ) {
        Log.d(TAG_SERIAL, "$direction[$path] ${frame.toHexString()}")
    }

    private fun synthesizeResponse(request: ByteArray): ByteArray {
        val slaveId = request.getOrNull(0) ?: 0x01
        val function = request.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        return when (function) {
            FUNCTION_READ_COILS -> {
                val quantity = readUShort(request, 4)
                val byteCount = ((quantity + 7) / 8).coerceAtLeast(1)
                val payload = ByteArray(3 + byteCount)
                payload[0] = slaveId
                payload[1] = function.toByte()
                payload[2] = byteCount.toByte()
                payload + crc16(payload)
            }
            FUNCTION_READ_HOLDING_REGISTERS -> {
                val quantity = readUShort(request, 4)
                val byteCount = (quantity * 2).coerceAtLeast(2)
                val payload = ByteArray(3 + byteCount)
                payload[0] = slaveId
                payload[1] = function.toByte()
                payload[2] = byteCount.toByte()
                payload + crc16(payload)
            }
            FUNCTION_WRITE_SINGLE_COIL,
            FUNCTION_WRITE_SINGLE_REGISTER,
            FUNCTION_WRITE_MULTIPLE_COILS,
            FUNCTION_WRITE_MULTIPLE_REGISTERS,
            -> {
                val payload = request.copyOfRange(0, 6)
                payload + crc16(payload)
            }
            else -> {
                val payload = request.copyOfRange(0, minOf(6, request.size))
                payload + crc16(payload)
            }
        }
    }
}
