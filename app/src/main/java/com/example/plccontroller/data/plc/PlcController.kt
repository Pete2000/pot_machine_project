package com.example.plccontroller.data.plc

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
    transport: ModbusTransport,
) {
    private val frameClient = ModbusFrameClient(initialSlaveId, transport)
    private var lastHeartbeatValue: Boolean = false
    private val heartbeatLock = Mutex()

    var chaosMonkeyDisconnectActive: Boolean = false
    var chaosMonkeyEStopActive: Boolean = false

    fun updateSlaveId(newId: Int) {
        frameClient.updateSlaveId(newId)
    }

    fun updateCommunicationConfig(config: PlcCommunicationConfig) {
        frameClient.updateCommunicationConfig(config)
    }

    fun serialDiagnostic(): PlcSerialDiagnostic = frameClient.serialDiagnostic()

    fun resetCommunicationConnection() {
        frameClient.resetConnection()
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
    ): List<Boolean> = frameClient.readCoils(startAddress, count)

    private suspend fun readHoldingRegisters(
        startRegisterAddress: Int,
        count: Int,
    ): List<Int> = frameClient.readHoldingRegisters(startRegisterAddress, count)

    private suspend fun writeSingleCoil(
        address: Int,
        enabled: Boolean,
        tolerateCrcFailure: Boolean = false,
    ) {
        frameClient.writeSingleCoil(address, enabled, tolerateCrcFailure)
    }

    private suspend fun writeSingleRegister(
        registerAddress: Int,
        value: Int,
    ) {
        frameClient.writeSingleRegister(registerAddress, value)
    }

    private suspend fun writeMultipleRegisters(
        startRegisterAddress: Int,
        values: List<Int>,
    ) {
        frameClient.writeMultipleRegisters(startRegisterAddress, values)
    }
}

class PlcCommunicationUnavailableException(
    message: String,
) : IllegalStateException(message)
