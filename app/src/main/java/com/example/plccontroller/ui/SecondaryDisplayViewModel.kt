package com.example.plccontroller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.plccontroller.AppContainer
import com.example.plccontroller.domain.Order
import com.example.plccontroller.runtime.MachineCoordinator
import com.example.plccontroller.runtime.MachineRuntimeState
import com.example.plccontroller.runtime.MachineRuntimeStore
import com.example.plccontroller.runtime.MaintenanceState
import com.example.plccontroller.runtime.TransferConfirmSource
import com.example.plccontroller.runtime.TransferDeckState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecondaryDisplayUiState(
    val transferDeck: TransferDeckState = TransferDeckState.empty(),
    val waitingTransferOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
    val demoModeEnabled: Boolean = false,
    val maintenanceState: MaintenanceState = MaintenanceState(),
    val lastMessage: String = "System Idle",
)

class SecondaryDisplayViewModel(
    private val runtimeStore: MachineRuntimeStore,
    private val machineCoordinator: MachineCoordinator,
) : ViewModel() {
    val uiState: StateFlow<SecondaryDisplayUiState> =
        runtimeStore.state
            .map(MachineRuntimeState::toSecondaryDisplayUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = runtimeStore.snapshot().toSecondaryDisplayUiState(),
            )

    fun refreshOrders() {
        viewModelScope.launch {
            machineCoordinator.refreshOrders()
        }
    }

    fun confirmTransfer(orderId: String) {
        viewModelScope.launch {
            machineCoordinator.confirmTransfer(
                orderId = orderId,
                source = TransferConfirmSource.SecondaryDisplay,
            )
        }
    }

    fun loadDemoOrders() {
        viewModelScope.launch {
            machineCoordinator.loadSecondaryDisplayDemoData()
        }
    }

    fun clearDemoOrders() {
        viewModelScope.launch {
            machineCoordinator.clearSecondaryDisplayDemoData()
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SecondaryDisplayViewModel(
                        runtimeStore = appContainer.runtimeStore,
                        machineCoordinator = appContainer.machineCoordinator,
                    ) as T
            }
    }
}

private fun MachineRuntimeState.toSecondaryDisplayUiState(): SecondaryDisplayUiState =
    SecondaryDisplayUiState(
        transferDeck = transferDeck,
        waitingTransferOrders = secondaryWaitingTransferOrders(),
        completedOrders = secondaryCompletedOrders(),
        demoModeEnabled = secondaryDisplayDemoState.enabled,
        maintenanceState = maintenanceState,
        lastMessage = lastMessage,
    )
