package com.example.plccontroller.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MachineRuntimeStore(
    initialState: MachineRuntimeState = MachineRuntimeState().withRefreshedTransferDeck(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<MachineRuntimeState> = _state.asStateFlow()

    fun snapshot(): MachineRuntimeState = _state.value

    fun update(transform: (MachineRuntimeState) -> MachineRuntimeState) {
        _state.update { current ->
            val next = transform(current)
            if (shouldRefreshTransferDeck(current, next)) {
                next.withRefreshedTransferDeck()
            } else {
                next.copy(transferDeck = current.transferDeck)
            }
        }
    }

    private fun shouldRefreshTransferDeck(
        current: MachineRuntimeState,
        next: MachineRuntimeState,
    ): Boolean =
        current.transferDisplayMode != next.transferDisplayMode ||
            current.waitingTransferOrders != next.waitingTransferOrders ||
            current.secondaryDisplayDemoState.enabled != next.secondaryDisplayDemoState.enabled ||
            current.secondaryDisplayDemoState.waitingTransferOrders !=
            next.secondaryDisplayDemoState.waitingTransferOrders
}
