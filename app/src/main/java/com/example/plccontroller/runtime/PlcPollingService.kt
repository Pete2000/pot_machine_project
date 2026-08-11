package com.example.plccontroller.runtime

import android.util.Log
import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.NoOpMachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import com.example.plccontroller.data.local.auditPlcCommand
import com.example.plccontroller.data.plc.PlcCommunicationUnavailableException
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcHeaterCommand
import com.example.plccontroller.data.toCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class PlcPollingService(
    private val plcController: PlcController,
    private val settingsStore: SettingsStore,
    private val runtimeStore: MachineRuntimeStore,
    private val auditStore: MachineAuditStore = NoOpMachineAuditStore,
    parentScope: CoroutineScope? = null,
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    fun start() {
        if (pollingJob?.isActive == true) return

        pollingJob =
            scope.launch {
                var lastHeartbeatAtMs = 0L
                var consecutiveFailures = 0
                var heaterEnableSentForConnection = false
                var activeCommunicationFaultId: Long? = null

                runtimeStore.update { state ->
                    state.copy(
                        plcState =
                            state.plcState.takeIf { it == PlcConnectionState.Fault }
                                ?: PlcConnectionState.Connecting,
                    )
                }

                while (isActive) {
                    val config = settingsStore.snapshot()
                    val runtime = runtimeStore.snapshot()
                    val pollingInterval =
                        if (runtime.automationEnabled || runtime.currentOrder != null) {
                            config.activePollingIntervalMs
                        } else {
                            config.idlePollingIntervalMs
                        }.coerceAtLeast(100L)

                    if (runtime.maintenanceState.active) {
                        delay(pollingInterval)
                        continue
                    }

                    runCatching {
                        if (config.registerOnlyMode) {
                            plcController.pollRegisterSnapshotStable(
                                blockStart = config.registerOnlyBlockStart,
                                blockCount = config.registerOnlyBlockCount,
                            )
                        } else {
                            plcController.pollSnapshotStable()
                        }
                    }.onSuccess { snapshot ->
                        consecutiveFailures = 0
                        if (activeCommunicationFaultId != null) {
                            auditStore.resolveFault(activeCommunicationFaultId)
                            activeCommunicationFaultId = null
                        }
                        var latestSnapshot = snapshot
                        var heartbeatWarning: String? = null
                        var heaterEnableInfo: String? = null
                        var heaterEnableWarning: String? = null
                        val now = System.currentTimeMillis()

                        if (!config.registerOnlyMode && now - lastHeartbeatAtMs >= config.heartbeatPeriodMs.coerceAtLeast(200L)) {
                            runCatching {
                                val nextHeartbeatValue = plcController.toggleHeartbeat()
                                lastHeartbeatAtMs = now
                                latestSnapshot = latestSnapshot.copy(lastHeartbeatValue = nextHeartbeatValue)
                            }.onFailure { error ->
                                heartbeatWarning = "心跳写入失败：${error.message ?: "未知错误"}"
                                Log.w(TAG_PLC_POLL, heartbeatWarning ?: "心跳写入失败", error)
                            }
                        }

                        if (
                            shouldAutoSendHeaterEnable(
                                config = config,
                                heaterEnableSentForConnection = heaterEnableSentForConnection,
                                heartbeatWarning = heartbeatWarning,
                                heaterActuatorConfigEditActive = runtimeStore.snapshot().heaterActuatorConfigEditActive,
                            ) &&
                            !runtimeStore.snapshot().maintenanceState.active
                        ) {
                            val commandId = settingsStore.nextPlcCommandId()
                            runCatching {
                                val command = config.toAutoHeaterEnableCommand(commandId)
                                auditStore.auditPlcCommand(
                                    PlcCommandAuditRequest(
                                        commandId = commandId,
                                        commandType = "HEATER_ENABLE",
                                        commandName = "Automatic heater enable after connection",
                                        requestRegisterStart = 100,
                                        requestPayloadJson = command.toRegisters().joinToString(",", "[", "]"),
                                    ),
                                ) {
                                    plcController.writeHeaterCommand(command)
                                }
                                heaterEnableSentForConnection = true
                                heaterEnableInfo = "Heater auto enable sent: cmd=$commandId"
                                Log.d(TAG_PLC_POLL, heaterEnableInfo ?: "Heater auto enable sent")
                            }.onFailure { error ->
                                heaterEnableSentForConnection = false
                                heaterEnableWarning = "Heater auto enable failed: ${error.message ?: "unknown error"}"
                                Log.w(TAG_PLC_POLL, heaterEnableWarning ?: "Heater auto enable failed", error)
                            }
                        }

                        val mergedWarnings =
                            buildList {
                                heartbeatWarning?.let(::add)
                                heaterEnableWarning?.let(::add)
                                addAll(latestSnapshot.pollWarnings)
                            }.distinct()
                        val mergedSnapshot =
                            if (mergedWarnings == latestSnapshot.pollWarnings) {
                                latestSnapshot
                            } else {
                                latestSnapshot.copy(pollWarnings = mergedWarnings)
                            }

                        val pollLabel =
                            if (config.registerOnlyMode) {
                                if (config.registerOnlyBlockStart > 0) {
                                    val countText =
                                        config.registerOnlyBlockCount
                                            .takeIf { it > 0 }
                                            ?.let { " count=$it" }
                                            .orEmpty()
                                    "register-only poll D${config.registerOnlyBlockStart}$countText"
                                } else {
                                    "register-only poll"
                                }
                            } else {
                                "background poll"
                            }
                        if (mergedSnapshot.pollWarnings.isEmpty()) {
                            Log.d(
                                TAG_PLC_POLL,
                                "$pollLabel ok temp0=${mergedSnapshot.temperatureSensor0} " +
                                    "temp1=${mergedSnapshot.temperatureSensor1} " +
                                    "level=${mergedSnapshot.liquidLevelState} " +
                                    "hb=${mergedSnapshot.lastHeartbeatValue} " +
                                    "serialRaw=${mergedSnapshot.serialDiagnostic.summary}/${mergedSnapshot.serialDiagnostic.method} " +
                                    "holding=${mergedSnapshot.holdingRegisters.joinToString(",")} " +
                                    "inputs=${mergedSnapshot.inputSummary} " +
                                    "outputs=${mergedSnapshot.outputSummary}",
                            )
                        } else {
                            Log.w(
                                TAG_PLC_POLL,
                                "$pollLabel partial warnings=${mergedSnapshot.pollWarnings.joinToString(" | ")} " +
                                    "temp0=${mergedSnapshot.temperatureSensor0} " +
                                    "temp1=${mergedSnapshot.temperatureSensor1} " +
                                    "level=${mergedSnapshot.liquidLevelState} " +
                                    "hb=${mergedSnapshot.lastHeartbeatValue} " +
                                    "serialRaw=${mergedSnapshot.serialDiagnostic.summary}/${mergedSnapshot.serialDiagnostic.method} " +
                                    "holding=${mergedSnapshot.holdingRegisters.joinToString(",")}",
                            )
                        }

                        runtimeStore.update { state ->
                            state.copy(
                                plcState = PlcConnectionState.Connected,
                                communicationConfig = state.communicationConfig ?: config.toCommunicationConfig(),
                                plcPollingSnapshot = mergedSnapshot,
                                lastMessage =
                                    heaterEnableInfo
                                        ?: mergedSnapshot.pollWarnings.firstOrNull()
                                        ?: if (config.registerOnlyMode) "PLC 寄存器轮询正常（03调试模式）" else "PLC 轮询正常",
                            )
                        }
                    }.onFailure { error ->
                        consecutiveFailures += 1
                        val serialDiagnostic = plcController.serialDiagnostic()
                        val previousState = runtimeStore.snapshot()
                        val hadSuccessfulPoll = previousState.plcPollingSnapshot.lastSuccessfulPollAtMs != null
                        val faultEscalated =
                            shouldEscalatePollFailure(
                                error = error,
                                consecutiveFailures = consecutiveFailures,
                                hadSuccessfulPoll = hadSuccessfulPoll,
                            )
                        if (faultEscalated && activeCommunicationFaultId == null) {
                            activeCommunicationFaultId =
                                auditStore.recordFault(
                                    faultType = "COMMUNICATION",
                                    severity = "ERROR",
                                    message = error.message ?: "PLC polling failed",
                                    snapshotJson =
                                        JSONObject()
                                            .put("serial", serialDiagnostic.summary)
                                            .put("method", serialDiagnostic.method)
                                            .toString(),
                                )
                        }
                        val failureScope = if (config.registerOnlyMode) "register-only poll failed" else "background poll failed"
                        Log.w(
                            TAG_PLC_POLL,
                            "$failureScope: ${error.message}; " +
                                "serialRaw=${serialDiagnostic.summary}/${serialDiagnostic.method}",
                            error,
                        )
                        runtimeStore.update { state ->
                            if (faultEscalated) {
                                heaterEnableSentForConnection = false
                            }
                            val failureMessage =
                                if (error is PlcCommunicationUnavailableException) {
                                    "PLC 通讯无响应，已触发熔断：${error.message ?: "未知错误"}"
                                } else {
                                    "后台轮询连续失败：${error.message ?: "未知错误"}"
                                }
                            val retryMessage = "后台轮询部分失败，正在重试：$consecutiveFailures/$MAX_TRANSIENT_POLL_FAILURES"

                            state.copy(
                                plcState = if (faultEscalated) PlcConnectionState.Fault else PlcConnectionState.Connected,
                                communicationConfig = state.communicationConfig ?: config.toCommunicationConfig(),
                                plcPollingSnapshot = state.plcPollingSnapshot.copy(serialDiagnostic = serialDiagnostic),
                                lastMessage = if (faultEscalated) failureMessage else retryMessage,
                            )
                        }
                    }

                    delay(pollingInterval)
                }
            }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }
}

private const val TAG_PLC_POLL = "PlcPolling"
private const val MAX_TRANSIENT_POLL_FAILURES = 3
private const val HEATER_COMMAND_TYPE_ENABLE = 11

internal fun shouldEscalatePollFailure(
    error: Throwable,
    consecutiveFailures: Int,
    hadSuccessfulPoll: Boolean,
): Boolean =
    error is PlcCommunicationUnavailableException ||
        consecutiveFailures >= MAX_TRANSIENT_POLL_FAILURES ||
        !hadSuccessfulPoll

private fun shouldAutoSendHeaterEnable(
    config: AppPersistentConfig,
    heaterEnableSentForConnection: Boolean,
    heartbeatWarning: String?,
    heaterActuatorConfigEditActive: Boolean,
): Boolean =
    !heaterEnableSentForConnection &&
        !config.registerOnlyMode &&
        !heaterActuatorConfigEditActive &&
        heartbeatWarning == null

private fun AppPersistentConfig.toAutoHeaterEnableCommand(commandId: Int): PlcHeaterCommand =
    PlcHeaterCommand(
        commandId = commandId,
        commandType = HEATER_COMMAND_TYPE_ENABLE,
        heaterSelect = deviceHeaterSelect.takeIf { it == 1 || it == 2 } ?: 1,
        targetTemp = deviceHeaterTargetTemp.coerceIn(0, 120),
        hysteresis = deviceHeaterHysteresisTemp.coerceIn(0, 30),
        sensorSelect = deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
    )
