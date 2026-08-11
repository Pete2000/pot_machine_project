package com.example.plccontroller.runtime

import com.example.plccontroller.AppConfig
import com.example.plccontroller.data.http.HttpOrderRepository
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.domain.withLifecycleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OrderLifecycleGateway {
    suspend fun setDelayed(
        orderId: String,
        delayed: Boolean,
    )

    suspend fun resetToPendingWater(orderId: String)

    suspend fun markDispatchStarted(orderId: String)

    suspend fun markDispatchResult(
        orderId: String,
        success: Boolean,
        message: String,
    )

    suspend fun markCancelled(orderId: String)

    suspend fun markCompleted(
        deviceCode: String,
        orderId: String,
    )

    suspend fun markPendingWater(orderId: String)
}

class HttpOrderLifecycleGateway(
    private val orderRepository: HttpOrderRepository,
) : OrderLifecycleGateway {
    override suspend fun setDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        orderRepository.setOrderDelayed(orderId, delayed)
    }

    override suspend fun resetToPendingWater(orderId: String) {
        orderRepository.markPendingWater(orderId)
    }

    override suspend fun markDispatchStarted(orderId: String) {
        withContext(Dispatchers.IO) {
            orderRepository.markStarted(orderId)
        }
    }

    override suspend fun markDispatchResult(
        orderId: String,
        success: Boolean,
        message: String,
    ) {
        withContext(Dispatchers.IO) {
            orderRepository.markFinished(
                orderId = orderId,
                success = success,
                message = message,
            )
        }
    }

    override suspend fun markCancelled(orderId: String) {
        withContext(Dispatchers.IO) {
            orderRepository.markCancelled(orderId)
        }
    }

    override suspend fun markCompleted(
        deviceCode: String,
        orderId: String,
    ) {
        withContext(Dispatchers.IO) {
            orderRepository.markCompleted(deviceCode = deviceCode, orderId = orderId)
        }
    }

    override suspend fun markPendingWater(orderId: String) {
        withContext(Dispatchers.IO) {
            orderRepository.markPendingWater(orderId)
        }
    }
}

class OrderLifecycleController(
    private val gateway: OrderLifecycleGateway,
) {
    suspend fun setDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        gateway.setDelayed(orderId, delayed)
    }

    suspend fun resetToPendingWater(orderId: String) {
        gateway.resetToPendingWater(orderId)
    }

    suspend fun markDispatchStarted(orderId: String) {
        gateway.markDispatchStarted(orderId)
    }

    suspend fun markDispatchResult(
        orderId: String,
        success: Boolean,
        message: String,
    ) {
        gateway.markDispatchResult(orderId, success, message)
    }

    suspend fun markCancelled(orderId: String) {
        gateway.markCancelled(orderId)
    }

    suspend fun markCompleted(
        deviceCode: String,
        orderId: String,
    ) {
        gateway.markCompleted(deviceCode, orderId)
    }

    suspend fun markPendingWater(orderId: String) {
        gateway.markPendingWater(orderId)
    }

    fun applyDispatchStarted(
        state: MachineRuntimeState,
        order: Order,
    ): MachineRuntimeState {
        val dispatchingOrder = order.withLifecycleStatus(OrderStatus.Dispatching)
        val updatedPending =
            if (state.pendingOrders.any { it.id == order.id }) {
                state.pendingOrders.map { pending ->
                    if (pending.id == order.id) dispatchingOrder else pending
                }
            } else {
                state.pendingOrders + dispatchingOrder
            }
        return state.copy(
            currentOrder = dispatchingOrder,
            pendingOrders = OrderQueueOrderingPolicy.pendingWater(updatedPending),
            waitingTransferOrders =
                state.waitingTransferOrders.filterNot { waiting ->
                    waiting.id == order.id
                },
            cancelledOrders =
                state.cancelledOrders.filterNot { cancelled ->
                    cancelled.id == order.id
                },
            lastMessage = "Dispatching order ${order.id}",
        )
    }

    fun applyReturnedToPendingWater(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            currentOrder = null,
            pendingOrders =
                OrderQueueOrderingPolicy.pendingWater(
                    prependUniqueOrder(
                        state.pendingOrders,
                        order.withLifecycleStatus(OrderStatus.PendingWater),
                    ),
                ),
            phaseRotationPrompt = null,
            orderPhaseReservation = null,
            manualPhasePrompt = null,
            manualPhaseTargetLogicalSlot = null,
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applyRewatered(
        state: MachineRuntimeState,
        order: Order,
    ): MachineRuntimeState =
        state.copy(
            waitingTransferOrders = state.waitingTransferOrders.filterNot { it.id == order.id },
            cancelledOrders = state.cancelledOrders.filterNot { it.id == order.id },
            phaseCurrentTopLeftLogicalSlot = 1,
        )

    fun applyPendingOrderCancelled(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
            cancelledOrders =
                OrderQueueOrderingPolicy.cancelledHistory(
                    prependUniqueOrder(
                        state.cancelledOrders,
                        order.withLifecycleStatus(OrderStatus.Cancelled),
                        maxSize = AppConfig.localOrderHistoryLimit,
                    ),
                ),
            orderPhaseReservation = null,
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applyDispatchFailed(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            currentOrder = null,
            pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
            cancelledOrders =
                OrderQueueOrderingPolicy.cancelledHistory(
                    prependUniqueOrder(
                        state.cancelledOrders,
                        order.withLifecycleStatus(OrderStatus.Cancelled),
                        maxSize = AppConfig.localOrderHistoryLimit,
                    ),
                ),
            plcState = PlcConnectionState.Fault,
            phaseRotationPrompt = null,
            orderPhaseReservation = null,
            manualPhasePrompt = null,
            manualPhaseTargetLogicalSlot = null,
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applySkippedWaitingTransfer(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            currentOrder = null,
            pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
            waitingTransferOrders =
                OrderQueueOrderingPolicy.waitingTransfer(
                    appendUniqueOrder(
                        state.waitingTransferOrders,
                        order.withLifecycleStatus(OrderStatus.WaitingTransfer),
                    ),
                    TransferDisplayMode.OldestFirst,
                ),
            phaseRotationPrompt = null,
            orderPhaseReservation = null,
            manualPhasePrompt = null,
            manualPhaseTargetLogicalSlot = null,
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applyWaitingTransfer(
        state: MachineRuntimeState,
        order: Order,
        latestSnapshot: PlcPollingSnapshot,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            currentOrder = null,
            pendingOrders = state.pendingOrders.filterNot { it.id == order.id },
            waitingTransferOrders =
                OrderQueueOrderingPolicy.waitingTransfer(
                    appendUniqueOrder(
                        state.waitingTransferOrders,
                        order.withLifecycleStatus(OrderStatus.WaitingTransfer),
                    ),
                    TransferDisplayMode.OldestFirst,
                ),
            plcState = PlcConnectionState.Connected,
            plcPollingSnapshot = latestSnapshot,
            phaseRotationPrompt = null,
            orderPhaseReservation = null,
            manualPhasePrompt = null,
            manualPhaseTargetLogicalSlot = null,
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applyLiveTransferCompleted(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            waitingTransferOrders = state.waitingTransferOrders.filterNot { it.id == order.id },
            completedOrders =
                OrderQueueOrderingPolicy.completedHistory(
                    prependUniqueOrder(
                        state.completedOrders,
                        order.withLifecycleStatus(OrderStatus.Completed),
                        maxSize = AppConfig.localOrderHistoryLimit,
                    ),
                ),
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    fun applyDemoTransferCompleted(
        state: MachineRuntimeState,
        order: Order,
        message: String,
    ): MachineRuntimeState =
        state.copy(
            secondaryDisplayDemoState =
                state.secondaryDisplayDemoState.copy(
                    waitingTransferOrders =
                        state.secondaryDisplayDemoState.waitingTransferOrders
                            .filterNot { it.id == order.id },
                    completedOrders =
                        OrderQueueOrderingPolicy.completedHistory(
                            prependUniqueOrder(
                                state.secondaryDisplayDemoState.completedOrders,
                                order.withLifecycleStatus(OrderStatus.Completed),
                                maxSize = AppConfig.localOrderHistoryLimit,
                            ),
                        ),
                ),
            phaseCurrentTopLeftLogicalSlot = 1,
            lastMessage = message,
        )

    private fun appendUniqueOrder(
        orders: List<Order>,
        order: Order,
    ): List<Order> {
        if (orders.any { it.id == order.id }) {
            return orders
        }
        return orders + order
    }

    private fun prependUniqueOrder(
        orders: List<Order>,
        order: Order,
        maxSize: Int = Int.MAX_VALUE,
    ): List<Order> =
        (listOf(order) + orders.filterNot { it.id == order.id })
            .take(maxSize)
}
