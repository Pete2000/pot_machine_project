package com.example.plccontroller.data.plc

import android.util.Log
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.domain.PlcRegisterMap
import com.example.plccontroller.domain.PlcSerialDiagnostic
import com.example.plccontroller.domain.PlcWriteResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlcController(
    initialSlaveId: Int,
    private val registerMap: PlcRegisterMap,
    private val transport: ModbusTransport,
) {
    private var slaveId: Int = initialSlaveId
    private var lastHeartbeatValue: Boolean = false
    private val heartbeatLock = Mutex()

    var chaosMonkeyDisconnectActive: Boolean = false
    var chaosMonkeyEStopActive: Boolean = false

    fun updateSlaveId(newId: Int) {
        slaveId = newId
    }

    fun updateCommunicationConfig(config: PlcCommunicationConfig) {
        updateSlaveId(config.slaveId)
        transport.updateConfig(config)
    }

    fun serialDiagnostic(): PlcSerialDiagnostic = transport.serialDiagnostic()

    fun resetCommunicationConnection() {
        transport.resetConnection()
    }

    suspend fun pollSnapshot(): PlcPollingSnapshot {
        val warnings = mutableListOf<String>()
        var successCount = 0

        val mirrors =
            runCatching {
                readCoils(startAddress = 300, count = 40)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "I/O线圈读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        // 分段读取寄存器，避免超出 PLC 响应范围（参考说明书：温度 D200-D201）
        val tempRegisters =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 200, count = 2)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "温度寄存器读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val levelRegisters =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 210, count = 1)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "液位寄存器读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        // 重组 holding 列表，确保索引位置与 domain 模型一致
        val holding = IntArray(15)
        tempRegisters.forEachIndexed { i, v -> if (i < 2) holding[i] = v }
        levelRegisters.forEachIndexed { i, v -> if (i < 1) holding[10] = v }
        val holdingList = holding.toList()

        val phase =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 320, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "相位状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val heater =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 350, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "加热状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val emergency =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 370, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "应急状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val heartbeat =
            runCatching {
                readCoils(startAddress = 400, count = 1)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "心跳线圈读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        require(successCount > 0) {
            warnings.joinToString("；").ifBlank { "PLC 轮询失败：未获取到任何有效数据块" }
        }

        return PlcPollingSnapshot(
            mirrorBits = mirrors,
            holdingRegisters = holdingList,
            phaseStatusRegisters = phase,
            heaterStatusRegisters = heater,
            emergencyStatusRegisters = emergency,
            pollWarnings = warnings,
            serialDiagnostic = serialDiagnostic(),
            lastHeartbeatValue = heartbeat.firstOrNull() ?: lastHeartbeatValue,
            lastSuccessfulPollAtMs = System.currentTimeMillis(),
        )
    }

    suspend fun pollSnapshotStable(): PlcPollingSnapshot {
        if (chaosMonkeyDisconnectActive) {
            throw PlcCommunicationUnavailableException("ChaosMonkey: Simulated Disconnect")
        }
        val warnings = mutableListOf<String>()
        var successCount = 0

        val mirrors =
            runCatching {
                readCoils(startAddress = 300, count = 40)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "I/O 线圈读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val environmentRegisters =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 200, count = 11)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "温度/液位寄存器读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        if (successCount == 0) {
            throw PlcCommunicationUnavailableException(
                "PLC 通讯不可用：${warnings.joinToString("；")}",
            )
        }

        val holdingList =
            IntArray(11)
                .also { merged ->
                    environmentRegisters.forEachIndexed { index, value ->
                        if (index < merged.size) {
                            merged[index] = value
                        }
                    }
                }.toList()

        val phase =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 320, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "相位状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val heater =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 350, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "加热状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val emergency =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 370, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "应急状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        require(successCount > 0) {
            warnings.joinToString("；").ifBlank { "PLC 轮询失败：未获取到任何有效数据块" }
        }

        return PlcPollingSnapshot(
            mirrorBits =
                if (chaosMonkeyEStopActive) {
                    // If E-Stop is active, flip bit 0 (normally closed) to 0 (open)
                    mirrors.toMutableList().apply { if (isNotEmpty()) this[0] = false }
                } else {
                    mirrors
                },
            holdingRegisters = holdingList,
            phaseStatusRegisters = phase,
            heaterStatusRegisters = heater,
            emergencyStatusRegisters = emergency,
            pollWarnings = warnings,
            serialDiagnostic = serialDiagnostic(),
            lastHeartbeatValue = lastHeartbeatValue,
            lastSuccessfulPollAtMs = System.currentTimeMillis(),
        )
    }

    suspend fun pollRegisterSnapshotStable(
        blockStart: Int = 0,
        blockCount: Int = 0,
    ): PlcPollingSnapshot {
        if (chaosMonkeyDisconnectActive) {
            throw java.net.SocketTimeoutException("ChaosMonkey: Simulated Disconnect")
        }
        if (blockStart in setOf(200, 320, 350, 370)) {
            return pollSingleRegisterBlockStable(blockStart, blockCount)
        }

        val warnings = mutableListOf<String>()
        var successCount = 0

        val environmentRegisters =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 200, count = 11)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "温度/液位寄存器读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val holdingList =
            IntArray(11)
                .also { merged ->
                    environmentRegisters.forEachIndexed { index, value ->
                        if (index < merged.size) {
                            merged[index] = value
                        }
                    }
                }.toList()

        val phase =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 320, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "相位状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val heater =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 350, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "加热状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        val emergency =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 370, count = 10)
            }.onSuccess {
                successCount += 1
            }.getOrElse { error ->
                warnings += "应急状态区读取失败：${error.message ?: "未知错误"}"
                emptyList()
            }

        require(successCount > 0) {
            warnings.joinToString("；").ifBlank { "PLC 寄存器轮询失败：未获取到任何有效数据块" }
        }

        return PlcPollingSnapshot(
            holdingRegisters = holdingList,
            phaseStatusRegisters = phase,
            heaterStatusRegisters = heater,
            emergencyStatusRegisters = emergency,
            pollWarnings = warnings,
            serialDiagnostic = serialDiagnostic(),
            lastHeartbeatValue = lastHeartbeatValue,
            lastSuccessfulPollAtMs = System.currentTimeMillis(),
        )
    }

    private suspend fun pollSingleRegisterBlockStable(
        blockStart: Int,
        requestedCount: Int,
    ): PlcPollingSnapshot {
        val defaultCount =
            when (blockStart) {
                200 -> 11
                320, 350, 370 -> 10
                else -> error("Unsupported register-only block: D$blockStart")
            }
        val count = requestedCount.takeIf { it in 1..125 } ?: defaultCount
        val registers =
            runCatching {
                readHoldingRegisters(startRegisterAddress = blockStart, count = count)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "03调试块 D$blockStart~D${blockStart + count - 1} 读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        return when (blockStart) {
            200 ->
                PlcPollingSnapshot(
                    holdingRegisters = registers,
                    serialDiagnostic = serialDiagnostic(),
                    lastHeartbeatValue = lastHeartbeatValue,
                    lastSuccessfulPollAtMs = System.currentTimeMillis(),
                )
            320 ->
                PlcPollingSnapshot(
                    phaseStatusRegisters = registers,
                    serialDiagnostic = serialDiagnostic(),
                    lastHeartbeatValue = lastHeartbeatValue,
                    lastSuccessfulPollAtMs = System.currentTimeMillis(),
                )
            350 ->
                PlcPollingSnapshot(
                    heaterStatusRegisters = registers,
                    serialDiagnostic = serialDiagnostic(),
                    lastHeartbeatValue = lastHeartbeatValue,
                    lastSuccessfulPollAtMs = System.currentTimeMillis(),
                )
            370 ->
                PlcPollingSnapshot(
                    emergencyStatusRegisters = registers,
                    serialDiagnostic = serialDiagnostic(),
                    lastHeartbeatValue = lastHeartbeatValue,
                    lastSuccessfulPollAtMs = System.currentTimeMillis(),
                )
            else -> error("Unsupported register-only block: D$blockStart")
        }
    }

    suspend fun pollSnapshotStableClean(): PlcPollingSnapshot {
        val environmentRegistersOnly =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 200, count = 11)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：温度/液位寄存器读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        val mirrors =
            runCatching {
                readCoils(startAddress = 300, count = 40)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：I/O 线圈读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        val phaseOnly =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 320, count = 10)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：相位状态区读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        val heaterOnly =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 350, count = 10)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：加热状态区读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        val emergencyOnly =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 370, count = 10)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：应急状态区读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        val heartbeat =
            runCatching {
                readCoils(startAddress = 400, count = 1)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 快速探测失败：心跳线圈读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        return PlcPollingSnapshot(
            mirrorBits = mirrors,
            holdingRegisters =
                IntArray(11)
                    .also { merged ->
                        environmentRegistersOnly.forEachIndexed { index, value ->
                            if (index < merged.size) merged[index] = value
                        }
                    }.toList(),
            phaseStatusRegisters = phaseOnly,
            heaterStatusRegisters = heaterOnly,
            emergencyStatusRegisters = emergencyOnly,
            pollWarnings = emptyList(),
            serialDiagnostic = serialDiagnostic(),
            lastHeartbeatValue = heartbeat.firstOrNull() ?: lastHeartbeatValue,
            lastSuccessfulPollAtMs = System.currentTimeMillis(),
        )
    }

    suspend fun pollPhaseStatusClean(): PlcPollingSnapshot {
        val phaseOnly =
            runCatching {
                readHoldingRegisters(startRegisterAddress = 320, count = 10)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "PLC 相位状态区读取失败：${error.message ?: "未知错误"}",
                    error,
                )
            }

        return PlcPollingSnapshot(
            phaseStatusRegisters = phaseOnly,
            pollWarnings = emptyList(),
            serialDiagnostic = serialDiagnostic(),
            lastSuccessfulPollAtMs = System.currentTimeMillis(),
        )
    }

    suspend fun writeHeartbeat(enabled: Boolean) {
        heartbeatLock.withLock {
            writeSingleCoil(registerMap.heartbeatCoil, enabled, tolerateCrcFailure = true)
            lastHeartbeatValue = enabled
        }
    }

    suspend fun toggleHeartbeat(): Boolean =
        heartbeatLock.withLock {
            val nextValue = !lastHeartbeatValue
            writeSingleCoil(registerMap.heartbeatCoil, nextValue, tolerateCrcFailure = true)
            lastHeartbeatValue = nextValue
            nextValue
        }

    suspend fun writePhaseCommand(command: PlcPhaseCommand) {
        writeMultipleRegisters(startRegisterAddress = 300, values = command.toRegisters())
    }

    suspend fun writeHeaterCommand(command: PlcHeaterCommand) {
        writeMultipleRegisters(startRegisterAddress = 340, values = command.toRegisters())
    }

    suspend fun writeEmergencyWaterCommand(command: PlcEmergencyWaterCommand) {
        writeMultipleRegisters(startRegisterAddress = 360, values = command.toRegisters())
    }

    suspend fun dispatch(order: Order): PlcWriteResult {
        val recipeCodeInt =
            order.recipeCode.toIntOrNull() ?: return PlcWriteResult(
                success = false,
                message = "Invalid recipe code: ${order.recipeCode} (must be an integer)",
            )
        val prototype = registerMap.prototypeDispatchRegisters
        writeMultipleRegisters(
            startRegisterAddress = prototype.recipeCodeRegister,
            values =
                listOf(
                    recipeCodeInt,
                    order.quantity,
                    order.targetTemperature,
                    order.cookSeconds,
                    order.spiceLevel,
                ),
        )
        writeSingleRegister(prototype.startCommandRegister, 1)
        return PlcWriteResult(
            success = true,
            message = "Order ${order.id} dispatched to PLC",
        )
    }

    private suspend fun readCoils(
        startAddress: Int,
        count: Int,
    ): List<Boolean> {
        require(count in 1..2000) { "Invalid coil count: $count" }
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_READ_COILS.toByte(),
                ((startAddress shr 8) and 0xFF).toByte(),
                (startAddress and 0xFF).toByte(),
                ((count shr 8) and 0xFF).toByte(),
                (count and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        val expectedByteCount = (count + 7) / 8
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_READ_COILS, 3 + expectedByteCount + 2, "coil")
        require((response[2].toInt() and 0xFF) == expectedByteCount) { "Unexpected Modbus coil byte count" }
        val values = mutableListOf<Boolean>()
        repeat(count) { index ->
            val byteIndex = 3 + (index / 8)
            val bitIndex = index % 8
            val bit = ((response[byteIndex].toInt() shr bitIndex) and 0x01) == 1
            values += bit
        }
        return values
    }

    private suspend fun readHoldingRegisters(
        startRegisterAddress: Int,
        count: Int,
    ): List<Int> {
        require(count in 1..125) { "Invalid register count: $count" }
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_READ_HOLDING_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((count shr 8) and 0xFF).toByte(),
                (count and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))

        // CRC only protects received bytes; byte count must still match the request.
        val expectedByteCount = count * 2
        ModbusResponseValidator.validate(
            response = response,
            expectedSlaveId = slaveId,
            expectedFunction = FUNCTION_READ_HOLDING_REGISTERS,
            expectedSize = 3 + expectedByteCount + 2,
            context = "register",
        )

        val byteCount = response[2].toInt() and 0xFF
        require(byteCount == expectedByteCount) {
            "Unexpected Modbus register byte count: $byteCount (expected $expectedByteCount)"
        }

        return List(count) { index ->
            val high = response[3 + index * 2].toInt() and 0xFF
            val low = response[4 + index * 2].toInt() and 0xFF
            (high shl 8) or low
        }
    }

    private suspend fun writeSingleCoil(
        address: Int,
        enabled: Boolean,
        tolerateCrcFailure: Boolean = false,
    ) {
        val value = if (enabled) MODBUS_TRUE_WORD else MODBUS_FALSE_WORD
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_SINGLE_COIL.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        try {
            ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_SINGLE_COIL, 8, "write-coil")
        } catch (error: IllegalArgumentException) {
            val responseEchoMatches = response.size >= 6 && response.copyOfRange(0, 6).contentEquals(payload)
            val isCrcError = error.message.orEmpty().contains("CRC", ignoreCase = true)
            if (tolerateCrcFailure && responseEchoMatches && isCrcError) {
                Log.w(
                    TAG_SERIAL,
                    "Heartbeat coil response CRC invalid but payload echo matched; accepting as compatibility workaround.",
                )
                return
            }
            throw error
        }
        require(response.copyOfRange(0, 6).contentEquals(payload)) { "Modbus write-coil response is not an echo" }
    }

    private suspend fun writeSingleRegister(
        registerAddress: Int,
        value: Int,
    ) {
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_SINGLE_REGISTER.toByte(),
                ((registerAddress shr 8) and 0xFF).toByte(),
                (registerAddress and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_SINGLE_REGISTER, 8, "write-register")
        require(response.copyOfRange(0, 6).contentEquals(payload)) { "Modbus write-register response is not an echo" }
    }

    private suspend fun writeMultipleRegisters(
        startRegisterAddress: Int,
        values: List<Int>,
    ) {
        require(values.isNotEmpty()) { "At least one register value is required" }
        require(values.size <= MAX_WRITE_MULTIPLE_REGISTER_COUNT) {
            "Cannot write more than $MAX_WRITE_MULTIPLE_REGISTER_COUNT registers in one Modbus frame"
        }

        val registerBytes =
            values
                .flatMap { value ->
                    listOf(
                        ((value shr 8) and 0xFF).toByte(),
                        (value and 0xFF).toByte(),
                    )
                }.toByteArray()
        val registerCount = values.size
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_MULTIPLE_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((registerCount shr 8) and 0xFF).toByte(),
                (registerCount and 0xFF).toByte(),
                registerBytes.size.toByte(),
            ) + registerBytes
        val response = transport.transact(payload + crc16(payload))
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_MULTIPLE_REGISTERS, 8, "write-multiple-registers")
        val expectedPrefix =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_MULTIPLE_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((registerCount shr 8) and 0xFF).toByte(),
                (registerCount and 0xFF).toByte(),
            )
        require(response.copyOfRange(0, 6).contentEquals(expectedPrefix)) { "Modbus write-multiple-registers response mismatch" }
    }
}

class PlcCommunicationUnavailableException(
    message: String,
) : IllegalStateException(message)
