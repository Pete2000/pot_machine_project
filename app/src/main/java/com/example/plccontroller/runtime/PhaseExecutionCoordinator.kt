package com.example.plccontroller.runtime

import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.ManualWaterPhaseSlot
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.structureIssueSummary

class PhaseExecutionCoordinator(
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val auditStore: MachineAuditStore,
    private val orderLifecycle: OrderLifecycleController,
    private val log: (String) -> Unit,
) {
    private var pendingPhaseCommands: List<PhaseCommandPlan> = emptyList()
    private var activeOrderPhaseContext: OrderPhaseContext? = null
    private val phaseCommandPlanBuilder =
        PhaseCommandPlanBuilder(
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
        )
    private val phaseCommandExecutor =
        PhaseCommandExecutor(
            plcController = plcController,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            auditStore = auditStore,
            log = log,
        )

    suspend fun sendManualWaterPhaseCommand(request: ManualWaterPhaseRequest) {
        if (pendingPhaseCommands.isNotEmpty()) {
            val prompt = pendingPhaseCommands.first().rotationPrompt
            runtimeStore.update { state ->
                state.copy(
                    phaseRotationPrompt = prompt,
                    manualPhasePrompt = prompt?.message ?: "请先完成当前待转锅加料相位",
                    manualPhaseTargetLogicalSlot = prompt?.targetLogicalSlot,
                    lastMessage = "已阻止新的手动加水：请先完成当前转锅加料",
                )
            }
            log("Manual water blocked because a phase rotation prompt is pending.")
            return
        }
        val currentTopLeft =
            runtimeStore
                .snapshot()
                .phaseCurrentTopLeftLogicalSlot
                .coerceIn(1, request.mode.slotCount())
        val plan =
            phaseCommandPlanBuilder.buildManualWaterPhaseCommands(
                request = request,
                currentTopLeftLogicalSlot = currentTopLeft,
            )
        executePhasePlans(plan)
    }

    suspend fun confirmPendingPhaseRotation() {
        val nextPlanItem = pendingPhaseCommands.firstOrNull()
        if (nextPlanItem == null) {
            clearPhasePrompt(message = "当前没有待继续的转锅相位")
            return
        }
        clearPhasePrompt(message = "已确认转锅，正在发送相位命令")

        executePhasePlans(
            plans = listOf(nextPlanItem.copy(rotationPrompt = null)) + pendingPhaseCommands.drop(1),
        )
    }

    suspend fun cancelPendingPhaseRotation() {
        val context = activeOrderPhaseContext
        val canceledOrder = context?.order
        if (context?.physicalActionStarted == true) {
            runtimeStore.update { state ->
                state.copy(
                    operatorAlerts =
                        state.operatorAlerts.enqueue(
                            OperatorAlert(
                                id = "order-physical-action:${context.order.id}",
                                title = "订单正在制作",
                                message = "该订单已完成部分加水，不能取消或返回待加水，请完成转锅流程。",
                            ),
                        ),
                    lastMessage = "订单 ${context.order.id} 已开始执行，不能暂不处理",
                )
            }
            log("Order ${context.order.id} phase cancel blocked after physical action started.")
            return
        }
        pendingPhaseCommands = emptyList()
        activeOrderPhaseContext = null
        runtimeStore.update { state ->
            if (canceledOrder == null) {
                state.copy(
                    phaseRotationPrompt = null,
                    orderPhaseReservation = null,
                    manualPhasePrompt = null,
                    manualPhaseTargetLogicalSlot = null,
                    lastMessage = "已暂不处理，本次手动加水流程已清空，可重新开始",
                )
            } else {
                orderLifecycle.applyReturnedToPendingWater(
                    state = state,
                    order = canceledOrder,
                    message = "订单 ${canceledOrder.id} 已暂不处理，已回到待加水，可重新开始",
                )
            }
        }
        log(
            if (canceledOrder == null) {
                "Manual phase flow canceled by operator; physical actions are not rolled back."
            } else {
                "Order ${canceledOrder.id} pre-execution phase flow canceled and returned to pending water."
            },
        )
    }

    fun pendingOrderId(): String? = activeOrderPhaseContext?.order?.id

    internal suspend fun executeAgingPhase(command: PlcPhaseCommand): PlcPollingSnapshot =
        phaseCommandExecutor.execute(
            planItem =
                PhaseCommandPlan(
                    source = PhaseFlowSource.Manual,
                    command = command,
                    rotationPrompt = null,
                ),
        )

    fun cancelPreExecutionFormulaPrompt(orderId: String): Boolean {
        val context = activeOrderPhaseContext ?: return false
        val prompt = pendingPhaseCommands.firstOrNull()?.rotationPrompt ?: return false
        if (context.order.id != orderId ||
            context.physicalActionStarted ||
            prompt.kind != PhasePromptKind.FormulaMissing
        ) {
            return false
        }
        pendingPhaseCommands = emptyList()
        activeOrderPhaseContext = null
        runtimeStore.update { state ->
            state.copy(
                phaseRotationPrompt = null,
                orderPhaseReservation = null,
                manualPhasePrompt = null,
                manualPhaseTargetLogicalSlot = null,
            )
        }
        log("Pre-execution formula-missing prompt cleared for canceled order $orderId.")
        return true
    }

    suspend fun dispatchOrder(order: Order) {
        try {
            if (order.hasBlockingStructureIssue()) {
                val message = order.structureIssueSummary() ?: "订单结构异常，禁止加水"
                runtimeStore.update { state ->
                    state.copy(lastMessage = message)
                }
                log("Dispatch blocked by order structure issue: order=${order.id}, $message")
                return
            }
            if (pendingPhaseCommands.isNotEmpty()) {
                log("Dispatch blocked because a phase rotation prompt is pending.")
                runtimeStore.update { state ->
                    state.copy(lastMessage = "请先处理当前转锅弹窗，再开始新的订单")
                }
                return
            }

            val orderMode = order.potMode.toManualWaterPotMode()
            val currentTopLeft =
                runtimeStore
                    .snapshot()
                    .phaseCurrentTopLeftLogicalSlot
                    .coerceIn(1, orderMode.slotCount())

            val buildResult =
                phaseCommandPlanBuilder.buildOrderPhaseCommands(
                    order = order,
                    currentTopLeftLogicalSlot = currentTopLeft,
                )

            if (buildResult.allSlotsMissing) {
                handleAllFormulaMissing(order = order, buildResult = buildResult)
                return
            }

            if (!buildResult.hasPhysicalCommands) {
                handleNoPhysicalOrder(order = order, buildResult = buildResult)
                return
            }

            buildResult.toOperatorNotice(order)?.let { notice ->
                runtimeStore.update { state ->
                    state.copy(
                        operatorNotice = notice,
                        lastMessage = notice.message,
                    )
                }
            }

            activeOrderPhaseContext =
                OrderPhaseContext(
                    order = order,
                    physicalActionStarted = false,
                    completionWarning = buildResult.partialMissingWarning(),
                )
            log("Dispatch initialized for order ${order.id}; physical start waits for successful PLC write.")

            executePhasePlans(buildResult.plans)
        } catch (error: Throwable) {
            val physicalActionStarted = activeOrderPhaseContext?.physicalActionStarted == true
            activeOrderPhaseContext = null
            pendingPhaseCommands = emptyList()
            if (physicalActionStarted) {
                runCatching {
                    orderLifecycle.markDispatchResult(
                        orderId = order.id,
                        success = false,
                        message = error.message ?: "Dispatch failed",
                    )
                }.onFailure { reportError ->
                    log("Dispatch failure callback failed: ${reportError.message}")
                }
            }
            log("Dispatch failed: ${error.message}")
            runtimeStore.update { state ->
                orderLifecycle.applyDispatchFailed(
                    state = state,
                    order = order,
                    message = error.message ?: "Dispatch failed",
                )
            }
        }
    }

    private fun handleAllFormulaMissing(
        order: Order,
        buildResult: OrderPhaseCommandBuildResult,
    ) {
        val message = "订单 ${order.id} 所有锅底均未匹配到配方，设备未执行，请检查配方。"
        runtimeStore.update { state ->
            state.copy(
                operatorAlerts =
                    state.operatorAlerts.enqueue(
                        OperatorAlert(
                            id = "formula-missing-all:${order.id}",
                            title = "订单配方缺失",
                            message = "$message\n未匹配锅底：${buildResult.missingSlots.toSlotWarningText()}",
                        ),
                    ),
                phaseRotationPrompt = null,
                orderPhaseReservation = null,
                manualPhasePrompt = null,
                manualPhaseTargetLogicalSlot = null,
                operatorNotice = null,
                lastMessage = message,
            )
        }
        log(message)
    }

    private suspend fun handleNoPhysicalOrder(
        order: Order,
        buildResult: OrderPhaseCommandBuildResult,
    ) {
        val message = buildResult.noPhysicalDispatchMessage()
        val notice = buildResult.toOperatorNotice(order)
        runCatching {
            orderLifecycle.markDispatchResult(
                orderId = order.id,
                success = true,
                message = message,
            )
        }.onFailure { error ->
            log("Skipped order dispatch callback failed for ${order.id}: ${error.message}")
        }
        runtimeStore.update { state ->
            val skippedState =
                orderLifecycle.applySkippedWaitingTransfer(
                    state = state,
                    order = order,
                    message = message,
                )
            if (notice == null) {
                skippedState
            } else {
                skippedState.copy(operatorNotice = notice)
            }
        }
        log("Order ${order.id} has no physical phase command. $message")
    }

    private suspend fun executePhasePlans(plans: List<PhaseCommandPlan>) {
        require(plans.isNotEmpty()) { "Phase plan is empty" }

        var remainingPlans = plans
        var latestSnapshot = runtimeStore.snapshot().plcPollingSnapshot
        while (remainingPlans.isNotEmpty()) {
            val nextPlan = remainingPlans.first()
            val prompt = nextPlan.rotationPrompt
            if (prompt != null) {
                pendingPhaseCommands = remainingPlans
                runtimeStore.update { state ->
                    state.copy(
                        phaseRotationPrompt = prompt,
                        orderPhaseReservation =
                            activeOrderPhaseContext?.let { context ->
                                OrderPhaseReservation(
                                    orderId = context.order.id,
                                    promptKind = prompt.kind,
                                    physicalActionStarted = context.physicalActionStarted,
                                )
                            },
                        manualPhasePrompt = prompt.message,
                        manualPhaseTargetLogicalSlot = prompt.targetLogicalSlot,
                        lastMessage = prompt.message,
                    )
                }
                log("Phase flow paused for rotation: ${prompt.message}")
                return
            }
            latestSnapshot =
                phaseCommandExecutor.execute(
                    planItem = nextPlan,
                    orderId = activeOrderPhaseContext?.order?.id,
                ) {
                    markPhysicalActionStartedIfNeeded()
                }
            remainingPlans = remainingPlans.drop(1)
        }

        pendingPhaseCommands = emptyList()
        val finishedContext = activeOrderPhaseContext
        val finishedOrder = finishedContext?.order
        activeOrderPhaseContext = null
        if (finishedOrder == null) {
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Connected,
                    plcPollingSnapshot = latestSnapshot,
                    phaseRotationPrompt = null,
                    orderPhaseReservation = null,
                    manualPhasePrompt = null,
                    manualPhaseTargetLogicalSlot = null,
                    manualPhaseCompletionToken = state.manualPhaseCompletionToken + 1,
                    lastMessage = "手动相位命令已全部发送",
                )
            }
        } else {
            val completionMessage =
                listOfNotNull(
                    "订单 ${finishedOrder.id} 相位已发送，等待传锅",
                    finishedContext?.completionWarning,
                ).joinToString("；")
            orderLifecycle.markDispatchResult(
                orderId = finishedOrder.id,
                success = true,
                message = completionMessage,
            )
            runtimeStore.update { state ->
                orderLifecycle.applyWaitingTransfer(
                    state = state,
                    order = finishedOrder,
                    latestSnapshot = latestSnapshot,
                    message = completionMessage,
                )
            }
        }
    }

    private fun clearPhasePrompt(message: String) {
        runtimeStore.update { state ->
            state.copy(
                phaseRotationPrompt = null,
                orderPhaseReservation =
                    activeOrderPhaseContext?.let { context ->
                        OrderPhaseReservation(
                            orderId = context.order.id,
                            promptKind =
                                pendingPhaseCommands.firstOrNull()?.rotationPrompt?.kind
                                    ?: PhasePromptKind.Rotation,
                            physicalActionStarted = context.physicalActionStarted,
                        )
                    },
                manualPhasePrompt = null,
                manualPhaseTargetLogicalSlot = null,
                lastMessage = message,
            )
        }
    }

    private suspend fun markPhysicalActionStartedIfNeeded() {
        val context = activeOrderPhaseContext ?: return
        if (context.physicalActionStarted) return

        // Set the in-memory guard first. A persistence failure after the PLC write must
        // never reopen the automatic-cancellation window.
        activeOrderPhaseContext = context.copy(physicalActionStarted = true)
        runtimeStore.update { state ->
            orderLifecycle.applyDispatchStarted(state, context.order).copy(
                orderPhaseReservation =
                    state.orderPhaseReservation?.copy(
                        physicalActionStarted = true,
                    ),
            )
        }
        runCatching {
            orderLifecycle.markDispatchStarted(context.order.id)
        }.onFailure { error ->
            // The PLC command has already been accepted. Keep the physical flow alive
            // and retain the in-memory cancellation guard even if local persistence fails.
            log("Failed to persist dispatch start for ${context.order.id}: ${error.message}")
        }
        log("Physical action started after PLC accepted command for order ${context.order.id}")
    }
}

private data class OrderPhaseContext(
    val order: Order,
    val physicalActionStarted: Boolean,
    val completionWarning: String? = null,
)

private fun List<OperatorAlert>.enqueue(alert: OperatorAlert): List<OperatorAlert> = if (any { it.id == alert.id }) this else this + alert

private fun OrderPhaseCommandBuildResult.partialMissingWarning(): String? {
    if (missingSlots.isEmpty()) return null
    return "部分锅底未匹配配方，已跳过：${missingSlots.toSlotWarningText()}"
}

internal fun OrderPhaseCommandBuildResult.toOperatorNotice(order: Order): OperatorNotice? {
    if (allSlotsMissing) return null

    val missingWarning = partialMissingWarning()
    return when {
        missingWarning != null && hasPhysicalCommands ->
            OperatorNotice(
                id = "formula-missing-partial:${order.id}",
                level = OperatorNoticeLevel.Warning,
                message = "$missingWarning；其余锅底正常执行",
                relatedOrderId = order.id,
            )

        missingWarning != null ->
            OperatorNotice(
                id = "formula-missing-partial:${order.id}",
                level = OperatorNoticeLevel.Warning,
                message = "$missingWarning；其余锅底无需设备执行，已跳过加水",
                relatedOrderId = order.id,
            )

        !hasPhysicalCommands && noActionSlots.isNotEmpty() ->
            OperatorNotice(
                id = "formula-no-action:${order.id}",
                level = OperatorNoticeLevel.Info,
                message = NO_PHYSICAL_ORDER_MESSAGE,
                relatedOrderId = order.id,
            )

        else -> null
    }
}

private fun OrderPhaseCommandBuildResult.noPhysicalDispatchMessage(): String =
    partialMissingWarning()?.let { "$it；其余锅底无需设备执行，已跳过加水" }
        ?: NO_PHYSICAL_ORDER_MESSAGE

private fun List<ManualWaterPhaseSlot>.toSlotWarningText(): String =
    joinToString("、") { slot ->
        "${slot.logicalSlot}号位 ${slot.label.ifBlank { "锅底" }}"
    }

private const val NO_PHYSICAL_ORDER_MESSAGE = "该订单配方无需设备执行，已跳过加水"
