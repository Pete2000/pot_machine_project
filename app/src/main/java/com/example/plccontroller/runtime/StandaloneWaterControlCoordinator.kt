package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import com.example.plccontroller.data.local.auditPlcCommand
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcEmergencyWaterCommand
import com.example.plccontroller.domain.PlcConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class StandaloneWaterControlCoordinator(
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val externalScope: CoroutineScope,
    private val auditStore: MachineAuditStore,
    private val runPlcMaintenanceAction: suspend (String, suspend () -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    fun onConfigChanged(
        previousConfig: AppPersistentConfig,
        config: AppPersistentConfig,
    ) {
        if (!previousConfig.hasEmergencyWaterParameterDiff(config)) {
            return
        }
        externalScope.launch {
            writeConfiguredEmergencyWaterParameters(config)
        }
    }

    suspend fun sendDebugEmergencyWaterTestCommand() {
        runPlcMaintenanceAction("Debug emergency-water command") {
            val config = settingsStore.snapshot()
            writeEmergencyWaterCommandAudited(config.toEmergencyWaterParameterCommand(), "Debug emergency water")
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "Debug emergency-water params written",
                )
            }
            log("Debug emergency-water command written: ${config.emergencyWaterSummaryText()}")
        }
    }

    private suspend fun writeConfiguredEmergencyWaterParameters(config: AppPersistentConfig) {
        runPlcMaintenanceAction("Write configured emergency-water parameters") {
            writeEmergencyWaterCommandAudited(config.toEmergencyWaterParameterCommand(), "Configured emergency water")
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "单独加水参数已写入：${config.emergencyWaterSummaryText()}",
                )
            }
            log("Configured emergency-water params written: ${config.emergencyWaterSummaryText()}")
        }
    }

    private suspend fun writeEmergencyWaterCommandAudited(
        command: PlcEmergencyWaterCommand,
        name: String,
    ) {
        auditStore.auditPlcCommand(
            PlcCommandAuditRequest(
                commandId = 0,
                commandType = "STANDALONE_WATER_PARAMETER",
                commandName = name,
                requestRegisterStart = 200,
                requestPayloadJson = command.toRegisters().joinToString(",", "[", "]"),
            ),
        ) {
            plcController.writeEmergencyWaterCommand(command)
        }
    }
}

internal fun AppPersistentConfig.hasEmergencyWaterParameterDiff(other: AppPersistentConfig): Boolean =
    deviceStandaloneWaterMode != other.deviceStandaloneWaterMode ||
        deviceStandaloneWaterTimedTicks != other.deviceStandaloneWaterTimedTicks ||
        deviceSparePumpMode != other.deviceSparePumpMode

internal fun AppPersistentConfig.toEmergencyWaterParameterCommand(): PlcEmergencyWaterCommand =
    PlcEmergencyWaterCommand(
        mode = deviceStandaloneWaterMode.takeIf { it in 0..2 } ?: 0,
        timedDuration = deviceStandaloneWaterTimedTicks.coerceIn(1, 3_600),
        sparePumpMode = deviceSparePumpMode.takeIf { it in 0..2 } ?: 0,
    )

internal fun AppPersistentConfig.emergencyWaterSummaryText(): String {
    val mode =
        when (deviceStandaloneWaterMode) {
            1 -> "点动"
            2 -> "时间控制"
            else -> "关闭"
        }
    val spare =
        when (deviceSparePumpMode) {
            1 -> "备用替代鸡油"
            2 -> "备用替代骨膏"
            else -> "不用备用"
        }
    return "$mode, timedTicks=$deviceStandaloneWaterTimedTicks, $spare"
}
