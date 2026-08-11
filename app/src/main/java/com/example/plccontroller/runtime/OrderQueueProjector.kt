package com.example.plccontroller.runtime

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus

object OrderQueueProjector {
    fun project(
        state: MachineRuntimeState,
        loadedOrders: List<Order>,
        warnings: List<String> = emptyList(),
        markNetworkOnline: Boolean = false,
    ): MachineRuntimeState {
        val reservedIds =
            buildSet {
                state.currentOrder?.id?.let(::add)
                addAll(state.waitingTransferOrders.map(Order::id))
                addAll(state.completedOrders.map(Order::id))
                addAll(state.cancelledOrders.map(Order::id))
            }
        val currentActiveOrder = state.currentOrder?.takeIf { it.status == OrderStatus.Dispatching }
        val freshPinnedOrderIds =
            state.pinnedOrderIds.filterTo(mutableSetOf()) { id ->
                loadedOrders.any { it.id == id && it.status.isPendingWaterBucket() }
            }
        val freshPendingOrders =
            (
                loadedOrders
                    .filter { it.status.isPendingWaterBucket() }
                    .filterNot { it.id in reservedIds } + listOfNotNull(currentActiveOrder)
            ).let { OrderQueueOrderingPolicy.pendingWater(it, freshPinnedOrderIds) }
        val freshDelayedOrders =
            loadedOrders
                .filter { it.status == OrderStatus.Delayed }
                .let { OrderQueueOrderingPolicy.pendingWater(it, freshPinnedOrderIds) }
        val backendWaitingTransfer =
            loadedOrders
                .filter { it.status == OrderStatus.WaitingTransfer }
        val mergedWaitingTransfer =
            mergeOrders(
                primary = state.waitingTransferOrders,
                secondary = backendWaitingTransfer,
            ).let {
                OrderQueueOrderingPolicy.waitingTransfer(it, TransferDisplayMode.OldestFirst)
            }
        val freshCompletedOrders =
            loadedOrders
                .filter { it.status == OrderStatus.Completed }
        val mergedCompletedOrders =
            mergeOrders(
                primary = state.completedOrders,
                secondary = freshCompletedOrders,
            ).let(OrderQueueOrderingPolicy::completedHistory)
        val freshCancelledOrders =
            loadedOrders
                .filter { it.status == OrderStatus.Cancelled }
        val mergedCancelledOrders =
            mergeOrders(
                primary = state.cancelledOrders,
                secondary = freshCancelledOrders,
            ).let(OrderQueueOrderingPolicy::cancelledHistory)
        val projected =
            state.copy(
                pinnedOrderIds = freshPinnedOrderIds,
                pendingOrders = freshPendingOrders,
                delayedOrders = freshDelayedOrders,
                waitingTransferOrders = mergedWaitingTransfer,
                completedOrders = mergedCompletedOrders,
                cancelledOrders = mergedCancelledOrders,
            )
        return if (markNetworkOnline) {
            val warningAlerts =
                warnings.distinct().map { warning ->
                    OperatorAlert(
                        id = "sync-warning:$warning",
                        title = "订单需要人工处理",
                        message = warning,
                    )
                }
            val activeWarningIds = warningAlerts.mapTo(mutableSetOf(), OperatorAlert::id)
            val acknowledgedActiveWarningIds =
                state.acknowledgedOperatorAlertIds
                    .filterTo(mutableSetOf()) { it in activeWarningIds }
            val nonSyncAlerts =
                state.operatorAlerts.filterNot {
                    it.id.startsWith(SYNC_WARNING_ALERT_PREFIX)
                }
            projected.copy(
                networkState = NetworkConnectionState.Online,
                operatorAlerts =
                    nonSyncAlerts +
                        warningAlerts.filterNot {
                            it.id in acknowledgedActiveWarningIds
                        },
                acknowledgedOperatorAlertIds =
                    state.acknowledgedOperatorAlertIds
                        .filterTo(mutableSetOf()) { id ->
                            !id.startsWith(SYNC_WARNING_ALERT_PREFIX) || id in activeWarningIds
                        },
                lastMessage =
                    if (warnings.isNotEmpty()) {
                        warnings.joinToString("；")
                    } else {
                        "Synced ${loadedOrders.size} orders"
                    },
            )
        } else {
            projected
        }
    }

    private fun mergeOrders(
        primary: List<Order>,
        secondary: List<Order>,
    ): List<Order> =
        (primary + secondary)
            .distinctBy(Order::id)
}

private const val SYNC_WARNING_ALERT_PREFIX = "sync-warning:"
