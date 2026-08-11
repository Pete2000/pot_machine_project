package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import com.example.plccontroller.data.local.auditPlcCommand
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcHeaterCommand
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class HeaterControlCoordinator(
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val externalScope: CoroutineScope,
    private val auditStore: MachineAuditStore,
    private val runPlcMaintenanceAction: suspend (String, suspend () -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    private var safeSaveInProgress = false

    fun onConfigChanged(
        previousConfig: AppPersistentConfig,
        config: AppPersistentConfig,
    ) {
        if (!previousConfig.hasHeaterParameterDiff(config)) {
            return
        }
        if (safeSaveInProgress) {
            log("Automatic heater parameter write skipped; safe actuator save is handling it.")
            return
        }
        externalScope.launch {
            writeConfiguredHeaterParameters(config)
        }
    }

    fun saveActuatorConfig(action: (SettingsStore) -> Unit) {
        externalScope.launch {
            runPlcMaintenanceAction("Safe heater actuator config save") {
                safeSaveInProgress = true
                try {
                    pauseHeaterAndConfirmOutputsOffLocked()
                    settingsStore.updateBatch {
                        action(this)
                    }
                    val config = settingsStore.snapshot()
                    val commandId = settingsStore.nextPlcCommandId()
                    writeHeaterCommandAudited(config.toHeaterParameterCommand(commandId))
                    val parameterSnapshot = plcController.pollSnapshotStableClean()
                    val enableCommandId = settingsStore.nextPlcCommandId()
                    writeHeaterCommandAudited(config.toHeaterEnableCommand(enableCommandId))
                    val snapshot = plcController.pollSnapshotStableClean()
                    runtimeStore.update { state ->
                        state.copy(
                            plcState = PlcConnectionState.Connected,
                            plcPollingSnapshot = snapshot,
                            lastMessage = "Heater actuator config saved safely: params=$commandId, enable=$enableCommandId",
                        )
                    }
                    log(
                        "Heater actuator config saved safely: params=$commandId, " +
                            "enable=$enableCommandId, afterParamState=${parameterSnapshot.heaterActionStateCode}",
                    )
                } finally {
                    safeSaveInProgress = false
                    runtimeStore.update { it.copy(heaterActuatorConfigEditActive = false) }
                }
            }
        }
    }

    fun pauseForActuatorConfigEdit() {
        runtimeStore.update { it.copy(heaterActuatorConfigEditActive = true) }
        externalScope.launch {
            runPlcMaintenanceAction("Pause heater for actuator config") {
                pauseHeaterAndConfirmOutputsOffLocked()
            }
        }
    }

    fun resumeAfterActuatorConfigEdit() {
        externalScope.launch {
            runPlcMaintenanceAction("Resume heater after actuator config") {
                val config = settingsStore.snapshot()
                val enableCommandId = settingsStore.nextPlcCommandId()
                writeHeaterCommandAudited(config.toHeaterEnableCommand(enableCommandId))
                val snapshot = plcController.pollSnapshotStableClean()
                runtimeStore.update { state ->
                    state.copy(
                        plcState = PlcConnectionState.Connected,
                        plcPollingSnapshot = snapshot,
                        heaterActuatorConfigEditActive = false,
                        lastMessage = "Heater enable resumed after actuator config: cmd=$enableCommandId",
                    )
                }
                log("Heater enable resumed after actuator config: cmd=$enableCommandId")
            }
        }
    }

    suspend fun sendDebugHeaterTestCommand() {
        runPlcMaintenanceAction("Debug heater command") {
            val config = settingsStore.snapshot()
            val commandId = settingsStore.nextPlcCommandId()
            writeHeaterCommandAudited(config.toHeaterParameterCommand(commandId))
            val parameterSnapshot = plcController.pollSnapshotStableClean()
            val enableCommandId = settingsStore.nextPlcCommandId()
            writeHeaterCommandAudited(config.toHeaterEnableCommand(enableCommandId))
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "Debug heater params written: cmd=$commandId",
                )
            }
            log("Debug heater command written: commandId=$commandId, ${config.heaterSummaryText()}")
            log("Debug heater enable sent: cmd=$enableCommandId, afterParamState=${parameterSnapshot.heaterActionStateCode}")
        }
    }

    private suspend fun writeConfiguredHeaterParameters(config: AppPersistentConfig) {
        runPlcMaintenanceAction("Write configured heater parameters") {
            val commandId = settingsStore.nextPlcCommandId()
            writeHeaterCommandAudited(config.toHeaterParameterCommand(commandId))
            val parameterSnapshot = plcController.pollSnapshotStableClean()
            val enableCommandId = settingsStore.nextPlcCommandId()
            writeHeaterCommandAudited(config.toHeaterEnableCommand(enableCommandId))
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "加热参数已写入：${config.heaterSummaryText()}，cmd=$commandId",
                )
            }
            log("Configured heater params written: cmd=$commandId, ${config.heaterSummaryText()}")
            log("Configured heater enable sent: cmd=$enableCommandId, afterParamState=${parameterSnapshot.heaterActionStateCode}")
        }
    }

    private suspend fun pauseHeaterAndConfirmOutputsOffLocked(): PlcPollingSnapshot {
        val config = settingsStore.snapshot()
        val disableCommandId = settingsStore.nextPlcCommandId()
        writeHeaterCommandAudited(config.toHeaterDisableCommand(disableCommandId))
        val snapshot = waitForHeaterOutputsOffLocked()
        runtimeStore.update { state ->
            state.copy(
                plcState = PlcConnectionState.Connected,
                plcPollingSnapshot = snapshot,
                lastMessage = "Heater enable paused for actuator config: cmd=$disableCommandId",
            )
        }
        log("Heater enable paused for actuator config: cmd=$disableCommandId, M320/M321 confirmed OFF")
        return snapshot
    }

    private suspend fun waitForHeaterOutputsOffLocked(): PlcPollingSnapshot {
        val deadline = System.currentTimeMillis() + HEATER_OUTPUT_OFF_CONFIRM_TIMEOUT_MS
        var latestSnapshot = plcController.pollSnapshotStableClean()
        while (latestSnapshot.mainHeaterOutput || latestSnapshot.backupHeaterOutput) {
            if (System.currentTimeMillis() >= deadline) {
                throw IllegalStateException(
                    "加热输出未能在安全时间内关闭：M320=${latestSnapshot.mainHeaterOutput}, " +
                        "M321=${latestSnapshot.backupHeaterOutput}",
                )
            }
            delay(HEATER_OUTPUT_OFF_CONFIRM_POLL_MS)
            latestSnapshot = plcController.pollSnapshotStableClean()
        }
        return latestSnapshot
    }

    private suspend fun writeHeaterCommandAudited(command: PlcHeaterCommand) {
        auditStore.auditPlcCommand(
            PlcCommandAuditRequest(
                commandId = command.commandId,
                commandType =
                    when (command.commandType) {
                        HEATER_COMMAND_TYPE_ENABLE -> "HEATER_ENABLE"
                        HEATER_COMMAND_TYPE_DISABLE -> "HEATER_DISABLE"
                        else -> "HEATER_PARAMETER"
                    },
                commandName = "Heater command type ${command.commandType}",
                requestRegisterStart = 100,
                requestPayloadJson = command.toRegisters().joinToString(",", "[", "]"),
            ),
        ) {
            plcController.writeHeaterCommand(command)
        }
    }
}

internal fun AppPersistentConfig.hasHeaterParameterDiff(other: AppPersistentConfig): Boolean =
    deviceHeaterTargetTemp != other.deviceHeaterTargetTemp ||
        deviceHeaterHysteresisTemp != other.deviceHeaterHysteresisTemp ||
        deviceHeaterSelect != other.deviceHeaterSelect ||
        deviceHeaterSensorSelect != other.deviceHeaterSensorSelect

internal fun AppPersistentConfig.toHeaterParameterCommand(commandId: Int): PlcHeaterCommand =
    PlcHeaterCommand(
        commandId = commandId,
        commandType = HEATER_COMMAND_TYPE_SAVE_PARAMS,
        heaterSelect = deviceHeaterSelect.takeIf { it == 1 || it == 2 } ?: 1,
        targetTemp = deviceHeaterTargetTemp.coerceIn(0, 120),
        hysteresis = deviceHeaterHysteresisTemp.coerceIn(0, 30),
        sensorSelect = deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
    )

internal fun AppPersistentConfig.toHeaterEnableCommand(commandId: Int): PlcHeaterCommand =
    PlcHeaterCommand(
        commandId = commandId,
        commandType = HEATER_COMMAND_TYPE_ENABLE,
        heaterSelect = deviceHeaterSelect.takeIf { it == 1 || it == 2 } ?: 1,
        targetTemp = deviceHeaterTargetTemp.coerceIn(0, 120),
        hysteresis = deviceHeaterHysteresisTemp.coerceIn(0, 30),
        sensorSelect = deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
    )

internal fun AppPersistentConfig.toHeaterDisableCommand(commandId: Int): PlcHeaterCommand =
    PlcHeaterCommand(
        commandId = commandId,
        commandType = HEATER_COMMAND_TYPE_DISABLE,
        heaterSelect = deviceHeaterSelect.takeIf { it == 1 || it == 2 } ?: 1,
        targetTemp = deviceHeaterTargetTemp.coerceIn(0, 120),
        hysteresis = deviceHeaterHysteresisTemp.coerceIn(0, 30),
        sensorSelect = deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
    )

internal fun AppPersistentConfig.heaterSummaryText(): String {
    val channel = if (deviceHeaterSelect == 2) "备加热" else "主加热"
    return "$channel, target=${deviceHeaterTargetTemp}C, hysteresis=${deviceHeaterHysteresisTemp}C"
}

private const val HEATER_COMMAND_TYPE_SAVE_PARAMS = 10
private const val HEATER_COMMAND_TYPE_ENABLE = 11
private const val HEATER_COMMAND_TYPE_DISABLE = 12
private const val HEATER_OUTPUT_OFF_CONFIRM_POLL_MS = 200L
private const val HEATER_OUTPUT_OFF_CONFIRM_TIMEOUT_MS = 3_000L
