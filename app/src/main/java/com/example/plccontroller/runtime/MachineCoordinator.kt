package com.example.plccontroller.runtime

import android.os.SystemClock
import android.util.Log
import com.example.plccontroller.AppConfig
import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.HttpOrderRepository
import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.NoOpMachineAuditStore
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.toCommunicationConfig
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.runtime.aging.PlcAgingCoordinator
import com.example.plccontroller.runtime.aging.PlcAgingProgress
import com.example.plccontroller.runtime.aging.PlcAgingReport
import com.example.plccontroller.runtime.aging.PlcAgingTestConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class MachineCoordinator(
    private val orderRepository: HttpOrderRepository,
    private val plcController: PlcController,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val auditStore: MachineAuditStore = NoOpMachineAuditStore,
    private val externalScope: CoroutineScope,
) {
    private val plcCommandLock = Mutex()
    private val maintenanceLock = Mutex()
    private val orderLocks = ConcurrentHashMap<String, Mutex>()
    private val orderLifecycle = OrderLifecycleController(HttpOrderLifecycleGateway(orderRepository))
    private val orderSyncCoordinator =
        OrderSyncCoordinator(
            orderRepository = orderRepository,
            runtimeStore = runtimeStore,
            deviceCodeProvider = {
                settingsStore
                    .snapshot()
                    .deviceCode
                    .trim()
                    .ifBlank { AppConfig.formulaDeviceCode.trim() }
            },
            log = { message -> log(message) },
        )
    private val formulaSyncCoordinator =
        FormulaSyncCoordinator(
            orderRepository = orderRepository,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            log = { message -> log(message) },
        )
    private val phaseExecutionCoordinator =
        PhaseExecutionCoordinator(
            plcController = plcController,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            auditStore = auditStore,
            orderLifecycle = orderLifecycle,
            log = { message -> log(message) },
        )
    private val plcAgingCoordinator =
        PlcAgingCoordinator(
            plcController = plcController,
            settingsStore = settingsStore,
            runtimeStore = runtimeStore,
            executePhase = phaseExecutionCoordinator::executeAgingPhase,
            pendingOrderId = phaseExecutionCoordinator::pendingOrderId,
            withCriticalMaintenance = ::withCriticalMaintenance,
            withPlcCommandLock = { block -> plcCommandLock.withLock { block() } },
            log = { message -> log(message) },
        )
    private val heaterControlCoordinator =
        HeaterControlCoordinator(
            plcController = plcController,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            externalScope = externalScope,
            auditStore = auditStore,
            runPlcMaintenanceAction = ::runPlcMaintenanceAction,
            log = { message -> log(message) },
        )
    private val standaloneWaterControlCoordinator =
        StandaloneWaterControlCoordinator(
            plcController = plcController,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            externalScope = externalScope,
            auditStore = auditStore,
            runPlcMaintenanceAction = ::runPlcMaintenanceAction,
            log = { message -> log(message) },
        )
    private val transferCoordinator =
        TransferCoordinator(
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            orderLifecycle = orderLifecycle,
            log = { message -> log(message) },
        )
    private val equipmentRegistrationCoordinator =
        EquipmentRegistrationCoordinator(
            orderRepository = orderRepository,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            log = { message -> log(message) },
        )
    private val orderActionCoordinator =
        OrderActionCoordinator(
            runtimeStore = runtimeStore,
            orderLifecycle = orderLifecycle,
            phaseExecutionCoordinator = phaseExecutionCoordinator,
            withOrderLock = ::withOrderLock,
            plcCommandLock = plcCommandLock,
            externalScope = externalScope,
            refreshOrdersLocal = ::refreshOrdersLocal,
            log = { message -> log(message) },
        )
    private val debugCommandCoordinator =
        DebugCommandCoordinator(
            plcController = plcController,
            runtimeStore = runtimeStore,
            auditStore = auditStore,
            heaterControlCoordinator = heaterControlCoordinator,
            standaloneWaterControlCoordinator = standaloneWaterControlCoordinator,
            orderRepository = orderRepository,
            phaseExecutionCoordinator = phaseExecutionCoordinator,
            runPlcMaintenanceAction = ::runPlcMaintenanceAction,
            withCriticalMaintenance = ::withCriticalMaintenance,
            plcCommandLock = plcCommandLock,
            nextPlcCommandId = ::nextPlcCommandId,
            log = { message -> log(message) },
        )
    private var configObserverJob: Job? = null
    private var formulaSyncJob: Job? = null
    private var activeFormulaSyncIntervalSeconds: Long? = null
    private var orderSyncJob: Job? = null
    private var activeOrderSyncIntervalMs: Long? = null
    private var lastObservedConfig: AppPersistentConfig = settingsStore.snapshot()

    init {
        // 观察配置变更并实时反馈到 UI 和业务组件
        configObserverJob =
            externalScope.launch {
                settingsStore.configFlow.collectLatest { config ->
                    onConfigChanged(config)
                }
            }

        // 监听物理传锅完成按钮（M302），进行防抖、释放检测及上升沿（0->1）触发
        externalScope.launch {
            var lastTransferButtonState = false
            var lastTriggerTimeMs = 0L

            runtimeStore.state
                .map { it.plcPollingSnapshot.transferCompleteInput }
                .distinctUntilChanged()
                .collect { isPressed ->
                    val now = SystemClock.elapsedRealtime()
                    if (isPressed && !lastTransferButtonState) {
                        // 检测到上升沿 (0 -> 1)
                        if (now - lastTriggerTimeMs >= 2000L) { // 2秒防抖冷却时间
                            lastTriggerTimeMs = now
                            Log.i(TAG_MACHINE_COORDINATOR, "Physical transfer button press detected (M302 rising edge)")
                            // 异步触发传锅业务，避免阻塞 Flow 收集
                            launch {
                                runCatching {
                                    confirmTransferFromPhysicalButton()
                                }.onFailure { error ->
                                    Log.e(TAG_MACHINE_COORDINATOR, "Failed to execute transfer confirm from physical button", error)
                                }
                            }
                        } else {
                            Log.w(
                                TAG_MACHINE_COORDINATOR,
                                "Physical transfer button press ignored due to debounce cooldown (${now - lastTriggerTimeMs}ms elapsed)",
                            )
                        }
                    }
                    lastTransferButtonState = isPressed
                }
        }

        // 观察故障与急停状态
        externalScope.launch {
            var lastFaultState = false
            runtimeStore.state
                .map { state ->
                    val isDisconnected = state.plcState == PlcConnectionState.Fault
                    val isEStop = state.plcPollingSnapshot.emergencyStopActive
                    Triple(isDisconnected, isEStop, state.currentOrder?.id)
                }.distinctUntilChanged()
                .collect { (isDisconnected, isEStop, currentOrderId) ->
                    val isFaultActive = isDisconnected || isEStop
                    if (isFaultActive && !lastFaultState) {
                        val type = if (isEStop) FaultType.EmergencyStop else FaultType.CommunicationDisconnect
                        runtimeStore.update { state ->
                            state.copy(
                                faultMeltdownState =
                                    FaultMeltdownState(
                                        active = true,
                                        type = type,
                                        interruptedOrderId = currentOrderId,
                                    ),
                            )
                        }
                        log("Global fault meltdown triggered: $type. Interrupted order: $currentOrderId")
                    } else if (!isFaultActive && lastFaultState) {
                        runtimeStore.update { state ->
                            if (state.faultMeltdownState.interruptedOrderId == null) {
                                state.copy(faultMeltdownState = FaultMeltdownState(active = false))
                            } else {
                                state
                            }
                        }
                    }
                    lastFaultState = isFaultActive
                }
        }
    }

    private fun onConfigChanged(config: AppPersistentConfig) {
        val previousConfig = lastObservedConfig
        lastObservedConfig = config
        // 更新 UI 上的某些基础标记
        runtimeStore.update { state ->
            state.copy(
                communicationConfig = config.toCommunicationConfig(),
                lastMessage = "Configuration updated: slave ${config.plcSlaveId}, formula sync ${config.formulaSyncIntervalSeconds}s",
            )
        }
        restartFormulaSyncIfNeeded(config.formulaSyncIntervalSeconds)
        restartOrderSyncIfNeeded(config.orderPollingIntervalMs)
        heaterControlCoordinator.onConfigChanged(previousConfig, config)
        standaloneWaterControlCoordinator.onConfigChanged(previousConfig, config)
    }

    private fun restartFormulaSyncIfNeeded(intervalSeconds: Long) {
        val normalizedIntervalSeconds = intervalSeconds.coerceAtLeast(0L)
        if (activeFormulaSyncIntervalSeconds == normalizedIntervalSeconds) {
            return
        }
        activeFormulaSyncIntervalSeconds = normalizedIntervalSeconds
        formulaSyncJob?.cancel()
        formulaSyncJob = null

        if (normalizedIntervalSeconds <= 0L) {
            formulaSyncCoordinator.useCachedFormulaOrLocalDefault("配方自动同步已关闭")
            return
        }

        formulaSyncJob =
            externalScope.launch {
                while (isActive) {
                    refreshFormulaCatalog()
                    delay(normalizedIntervalSeconds * 1_000L)
                }
            }
        log("Formula sync loop started: ${normalizedIntervalSeconds}s")
    }

    private fun restartOrderSyncIfNeeded(intervalMs: Long) {
        val normalizedIntervalMs = intervalMs.coerceAtLeast(0L)
        if (activeOrderSyncIntervalMs == normalizedIntervalMs) {
            return
        }
        activeOrderSyncIntervalMs = normalizedIntervalMs
        orderSyncJob?.cancel()
        orderSyncJob = null

        if (normalizedIntervalMs <= 0L) {
            log("Order auto sync disabled")
            return
        }

        orderSyncJob =
            externalScope.launch {
                refreshOrdersLocal()
                while (isActive) {
                    refreshOrders()
                    delay(normalizedIntervalMs)
                }
            }
        log("Order sync loop started: ${normalizedIntervalMs}ms")
    }

    fun updateSettings(action: (SettingsStore) -> Unit) {
        settingsStore.updateBatch {
            action(this)
        }
    }

    fun saveHeaterActuatorConfig(action: (SettingsStore) -> Unit) {
        heaterControlCoordinator.saveActuatorConfig(action)
    }

    fun pauseHeaterForActuatorConfigEdit() {
        heaterControlCoordinator.pauseForActuatorConfigEdit()
    }

    fun resumeHeaterAfterActuatorConfigEdit() {
        heaterControlCoordinator.resumeAfterActuatorConfigEdit()
    }

    fun requestWater() {
        log("Operator requested water action")
        runtimeStore.update {
            it.copy(lastMessage = "Water request accepted. Waiting for PLC integration.")
        }
    }

    suspend fun sendManualWaterPhaseCommand(request: ManualWaterPhaseRequest) {
        runPlcMaintenanceAction("Manual water phase command") {
            phaseExecutionCoordinator.sendManualWaterPhaseCommand(request)
        }
    }

    suspend fun continueManualPhaseCommand() {
        confirmPendingPhaseRotation()
    }

    suspend fun confirmPendingPhaseRotation() {
        val orderId = phaseExecutionCoordinator.pendingOrderId()
        if (orderId == null) {
            runPlcMaintenanceAction("Confirm phase rotation") {
                phaseExecutionCoordinator.confirmPendingPhaseRotation()
            }
        } else {
            withOrderLock(orderId) {
                runPlcMaintenanceAction("Confirm phase rotation") {
                    phaseExecutionCoordinator.confirmPendingPhaseRotation()
                }
            }
        }
    }

    suspend fun cancelPendingPhaseRotation() {
        val orderId = phaseExecutionCoordinator.pendingOrderId()
        if (orderId == null) {
            runPlcMaintenanceAction("Cancel phase rotation") {
                phaseExecutionCoordinator.cancelPendingPhaseRotation()
            }
        } else {
            withOrderLock(orderId) {
                runPlcMaintenanceAction("Cancel phase rotation") {
                    phaseExecutionCoordinator.cancelPendingPhaseRotation()
                }
            }
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        runtimeStore.update { state ->
            state.copy(
                automationEnabled = enabled,
                plcState =
                    if (enabled && state.plcState == PlcConnectionState.Disconnected) {
                        PlcConnectionState.Connecting
                    } else {
                        state.plcState
                    },
                lastMessage =
                    if (enabled) {
                        "Automation enabled"
                    } else {
                        "Automation stopped"
                    },
            )
        }
    }

    suspend fun refreshPlcSnapshotNow() {
        debugCommandCoordinator.refreshPlcSnapshotNow()
    }

    suspend fun triggerDebugHeartbeatPulse() {
        debugCommandCoordinator.triggerDebugHeartbeatPulse()
    }

    suspend fun sendDebugHeaterTestCommand() {
        debugCommandCoordinator.sendDebugHeaterTestCommand()
    }

    suspend fun sendDebugEmergencyWaterTestCommand() {
        debugCommandCoordinator.sendDebugEmergencyWaterTestCommand()
    }

    suspend fun sendDebugPhaseTestCommand() {
        debugCommandCoordinator.sendDebugPhaseTestCommand()
    }

    suspend fun refreshOrders() {
        refreshOrdersWithPromptReservation(orderSyncCoordinator::refreshOrders)
    }

    suspend fun refreshOrdersLocal() {
        refreshOrdersWithPromptReservation(orderSyncCoordinator::refreshOrdersLocal)
    }

    suspend fun refreshFormulaCatalog() {
        formulaSyncCoordinator.refreshFormulaCatalog()
    }

    suspend fun registerFirstEquipment() {
        equipmentRegistrationCoordinator.registerFirstEquipment()
    }

    @Suppress("MaxLineLength")
    suspend fun fetchDeviceTypes(): List<com.example.plccontroller.data.http.DeviceTypeDto> = equipmentRegistrationCoordinator.fetchDeviceTypes()

    @Suppress("MaxLineLength")
    suspend fun fetchEquipment(deviceTypeCode: String): List<com.example.plccontroller.data.http.EquipmentDto> = equipmentRegistrationCoordinator.fetchEquipment(deviceTypeCode)

    suspend fun saveFormulaParameter(update: FormulaParameterUpdate) {
        formulaSyncCoordinator.saveFormulaParameter(update)
    }

    suspend fun selectFormulaCatalog(formulaCode: String) {
        formulaSyncCoordinator.selectFormulaCatalog(formulaCode)
    }

    fun setOrderDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        orderActionCoordinator.setOrderDelayed(orderId, delayed)
    }

    fun toggleOrderPinned(orderId: String) {
        orderActionCoordinator.toggleOrderPinned(orderId)
    }

    fun rewaterOrder(order: Order) {
        orderActionCoordinator.rewaterOrder(order)
    }

    suspend fun cancelPendingOrder(order: Order) {
        orderActionCoordinator.cancelPendingOrder(order)
    }

    suspend fun dispatchOrder(order: Order) {
        orderActionCoordinator.dispatchOrder(order)
    }

    suspend fun confirmTransfer(
        orderId: String,
        source: TransferConfirmSource = TransferConfirmSource.MainScreen,
    ) {
        if (runtimeStore.snapshot().maintenanceState.active) {
            log("Transfer confirmation blocked by maintenance mode")
            return
        }

        withOrderLock(orderId) {
            transferCoordinator.confirmTransferLocked(orderId, source)
        }
    }

    suspend fun confirmTransferFromPhysicalButton() {
        val confirmOrderId =
            transferCoordinator.physicalConfirmOrderId() ?: run {
                log("Physical transfer button ignored because no confirm card is available")
                return
            }
        confirmTransfer(
            orderId = confirmOrderId,
            source = TransferConfirmSource.PhysicalButton,
        )
    }

    suspend fun setTransferDisplayMode(mode: TransferDisplayMode) {
        transferCoordinator.setTransferDisplayMode(mode)
    }

    suspend fun loadSecondaryDisplayDemoData(orderCount: Int = 7) {
        transferCoordinator.loadSecondaryDisplayDemoData(orderCount)
    }

    suspend fun clearSecondaryDisplayDemoData() {
        transferCoordinator.clearSecondaryDisplayDemoData()
    }

    suspend fun clearAllLocalOrdersForDebug() {
        debugCommandCoordinator.clearAllLocalOrdersForDebug()
    }

    suspend fun resolveFaultMeltdown(completeInterruptedOrder: Boolean) {
        val snapshot = runtimeStore.snapshot()

        val isFaulty = snapshot.plcState != PlcConnectionState.Connected || snapshot.plcPollingSnapshot.emergencyStopActive
        if (isFaulty) {
            log("resolveFaultMeltdown blocked because fault is still active.")
            return
        }

        val orderId = snapshot.faultMeltdownState.interruptedOrderId
        if (orderId != null) {
            val order =
                snapshot.currentOrder?.takeIf { it.id == orderId }
                    ?: snapshot.pendingOrders.firstOrNull { it.id == orderId }
                    ?: snapshot.delayedOrders.firstOrNull { it.id == orderId }

            if (order != null) {
                if (completeInterruptedOrder) {
                    val deviceCode = settingsStore.snapshot().deviceCode
                    orderLifecycle.markCompleted(deviceCode, order.id)
                    runtimeStore.update { state ->
                        state
                            .copy(
                                currentOrder = if (state.currentOrder?.id == order.id) null else state.currentOrder,
                                pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
                                delayedOrders = state.delayedOrders.filterNot { it.id == order.id },
                                waitingTransferOrders = state.waitingTransferOrders.filterNot { it.id == order.id },
                            ).let { nextState ->
                                orderLifecycle.applyLiveTransferCompleted(nextState, order, "故障熔断恢复：强行完成订单")
                            }
                    }
                    log("Fault resolved: interrupted order ${order.id} forcibly marked as completed.")
                    auditStore.recordFault(
                        faultType = "MANUAL_OVERRIDE",
                        severity = "WARNING",
                        message = "Operator forced completion of interrupted order ${order.id}",
                        snapshotJson = "{\"orderId\":\"${order.id}\", \"action\":\"FORCE_COMPLETE\"}",
                    )
                } else {
                    val formulaPromptCleared = phaseExecutionCoordinator.cancelPreExecutionFormulaPrompt(order.id)
                    orderLifecycle.markCancelled(order.id)
                    runtimeStore.update { state ->
                        state
                            .copy(
                                currentOrder = if (state.currentOrder?.id == order.id) null else state.currentOrder,
                                pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
                                delayedOrders = state.delayedOrders.filterNot { it.id == order.id },
                            ).let { nextState ->
                                val cancelledState =
                                    orderLifecycle.applyPendingOrderCancelled(
                                        state = nextState,
                                        order = order,
                                        message = "故障熔断恢复：强行作废中断订单 ${order.id}",
                                    )
                                if (formulaPromptCleared) {
                                    cancelledState.withFormulaMissingCancellationAlert(order.id)
                                } else {
                                    cancelledState
                                }
                            }
                    }
                    log("Fault resolved: interrupted order ${order.id} forcibly cancelled.")
                    auditStore.recordFault(
                        faultType = "MANUAL_OVERRIDE",
                        severity = "WARNING",
                        message = "Operator forced cancellation of interrupted order ${order.id}",
                        snapshotJson = "{\"orderId\":\"${order.id}\", \"action\":\"FORCE_CANCEL\"}",
                    )
                }
            }
        }

        runtimeStore.update { state ->
            state.copy(faultMeltdownState = FaultMeltdownState(active = false))
        }
    }

    suspend fun withCriticalMaintenance(
        reason: String,
        block: suspend () -> Unit,
    ) {
        maintenanceLock.withLock {
            runtimeStore.update {
                it.copy(
                    maintenanceState = MaintenanceState(active = true, reason = reason),
                    lastMessage = reason,
                )
            }
            try {
                block()
            } finally {
                runtimeStore.update {
                    it.copy(maintenanceState = MaintenanceState())
                }
            }
        }
    }

    suspend fun runPlcAgingTest(
        config: PlcAgingTestConfig,
        onProgress: (PlcAgingProgress) -> Unit = {},
    ): PlcAgingReport = plcAgingCoordinator.run(config, onProgress)

    private suspend fun withOrderLock(
        orderId: String,
        block: suspend () -> Unit,
    ) {
        val mutex = orderLocks.computeIfAbsent(orderId) { Mutex() }
        mutex.withLock { block() }
    }

    fun acknowledgeOperatorAlert(alertId: String) {
        runtimeStore.update { state ->
            state.copy(
                operatorAlerts = state.operatorAlerts.filterNot { it.id == alertId },
                acknowledgedOperatorAlertIds = state.acknowledgedOperatorAlertIds + alertId,
            )
        }
    }

    fun dismissOperatorNotice(noticeId: String) {
        runtimeStore.update { state ->
            if (state.operatorNotice?.id == noticeId) {
                state.copy(operatorNotice = null)
            } else {
                state
            }
        }
    }

    private suspend fun refreshOrdersWithPromptReservation(refresh: suspend () -> Unit) {
        val orderId = phaseExecutionCoordinator.pendingOrderId()
        if (orderId == null) {
            refresh()
            return
        }
        withOrderLock(orderId) {
            refresh()
            reconcileCancelledFormulaPrompt(orderId)
        }
    }

    private fun reconcileCancelledFormulaPrompt(orderId: String) {
        val isCancelled = runtimeStore.snapshot().cancelledOrders.any { it.id == orderId }
        if (!isCancelled) return
        if (!phaseExecutionCoordinator.cancelPreExecutionFormulaPrompt(orderId)) return
        runtimeStore.update { state ->
            state.withFormulaMissingCancellationAlert(orderId)
        }
    }

    private fun log(message: String) {
        runtimeStore.update {
            it.copy(logs = listOf("${System.currentTimeMillis()}  $message") + it.logs.take(11))
        }
        Log.d(TAG_MACHINE_COORDINATOR, message)
    }

    private suspend fun runPlcMaintenanceAction(
        actionLabel: String,
        block: suspend () -> Unit,
    ) {
        runCatching {
            withCriticalMaintenance(reason = "$actionLabel in progress") {
                plcCommandLock.withLock {
                    block()
                }
            }
        }.onFailure { error ->
            val message = error.message ?: "Unknown PLC communication error"
            Log.w(TAG_PLC_DEBUG, "$actionLabel failed: $message", error)
            log("$actionLabel failed: $message")
            runtimeStore.update { state ->
                state.copy(
                    plcState = PlcConnectionState.Fault,
                    lastMessage = "$actionLabel failed: $message",
                )
            }
        }
    }

    private fun nextPlcCommandId(): Int = settingsStore.nextPlcCommandId()
}

internal fun MachineRuntimeState.withFormulaMissingCancellationAlert(orderId: String): MachineRuntimeState {
    val alert =
        OperatorAlert(
            id = "formula-cancelled:$orderId",
            title = "订单已退单",
            message = "该订单已被退单，配方缺失处理已终止，设备未执行加水。",
        )
    return copy(
        operatorAlerts =
            if (operatorAlerts.any { it.id == alert.id }) {
                operatorAlerts
            } else {
                operatorAlerts + alert
            },
        lastMessage = alert.message,
    )
}

private const val TAG_MACHINE_COORDINATOR = "MachineCoordinator"
private const val TAG_PLC_DEBUG = "PlcDebugAction"
