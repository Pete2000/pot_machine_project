package com.example.plccontroller.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.plccontroller.AppContainer
import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.DeviceTypeDto
import com.example.plccontroller.data.http.EquipmentDto
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.ManualWaterPhaseSlot
import com.example.plccontroller.domain.ManualWaterPotMode
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.FaultMeltdownState
import com.example.plccontroller.runtime.MachineCoordinator
import com.example.plccontroller.runtime.MachineRuntimeState
import com.example.plccontroller.runtime.MachineRuntimeStore
import com.example.plccontroller.runtime.NetworkConnectionState
import com.example.plccontroller.runtime.OperatorAlert
import com.example.plccontroller.runtime.OperatorNotice
import com.example.plccontroller.runtime.PhaseRotationPrompt
import com.example.plccontroller.runtime.TransferConfirmSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val plcState: PlcConnectionState = PlcConnectionState.Disconnected,
    val networkState: NetworkConnectionState = NetworkConnectionState.Idle,
    val automationEnabled: Boolean = false,
    val currentOrder: Order? = null,
    val pendingOrders: List<Order> = emptyList(),
    val waitingTransferOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
    val cancelledOrders: List<Order> = emptyList(),
    val formulaCatalog: FormulaCatalog? = null,
    val formulaSourceLabel: String = "Local Default",
    val availableCatalogs: List<FormulaCatalog> = emptyList(),
    val delayedOrders: List<Order> = emptyList(),
    val urgedOrders: List<Order> = emptyList(),
    val pinnedOrderIds: Set<String> = emptySet(),
    val manualPhasePrompt: String? = null,
    val manualPhaseTargetLogicalSlot: Int? = null,
    val phaseRotationPrompt: PhaseRotationPrompt? = null,
    val operatorAlerts: List<OperatorAlert> = emptyList(),
    val operatorNotice: OperatorNotice? = null,
    val phaseCurrentTopLeftLogicalSlot: Int = 1,
    val manualPhaseCompletionToken: Int = 0,
    val communicationConfig: PlcCommunicationConfig? = null,
    val businessUrl: String = "",
    val managementUrl: String = "",
    val deviceCode: String = "",
    val formulaSyncIntervalSeconds: Long = 30L,
    val deviceConfig: AppPersistentConfig? = null,
    val plcPollingSnapshot: PlcPollingSnapshot = PlcPollingSnapshot(),
    val faultMeltdownState: FaultMeltdownState = FaultMeltdownState(),
    val lastMessage: String = "System Idle",
    val logs: List<String> = listOf("Application started"),
)

class MainViewModel(
    private val runtimeStore: MachineRuntimeStore,
    private val machineCoordinator: MachineCoordinator,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private var lastOrderRefreshAtMs: Long = 0L

    val uiState: StateFlow<MainUiState> =
        runtimeStore.state
            .map { it.toMainUiState(settingsStore.snapshot()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = runtimeStore.snapshot().toMainUiState(settingsStore.snapshot()),
            )

    fun toggleAutomation() {
        machineCoordinator.setAutomationEnabled(!uiState.value.automationEnabled)
    }

    fun refreshOrders() {
        refreshOrdersIfStale(minIntervalMs = 0L)
    }

    fun refreshOrdersIfStale(minIntervalMs: Long = 3_000L) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOrderRefreshAtMs < minIntervalMs) {
            return
        }
        lastOrderRefreshAtMs = now
        viewModelScope.launch {
            machineCoordinator.refreshOrders()
        }
    }

    fun refreshFormulaCatalog() {
        viewModelScope.launch {
            machineCoordinator.refreshFormulaCatalog()
        }
    }

    fun selectFormulaCatalog(formulaCode: String) {
        viewModelScope.launch {
            machineCoordinator.selectFormulaCatalog(formulaCode)
        }
    }

    fun setOrderDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        machineCoordinator.setOrderDelayed(orderId, delayed)
    }

    fun toggleOrderPinned(orderId: String) {
        machineCoordinator.toggleOrderPinned(orderId)
    }

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    fun startRegistration() {
        Log.d("Registration", "startRegistration() called")
        viewModelScope.launch {
            Log.d("Registration", "Setting state to LoadingTypes")
            _registrationState.value = RegistrationState.LoadingTypes
            runCatching {
                machineCoordinator.fetchDeviceTypes()
            }.onSuccess { types ->
                Log.d("Registration", "fetchDeviceTypes success: ${types.size} types found")
                if (types.isEmpty()) {
                    _registrationState.value = RegistrationState.Error("没有获取到任何设备类型")
                } else {
                    _registrationState.value = RegistrationState.SelectingType(types)
                }
            }.onFailure { error ->
                Log.e("Registration", "fetchDeviceTypes failed", error)
                _registrationState.value = RegistrationState.Error("获取设备类型失败: ${error.message}")
            }
        }
    }

    fun selectDeviceType(typeCode: String) {
        Log.d("Registration", "selectDeviceType($typeCode) called")
        viewModelScope.launch {
            Log.d("Registration", "Setting state to LoadingEquipment")
            _registrationState.value = RegistrationState.LoadingEquipment
            runCatching {
                machineCoordinator.fetchEquipment(typeCode)
            }.onSuccess { equipments ->
                Log.d("Registration", "fetchEquipment success: ${equipments.size} equipments found")
                if (equipments.isEmpty()) {
                    _registrationState.value = RegistrationState.Error("该类型下无可用设备列表")
                } else {
                    _registrationState.value = RegistrationState.SelectingEquipment(equipments, typeCode)
                }
            }.onFailure { error ->
                Log.e("Registration", "fetchEquipment failed", error)
                _registrationState.value = RegistrationState.Error("获取设备列表失败: ${error.message}")
            }
        }
    }

    fun selectEquipment(equipment: EquipmentDto) {
        Log.d("Registration", "selectEquipment(${equipment.equipmentName}) called")
        viewModelScope.launch {
            Log.d("Registration", "Setting state to Saving")
            _registrationState.value = RegistrationState.Saving
            runCatching {
                require(equipment.deviceCode.isNotBlank()) { "设备编码为空" }
                // Bind device code to local store
                machineCoordinator.updateSettings { store ->
                    store.updateDeviceCode(equipment.deviceCode.trim())
                }
                // Notify machine coordinator to refresh (or do it here)
                runtimeStore.update {
                    it.copy(
                        networkState = NetworkConnectionState.Online,
                        lastMessage = "设备已绑定: ${equipment.equipmentName}",
                    )
                }
            }.onSuccess {
                Log.d("Registration", "selectEquipment success for ${equipment.equipmentName}")
                _registrationState.value = RegistrationState.Success(equipment.deviceCode, equipment.equipmentName)
            }.onFailure { error ->
                Log.e("Registration", "selectEquipment failed", error)
                _registrationState.value = RegistrationState.Error("绑定设备失败: ${error.message}")
            }
        }
    }

    fun resetRegistrationState() {
        Log.d("Registration", "resetRegistrationState() called, setting state to Idle")
        _registrationState.value = RegistrationState.Idle
    }

    fun registerFirstEquipment() {
        viewModelScope.launch {
            machineCoordinator.registerFirstEquipment()
        }
    }

    fun saveFormulaParameter(update: FormulaParameterUpdate) {
        viewModelScope.launch {
            machineCoordinator.saveFormulaParameter(update)
        }
    }

    fun dispatchCurrentOrder() {
        uiState.value.pendingOrders
            .firstOrNull()
            ?.let(::dispatchOrder)
    }

    fun dispatchOrder(order: Order) {
        if (uiState.value.currentOrder?.id == order.id) {
            return
        }
        viewModelScope.launch {
            machineCoordinator.dispatchOrder(order)
        }
    }

    fun cancelPendingOrder(order: Order) {
        viewModelScope.launch {
            machineCoordinator.cancelPendingOrder(order)
        }
    }

    fun rewaterOrder(order: Order) {
        viewModelScope.launch {
            machineCoordinator.rewaterOrder(order)
        }
    }

    fun requestWater() {
        val targetOrder = uiState.value.currentOrder ?: uiState.value.pendingOrders.firstOrNull()
        if (targetOrder == null) {
            machineCoordinator.requestWater()
        } else {
            dispatchOrder(targetOrder)
        }
    }

    internal fun requestManualWater(
        mode: ManualPotMode,
        recipes: List<ManualRecipeOption>,
    ) {
        val request =
            ManualWaterPhaseRequest(
                mode = mode.toManualWaterPotMode(),
                slots =
                    recipes.mapIndexed { index, recipe ->
                        ManualWaterPhaseSlot(
                            logicalSlot = index + 1,
                            waterDurationMs = recipe.potType?.waterDurationMs ?: DEFAULT_MANUAL_WATER_DURATION_MS,
                            chickenOilDurationMs = recipe.potType?.chickenOilDurationMs ?: 0L,
                            bonePasteDurationMs = recipe.potType?.bonePasteDurationMs ?: 0L,
                            label = recipe.name,
                            formulaMissing = recipe.potType == null,
                        )
                    },
            )
        viewModelScope.launch {
            machineCoordinator.sendManualWaterPhaseCommand(request)
        }
    }

    fun continueManualPhase() {
        viewModelScope.launch {
            machineCoordinator.continueManualPhaseCommand()
        }
    }

    fun confirmPhaseRotation() {
        viewModelScope.launch {
            machineCoordinator.confirmPendingPhaseRotation()
        }
    }

    fun cancelPhaseRotation() {
        viewModelScope.launch {
            machineCoordinator.cancelPendingPhaseRotation()
        }
    }

    fun acknowledgeOperatorAlert(alertId: String) {
        machineCoordinator.acknowledgeOperatorAlert(alertId)
    }

    fun dismissOperatorNotice(noticeId: String) {
        machineCoordinator.dismissOperatorNotice(noticeId)
    }

    fun resolveFaultMeltdown(completeInterruptedOrder: Boolean) {
        viewModelScope.launch {
            machineCoordinator.resolveFaultMeltdown(completeInterruptedOrder)
        }
    }

    fun refreshPlcStatus() {
        viewModelScope.launch {
            machineCoordinator.refreshPlcSnapshotNow()
        }
    }

    fun sendDebugHeartbeatPulse() {
        viewModelScope.launch {
            machineCoordinator.triggerDebugHeartbeatPulse()
        }
    }

    fun sendDebugHeaterTestCommand() {
        viewModelScope.launch {
            machineCoordinator.sendDebugHeaterTestCommand()
        }
    }

    fun sendDebugEmergencyWaterTestCommand() {
        viewModelScope.launch {
            machineCoordinator.sendDebugEmergencyWaterTestCommand()
        }
    }

    fun sendDebugPhaseTestCommand() {
        viewModelScope.launch {
            machineCoordinator.sendDebugPhaseTestCommand()
        }
    }

    fun clearAllLocalOrdersForDebug() {
        viewModelScope.launch {
            machineCoordinator.clearAllLocalOrdersForDebug()
        }
    }

    fun updateSettings(action: (com.example.plccontroller.data.SettingsStore) -> Unit) {
        machineCoordinator.updateSettings(action)
    }

    fun beginHeaterActuatorConfigEdit() {
        machineCoordinator.pauseHeaterForActuatorConfigEdit()
    }

    fun cancelHeaterActuatorConfigEdit() {
        machineCoordinator.resumeHeaterAfterActuatorConfigEdit()
    }

    fun saveHeaterActuatorConfig(action: (com.example.plccontroller.data.SettingsStore) -> Unit) {
        machineCoordinator.saveHeaterActuatorConfig(action)
    }

    fun confirmTransfer(order: Order) {
        viewModelScope.launch {
            machineCoordinator.confirmTransfer(
                orderId = order.id,
                source = TransferConfirmSource.MainScreen,
            )
        }
    }

    fun loadSecondaryDisplayDemoData() {
        viewModelScope.launch {
            machineCoordinator.loadSecondaryDisplayDemoData()
        }
    }

    fun clearSecondaryDisplayDemoData() {
        viewModelScope.launch {
            machineCoordinator.clearSecondaryDisplayDemoData()
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(
                        runtimeStore = appContainer.runtimeStore,
                        machineCoordinator = appContainer.machineCoordinator,
                        settingsStore = appContainer.settingsStore,
                    ) as T
            }
    }
}

private fun ManualPotMode.toManualWaterPotMode(): ManualWaterPotMode =
    when (this) {
        ManualPotMode.Small -> ManualWaterPotMode.Small
        ManualPotMode.Single -> ManualWaterPotMode.Single
        ManualPotMode.Split -> ManualWaterPotMode.Split
        ManualPotMode.ThreeGrid -> ManualWaterPotMode.ThreeGrid
        ManualPotMode.FourGrid -> ManualWaterPotMode.FourGrid
    }

private fun MachineRuntimeState.toMainUiState(settings: com.example.plccontroller.data.AppPersistentConfig): MainUiState =
    MainUiState(
        plcState = plcState,
        networkState = networkState,
        automationEnabled = automationEnabled,
        currentOrder = currentOrder,
        pendingOrders = pendingOrders,
        waitingTransferOrders = waitingTransferOrders,
        completedOrders = completedOrders,
        cancelledOrders = cancelledOrders,
        formulaCatalog = formulaCatalog,
        formulaSourceLabel = formulaSourceLabel,
        availableCatalogs = availableCatalogs,
        delayedOrders = delayedOrders,
        urgedOrders = pendingOrders.filter { it.isUrged },
        pinnedOrderIds = pinnedOrderIds,
        manualPhasePrompt = manualPhasePrompt,
        manualPhaseTargetLogicalSlot = manualPhaseTargetLogicalSlot,
        phaseRotationPrompt = phaseRotationPrompt,
        operatorAlerts = operatorAlerts,
        operatorNotice = operatorNotice,
        phaseCurrentTopLeftLogicalSlot = phaseCurrentTopLeftLogicalSlot,
        manualPhaseCompletionToken = manualPhaseCompletionToken,
        communicationConfig = communicationConfig,
        businessUrl = settings.businessUrl,
        managementUrl = settings.managementUrl,
        deviceCode = settings.deviceCode,
        formulaSyncIntervalSeconds = settings.formulaSyncIntervalSeconds,
        deviceConfig = settings,
        plcPollingSnapshot = plcPollingSnapshot,
        faultMeltdownState = faultMeltdownState,
        lastMessage = lastMessage,
        logs = logs,
    )

private const val DEFAULT_MANUAL_WATER_DURATION_MS = 10_000L

sealed interface RegistrationState {
    object Idle : RegistrationState

    object LoadingTypes : RegistrationState

    data class SelectingType(
        val types: List<DeviceTypeDto>,
    ) : RegistrationState

    object LoadingEquipment : RegistrationState

    data class SelectingEquipment(
        val equipments: List<EquipmentDto>,
        val selectedTypeCode: String,
    ) : RegistrationState

    object Saving : RegistrationState

    data class Error(
        val message: String,
    ) : RegistrationState

    data class Success(
        val deviceCode: String,
        val equipmentName: String,
    ) : RegistrationState
}
