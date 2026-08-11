package com.example.plccontroller.runtime

import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import com.example.plccontroller.data.local.auditPlcCommand
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import kotlinx.coroutines.delay

internal class PhaseCommandExecutor(
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val auditStore: MachineAuditStore,
    private val log: (String) -> Unit,
) {
    suspend fun execute(
        planItem: PhaseCommandPlan,
        orderId: String? = null,
        onCommandAccepted: suspend () -> Unit = {},
    ): PlcPollingSnapshot {
        val command = planItem.command
        return auditStore.auditPlcCommand(
            request =
                PlcCommandAuditRequest(
                    commandId = command.commandId,
                    orderId = orderId,
                    commandType = "PHASE_COMMAND",
                    commandName = planItem.source.name,
                    requestRegisterStart = 300,
                    requestPayloadJson = command.toRegisters().joinToString(",", "[", "]"),
                ),
        ) {
            runCatching {
                syncSparePumpModeBeforeAdditivePhase(command, orderId)
                plcController.writePhaseCommand(command)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "写相位命令失败：cmd=${command.commandId}, ${error.message ?: "未知错误"}",
                    error,
                )
            }
            // From this point onward the machine may already be moving/watering. Keep the
            // business lifecycle boundary aligned with the successful PLC write, not with
            // an earlier operator prompt or a later completion poll.
            onCommandAccepted()
            logPhaseCommand(planItem)
            runtimeStore.update { state ->
                state.copy(
                    phaseRotationPrompt = null,
                    manualPhasePrompt = null,
                    manualPhaseTargetLogicalSlot = null,
                    phaseCurrentTopLeftLogicalSlot = command.activeTopLeftLogicalSlot,
                    lastMessage = "相位命令已发送，等待 PLC 完成：cmd=${command.commandId}",
                )
            }
            delay(PHASE_AFTER_WRITE_SETTLE_MS)
            waitForPhaseCommandDone(command)
        }
    }

    private suspend fun waitForPhaseCommandDone(command: PlcPhaseCommand): PlcPollingSnapshot {
        val timeoutMs =
            (command.expectedDurationMs() + PHASE_COMPLETION_GRACE_MS)
                .coerceAtLeast(PHASE_COMPLETION_MIN_WAIT_MS)
        val deadline = System.currentTimeMillis() + timeoutMs
        var latestSnapshot = runtimeStore.snapshot().plcPollingSnapshot

        while (System.currentTimeMillis() <= deadline) {
            val phaseSnapshot = plcController.pollPhaseStatusClean()
            latestSnapshot =
                latestSnapshot.copy(
                    phaseStatusRegisters = phaseSnapshot.phaseStatusRegisters,
                    pollWarnings = emptyList(),
                    lastSuccessfulPollAtMs = phaseSnapshot.lastSuccessfulPollAtMs,
                )
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = latestSnapshot,
                )
            }
            val failureMessage = latestSnapshot.phaseFailureMessage(command.commandId)
            if (failureMessage != null) {
                throw IllegalStateException(failureMessage)
            }
            if (latestSnapshot.isPhaseCommandCompleted(command.commandId)) {
                log("Phase completed: commandId=${command.commandId}")
                return latestSnapshot
            }
            delay(PHASE_STATUS_POLL_INTERVAL_MS)
        }

        throw IllegalStateException(
            "等待 PLC 相位完成超时：cmd=${command.commandId}, " +
                "D320~D329=${latestSnapshot.phaseStatusRegisters.joinToString(",")}",
        )
    }

    private fun logPhaseCommand(planItem: PhaseCommandPlan) {
        val command = planItem.command
        log(
            "${planItem.source.name.lowercase()} phase written: commandId=${command.commandId}, " +
                "phaseNo=${command.phaseNo}, activeTopLeft=${command.activeTopLeftLogicalSlot}, " +
                "mask=${command.jobSlotMask}, water=[${command.waterDuration1},${command.waterDuration2}," +
                "${command.waterDuration3},${command.waterDuration4}], " +
                "chicken=${command.chickenOilDuration}, bone=${command.bonePasteDuration}",
        )
    }

    private suspend fun syncSparePumpModeBeforeAdditivePhase(
        command: PlcPhaseCommand,
        orderId: String?,
    ) {
        if (command.chickenOilDuration <= 0 && command.bonePasteDuration <= 0) {
            return
        }
        val config = settingsStore.snapshot()
        val spareCommand = config.toEmergencyWaterParameterCommand()
        auditStore.auditPlcCommand(
            PlcCommandAuditRequest(
                commandId = command.commandId,
                orderId = orderId,
                commandType = "SPARE_PUMP_CONFIG",
                commandName = "Sync spare pump before additive phase",
                requestRegisterStart = 200,
                requestPayloadJson = spareCommand.toRegisters().joinToString(",", "[", "]"),
            ),
        ) {
            plcController.writeEmergencyWaterCommand(spareCommand)
        }
        log("Spare pump mode synced before additive phase: ${config.emergencyWaterSummaryText()}")
    }

    private fun PlcPhaseCommand.expectedDurationMs(): Long {
        val maxTicks =
            maxOf(
                waterDuration1,
                waterDuration2,
                waterDuration3,
                waterDuration4,
                chickenOilDuration,
                bonePasteDuration,
            )
        return maxTicks.toLong() * PHASE_TICK_MS
    }

    private fun PlcPollingSnapshot.isPhaseCommandCompleted(commandId: Int): Boolean {
        val finishedCommandId = phaseStatusRegisters.getOrNull(2)
        val actionState = phaseStatusRegisters.getOrNull(3)
        val resultCode = phaseStatusRegisters.getOrNull(4)
        return finishedCommandId == commandId &&
            resultCode == PLC_RESULT_OK &&
            (actionState == PLC_ACTION_COMPLETED || actionState == PLC_ACTION_IDLE)
    }

    private fun PlcPollingSnapshot.phaseFailureMessage(commandId: Int): String? {
        val acceptedCommandId = phaseStatusRegisters.getOrNull(0)
        val executingCommandId = phaseStatusRegisters.getOrNull(1)
        val finishedCommandId = phaseStatusRegisters.getOrNull(2)
        val actionState = phaseStatusRegisters.getOrNull(3)
        val resultCode = phaseStatusRegisters.getOrNull(4)
        val faultCode = phaseStatusRegisters.getOrNull(5)
        val isCurrentCommand =
            acceptedCommandId == commandId ||
                executingCommandId == commandId ||
                finishedCommandId == commandId
        if (!isCurrentCommand) return null
        val isFailedState =
            actionState == PLC_ACTION_FAULT ||
                actionState == PLC_ACTION_REJECTED ||
                actionState == PLC_ACTION_STOPPED
        val isCompletedWithError =
            finishedCommandId == commandId &&
                actionState == PLC_ACTION_COMPLETED &&
                resultCode != PLC_RESULT_OK
        return if (isFailedState || isCompletedWithError) {
            "PLC 相位执行失败：cmd=$commandId, state=$actionState, result=$resultCode, fault=$faultCode"
        } else {
            null
        }
    }
}

private const val PHASE_TICK_MS = 100L
private const val PHASE_STATUS_POLL_INTERVAL_MS = 300L
private const val PHASE_AFTER_WRITE_SETTLE_MS = 200L
private const val PHASE_COMPLETION_MIN_WAIT_MS = 3_000L
private const val PHASE_COMPLETION_GRACE_MS = 5_000L
private const val PLC_ACTION_IDLE = 0
private const val PLC_ACTION_COMPLETED = 3
private const val PLC_ACTION_FAULT = 4
private const val PLC_ACTION_REJECTED = 5
private const val PLC_ACTION_STOPPED = 6
private const val PLC_RESULT_OK = 0
