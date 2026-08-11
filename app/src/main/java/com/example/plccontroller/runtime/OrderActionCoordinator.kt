package com.example.plccontroller.runtime

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("ComplexCondition")
internal class OrderActionCoordinator(
    private val runtimeStore: MachineRuntimeStore,
    private val orderLifecycle: OrderLifecycleController,
    private val phaseExecutionCoordinator: PhaseExecutionCoordinator,
    private val withOrderLock: suspend (String, suspend () -> Unit) -> Unit,
    private val plcCommandLock: Mutex,
    private val externalScope: CoroutineScope,
    private val refreshOrdersLocal: suspend () -> Unit,
    private val log: (String) -> Unit,
) {
    fun setOrderDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        externalScope.launch {
            orderLifecycle.setDelayed(orderId, delayed)
            log("Order $orderId delayed status set to $delayed")
            refreshOrdersLocal()
        }
    }

    fun toggleOrderPinned(orderId: String) {
        runtimeStore.update { state ->
            val nextPinned =
                if (orderId in state.pinnedOrderIds) {
                    state.pinnedOrderIds - orderId
                } else {
                    state.pinnedOrderIds + orderId
                }
            state.copy(
                pinnedOrderIds = nextPinned,
                pendingOrders = OrderQueueOrderingPolicy.pendingWater(state.pendingOrders, nextPinned),
            )
        }
        log("Toggled manual pin for order $orderId")
    }

    fun rewaterOrder(order: Order) {
        externalScope.launch {
            orderLifecycle.resetToPendingWater(order.id)
            log("Rewater order: status of ${order.id} set back to PendingWater")
            runtimeStore.update { state ->
                orderLifecycle.applyRewatered(state, order)
            }
            refreshOrdersLocal()
        }
    }

    suspend fun cancelPendingOrder(order: Order) {
        withOrderLock(order.id) {
            val snapshot = runtimeStore.snapshot()
            val latestOrder =
                snapshot.pendingOrders.firstOrNull { it.id == order.id }
                    ?: snapshot.delayedOrders.firstOrNull { it.id == order.id }
                    ?: snapshot.currentOrder?.takeIf { it.id == order.id }

            if (latestOrder == null) {
                runtimeStore.update { state ->
                    state.copy(lastMessage = "找不到订单 ${order.id}，或该订单已完成/已取消")
                }
                return@withOrderLock
            }

            if (latestOrder.status != OrderStatus.Pending &&
                latestOrder.status != OrderStatus.PendingWater &&
                latestOrder.status != OrderStatus.Delayed
            ) {
                runtimeStore.update { state ->
                    state.copy(lastMessage = "订单 ${order.id} 正在执行或已制作完成，不能取消")
                }
                log("Cancel pending order ignored because status is ${latestOrder.status}")
                return@withOrderLock
            }

            val formulaPromptCleared =
                phaseExecutionCoordinator
                    .cancelPreExecutionFormulaPrompt(order.id)
            orderLifecycle.markCancelled(order.id)
            runtimeStore.update { state ->
                val cancelledState =
                    orderLifecycle.applyPendingOrderCancelled(
                        state = state,
                        order = latestOrder,
                        message = "已取消待加水订单 ${order.id}",
                    )
                if (formulaPromptCleared) {
                    cancelledState.withFormulaMissingCancellationAlert(order.id)
                } else {
                    cancelledState
                }
            }
            log("Pending order ${order.id} canceled by operator.")
        }
    }

    suspend fun dispatchOrder(order: Order) {
        if (runtimeStore.snapshot().maintenanceState.active) {
            log("Dispatch blocked by maintenance mode")
            return
        }

        withOrderLock(order.id) {
            val snapshot = runtimeStore.snapshot()
            val latestOrder =
                snapshot.pendingOrders.firstOrNull { it.id == order.id }
                    ?: snapshot.delayedOrders.firstOrNull { it.id == order.id }
            if (latestOrder == null ||
                (
                    latestOrder.status != OrderStatus.Pending &&
                        latestOrder.status != OrderStatus.PendingWater &&
                        latestOrder.status != OrderStatus.Delayed
                )
            ) {
                log("Dispatch ignored because order ${order.id} is no longer pending.")
                return@withOrderLock
            }
            plcCommandLock.withLock {
                phaseExecutionCoordinator.dispatchOrder(latestOrder)
            }
        }
    }
}
