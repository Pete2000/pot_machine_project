package com.example.plccontroller.runtime

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderQueueProjectorTest {
    @Test
    fun restoredDelayedOrderReturnsToPendingBucketFromLocalProjection() {
        val restored = order("order-1", OrderStatus.PendingWater)
        val state =
            MachineRuntimeState(
                delayedOrders = listOf(restored.copy(status = OrderStatus.Delayed)),
                lastMessage = "keep me",
            )

        val next =
            OrderQueueProjector.project(
                state = state,
                loadedOrders = listOf(restored),
            )

        assertEquals(listOf("order-1"), next.pendingOrders.map(Order::id))
        assertTrue(next.delayedOrders.isEmpty())
        assertEquals("keep me", next.lastMessage)
    }

    @Test
    fun rewateredCancelledOrderReturnsToPendingAfterCancelledBucketIsCleared() {
        val restored = order("order-1", OrderStatus.PendingWater)
        val state =
            MachineRuntimeState(
                cancelledOrders = emptyList(),
            )

        val next =
            OrderQueueProjector.project(
                state = state,
                loadedOrders = listOf(restored),
            )

        assertEquals(listOf("order-1"), next.pendingOrders.map(Order::id))
        assertTrue(next.cancelledOrders.isEmpty())
    }

    @Test
    fun remoteProjectionMarksNetworkOnlineAndMergesCompletedHistory() {
        val existingCompleted =
            order(
                "done-1",
                OrderStatus.Completed,
                transferCompletedAt = "2026-06-17 10:00:00",
            )
        val freshCompleted =
            order(
                "done-2",
                OrderStatus.Completed,
                transferCompletedAt = "2026-06-17 10:05:00",
            )
        val state =
            MachineRuntimeState(
                networkState = NetworkConnectionState.Syncing,
                completedOrders = listOf(existingCompleted),
            )

        val next =
            OrderQueueProjector.project(
                state = state,
                loadedOrders = listOf(freshCompleted),
                markNetworkOnline = true,
            )

        assertEquals(NetworkConnectionState.Online, next.networkState)
        assertEquals(listOf("done-2", "done-1"), next.completedOrders.map(Order::id))
        assertEquals("Synced 1 orders", next.lastMessage)
    }

    @Test
    fun pendingOrdersAreSortedByOrderTimeOldestFirst() {
        val newer = order("newer", OrderStatus.PendingWater, orderTime = "2026-06-17 11:00:00")
        val older = order("older", OrderStatus.PendingWater, orderTime = "2026-06-17 10:00:00")

        val next =
            OrderQueueProjector.project(
                state = MachineRuntimeState(),
                loadedOrders = listOf(newer, older),
            )

        assertEquals(listOf("older", "newer"), next.pendingOrders.map(Order::id))
    }

    @Test
    fun waitingTransferOrdersAreStoredOldestFirstByWaterCompletedAt() {
        val newer =
            order(
                "newer",
                OrderStatus.WaitingTransfer,
                waterCompletedAt = "2026-06-17 10:10:00",
            )
        val older =
            order(
                "older",
                OrderStatus.WaitingTransfer,
                waterCompletedAt = "2026-06-17 10:00:00",
            )

        val next =
            OrderQueueProjector.project(
                state = MachineRuntimeState(),
                loadedOrders = listOf(newer, older),
            )

        assertEquals(listOf("older", "newer"), next.waitingTransferOrders.map(Order::id))
        assertEquals(
            listOf("newer", "older"),
            OrderQueueOrderingPolicy
                .waitingTransfer(
                    next.waitingTransferOrders,
                    TransferDisplayMode.LatestFirst,
                ).map(Order::id),
        )
    }

    @Test
    fun cancelledHistoryIsSortedLatestFirstByCancelledAt() {
        val older =
            order(
                "older",
                OrderStatus.Cancelled,
                cancelledAt = "2026-06-17 10:00:00",
            )
        val newer =
            order(
                "newer",
                OrderStatus.Cancelled,
                cancelledAt = "2026-06-17 10:15:00",
            )

        val next =
            OrderQueueProjector.project(
                state = MachineRuntimeState(),
                loadedOrders = listOf(older, newer),
            )

        assertEquals(listOf("newer", "older"), next.cancelledOrders.map(Order::id))
    }

    @Test
    fun pendingOrdersPrioritizeManuallyPinnedOrdersUnderDispatchingOrder() {
        val regularOld = order("reg-old", OrderStatus.PendingWater, orderTime = "2026-06-17 10:00:00")
        val regularNew = order("reg-new", OrderStatus.PendingWater, orderTime = "2026-06-17 10:30:00")
        val urgedOld = order("urged-old", OrderStatus.PendingWater, orderTime = "2026-06-17 10:10:00", operation = "302")
        val urgedNew = order("urged-new", OrderStatus.PendingWater, orderTime = "2026-06-17 10:20:00", operation = "302")
        val dispatching = order("disp", OrderStatus.Dispatching, orderTime = "2026-06-17 09:50:00")

        // 1. Without pinning, everything is sorted chronologically under dispatching:
        // Expected order: disp, reg-old, urged-old, urged-new, reg-new
        val state = MachineRuntimeState(currentOrder = dispatching)
        val next1 =
            OrderQueueProjector.project(
                state = state,
                loadedOrders = listOf(regularOld, regularNew, urgedOld, urgedNew),
            )
        assertEquals(
            listOf("disp", "reg-old", "urged-old", "urged-new", "reg-new"),
            next1.pendingOrders.map(Order::id),
        )

        // 2. Pin urged-new manually:
        // Expected order: disp, urged-new (pinned), reg-old, urged-old, reg-new
        val statePinned = state.copy(pinnedOrderIds = setOf("urged-new"))
        val next2 =
            OrderQueueProjector.project(
                state = statePinned,
                loadedOrders = listOf(regularOld, regularNew, urgedOld, urgedNew),
            )
        assertEquals(
            listOf("disp", "urged-new", "reg-old", "urged-old", "reg-new"),
            next2.pendingOrders.map(Order::id),
        )
    }

    @Test
    fun syncWarningCreatesPersistentOperatorAlertUntilAcknowledged() {
        val warning = "退单已进入制作或完成，需要人工处理：10"
        val first =
            OrderQueueProjector.project(
                state = MachineRuntimeState(),
                loadedOrders = emptyList(),
                warnings = listOf(warning),
                markNetworkOnline = true,
            )

        assertEquals(warning, first.operatorAlerts.single().message)

        val alertId = first.operatorAlerts.single().id
        val acknowledged =
            first.copy(
                operatorAlerts = emptyList(),
                acknowledgedOperatorAlertIds = setOf(alertId),
            )
        val repeated =
            OrderQueueProjector.project(
                state = acknowledged,
                loadedOrders = emptyList(),
                warnings = listOf(warning),
                markNetworkOnline = true,
            )

        assertTrue(repeated.operatorAlerts.isEmpty())
        assertTrue(alertId in repeated.acknowledgedOperatorAlertIds)
    }

    private fun order(
        id: String,
        status: OrderStatus,
        orderTime: String? = null,
        waterCompletedAt: String? = null,
        cancelledAt: String? = null,
        transferCompletedAt: String? = null,
        operation: String? = null,
    ): Order =
        Order(
            id = id,
            recipeCode = "recipe-$id",
            quantity = 1,
            targetTemperature = 0,
            cookSeconds = 0,
            spiceLevel = 0,
            status = status,
            orderTime = orderTime,
            waterCompletedAt = waterCompletedAt,
            cancelledAt = cancelledAt,
            transferCompletedAt = transferCompletedAt,
            operation = operation,
        )
}
