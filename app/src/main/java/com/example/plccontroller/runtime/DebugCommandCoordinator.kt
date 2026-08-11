package com.example.plccontroller.runtime

import com.example.plccontroller.data.http.HttpOrderRepository
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import com.example.plccontroller.data.local.auditPlcCommand
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.PlcConnectionState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("LongParameterList")
internal class DebugCommandCoordinator(
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val auditStore: MachineAuditStore,
    private val heaterControlCoordinator: HeaterControlCoordinator,
    private val standaloneWaterControlCoordinator: StandaloneWaterControlCoordinator,
    private val orderRepository: HttpOrderRepository,
    private val phaseExecutionCoordinator: PhaseExecutionCoordinator,
    private val runPlcMaintenanceAction: suspend (String, suspend () -> Unit) -> Unit,
    private val withCriticalMaintenance: suspend (String, suspend () -> Unit) -> Unit,
    private val plcCommandLock: Mutex,
    private val nextPlcCommandId: () -> Int,
    private val log: (String) -> Unit,
) {
    suspend fun refreshPlcSnapshotNow() {
        runPlcMaintenanceAction("Manual PLC refresh") {
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "PLC snapshot refreshed manually",
                )
            }
            log(
                "Manual PLC refresh: temp0=${snapshot.temperatureSensor0}, " +
                    "temp1=${snapshot.temperatureSensor1}, level=${snapshot.liquidLevelState}",
            )
        }
    }

    suspend fun triggerDebugHeartbeatPulse() {
        runPlcMaintenanceAction("Debug heartbeat pulse") {
            val currentState = plcController.pollSnapshotStableClean().lastHeartbeatValue
            plcController.writeHeartbeat(!currentState)
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "Debug heartbeat pulse sent (bit flipped)",
                )
            }
            log("Debug heartbeat pulse sent: ${!currentState}")
        }
    }

    suspend fun sendDebugHeaterTestCommand() {
        heaterControlCoordinator.sendDebugHeaterTestCommand()
    }

    suspend fun sendDebugEmergencyWaterTestCommand() {
        standaloneWaterControlCoordinator.sendDebugEmergencyWaterTestCommand()
    }

    suspend fun sendDebugPhaseTestCommand() {
        runPlcMaintenanceAction("Debug phase command") {
            val commandId = nextPlcCommandId()
            val command =
                PlcPhaseCommand(
                    commandId = commandId,
                    commandType = 1,
                    jobSlotMask = 1,
                    waterDuration1 = 10,
                    waterDuration2 = 0,
                    waterDuration3 = 0,
                    waterDuration4 = 0,
                    activeTopLeftLogicalSlot = 1,
                    chickenOilDuration = 0,
                    bonePasteDuration = 0,
                    phaseNo = 1,
                )
            auditStore.auditPlcCommand(
                PlcCommandAuditRequest(
                    commandId = commandId,
                    commandType = "DEBUG_COMMAND",
                    commandName = "Debug phase command",
                    requestRegisterStart = 300,
                    requestPayloadJson = command.toRegisters().joinToString(",", "[", "]"),
                ),
            ) {
                plcController.writePhaseCommand(command)
            }
            val snapshot = plcController.pollSnapshotStableClean()
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = snapshot,
                    lastMessage = "Debug phase command written: cmd=$commandId",
                )
            }
            log("Debug phase command written: commandId=$commandId, slotMask=1, water=[10,0,0,0]")
        }
    }

    suspend fun clearAllLocalOrdersForDebug() {
        withCriticalMaintenance("Clearing local order data") {
            plcCommandLock.withLock {
                val snapshot = runtimeStore.snapshot()
                if (snapshot.currentOrder != null ||
                    snapshot.orderPhaseReservation?.physicalActionStarted == true ||
                    snapshot.plcPollingSnapshot.hasActivePhysicalOutput()
                ) {
                    runtimeStore.update { state ->
                        state.copy(lastMessage = "设备正在制作或存在物理输出，禁止清空订单")
                    }
                    log("Clear local orders blocked because physical execution is active.")
                    return@withLock
                }

                snapshot.orderPhaseReservation
                    ?.takeIf { !it.physicalActionStarted }
                    ?.let { reservation ->
                        phaseExecutionCoordinator.cancelPreExecutionFormulaPrompt(reservation.orderId)
                    }
                orderRepository.clearAllLocalOrdersForDebug()
                runtimeStore.update { state ->
                    state
                        .copy(
                            currentOrder = null,
                            pinnedOrderIds = emptySet(),
                            pendingOrders = emptyList(),
                            delayedOrders = emptyList(),
                            waitingTransferOrders = emptyList(),
                            completedOrders = emptyList(),
                            cancelledOrders = emptyList(),
                            phaseRotationPrompt = null,
                            orderPhaseReservation = null,
                            manualPhasePrompt = null,
                            manualPhaseTargetLogicalSlot = null,
                            operatorAlerts = emptyList(),
                            acknowledgedOperatorAlertIds = emptySet(),
                            operatorNotice = null,
                            lastMessage = "已清空全部本地订单与订单事件；接口订单将在下次同步时重新进入",
                        ).withRefreshedTransferDeck()
                }
                log("All local orders and order audit events cleared from Room for debugging.")
            }
        }
    }
}

private fun com.example.plccontroller.domain.PlcPollingSnapshot.hasActivePhysicalOutput(): Boolean =
    phaseActionStateCode == 2 ||
        emergencyActionStateCode == 2 ||
        waterValve0Output ||
        waterValve1Output ||
        waterValve2Output ||
        waterValve3Output ||
        singleWaterValveOutput ||
        chickenOilPumpOutput ||
        bonePastePumpOutput
