package com.example.plccontroller.runtime

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderLifecycleControllerTest {
    private val gateway = FakeOrderLifecycleGateway()
    private val controller = OrderLifecycleController(gateway)

    @Test
    fun dispatchStartedMovesOrderIntoCurrentAndRemovesDuplicateBuckets() {
        val target = order("order-1")
        val state =
            MachineRuntimeState(
                pendingOrders = listOf(order("other"), target),
                waitingTransferOrders = listOf(target.copy(status = OrderStatus.WaitingTransfer)),
                cancelledOrders = listOf(target.copy(status = OrderStatus.Cancelled)),
            )

        val next = controller.applyDispatchStarted(state, target)

        assertEquals("order-1", next.currentOrder?.id)
        assertEquals(OrderStatus.Dispatching, next.currentOrder?.status)
        assertEquals(listOf("order-1", "other"), next.pendingOrders.map(Order::id))
        assertEquals(OrderStatus.Dispatching, next.pendingOrders.first().status)
        assertTrue(next.waitingTransferOrders.none { it.id == "order-1" })
        assertTrue(next.cancelledOrders.none { it.id == "order-1" })
    }

    @Test
    fun returnedToPendingWaterClearsPromptsAndRequeuesOrderFirst() {
        val target = order("order-1", OrderStatus.Dispatching)
        val state =
            MachineRuntimeState(
                currentOrder = target,
                pendingOrders = listOf(order("other")),
                phaseRotationPrompt =
                    PhaseRotationPrompt(
                        source = PhaseFlowSource.Order,
                        title = "需要转锅",
                        message = "请确认",
                    ),
                manualPhasePrompt = "manual prompt",
                manualPhaseTargetLogicalSlot = 2,
            )

        val next =
            controller.applyReturnedToPendingWater(
                state = state,
                order = target,
                message = "returned",
            )

        assertNull(next.currentOrder)
        assertEquals(listOf("order-1", "other"), next.pendingOrders.map(Order::id))
        assertEquals(OrderStatus.PendingWater, next.pendingOrders.first().status)
        assertNull(next.phaseRotationPrompt)
        assertNull(next.manualPhasePrompt)
        assertNull(next.manualPhaseTargetLogicalSlot)
        assertEquals("returned", next.lastMessage)
    }

    @Test
    fun waitingTransferClearsCurrentOrderAndMarksPlcConnected() {
        val target = order("order-1", OrderStatus.Dispatching)
        val snapshot = PlcPollingSnapshot(lastHeartbeatValue = true)
        val state =
            MachineRuntimeState(
                plcState = PlcConnectionState.Fault,
                currentOrder = target,
            )

        val next =
            controller.applyWaitingTransfer(
                state = state,
                order = target,
                latestSnapshot = snapshot,
                message = "waiting transfer",
            )

        assertNull(next.currentOrder)
        assertEquals(listOf("order-1"), next.waitingTransferOrders.map(Order::id))
        assertEquals(OrderStatus.WaitingTransfer, next.waitingTransferOrders.first().status)
        assertEquals(PlcConnectionState.Connected, next.plcState)
        assertEquals(snapshot, next.plcPollingSnapshot)
    }

    @Test
    fun skippedWaitingTransferDoesNotMutatePlcState() {
        val target = order("order-1", OrderStatus.PendingWater)
        val snapshot = PlcPollingSnapshot(lastHeartbeatValue = false)
        val state =
            MachineRuntimeState(
                plcState = PlcConnectionState.Fault,
                plcPollingSnapshot = snapshot,
                pendingOrders = listOf(target),
            )

        val next =
            controller.applySkippedWaitingTransfer(
                state = state,
                order = target,
                message = "该订单配方无需设备执行，已跳过加水",
            )

        assertEquals(PlcConnectionState.Fault, next.plcState)
        assertEquals(snapshot, next.plcPollingSnapshot)
        assertTrue(next.pendingOrders.none { it.id == "order-1" })
        assertEquals(OrderStatus.WaitingTransfer, next.waitingTransferOrders.single().status)
        assertEquals("该订单配方无需设备执行，已跳过加水", next.lastMessage)
    }

    @Test
    fun liveTransferCompletedMovesOrderIntoCompletedHistory() {
        val target = order("order-1", OrderStatus.WaitingTransfer)
        val state =
            MachineRuntimeState(
                waitingTransferOrders = listOf(target, order("other", OrderStatus.WaitingTransfer)),
            )

        val next =
            controller.applyLiveTransferCompleted(
                state = state,
                order = target,
                message = "completed",
            )

        assertFalse(next.waitingTransferOrders.any { it.id == "order-1" })
        assertEquals("order-1", next.completedOrders.first().id)
        assertEquals(OrderStatus.Completed, next.completedOrders.first().status)
        assertEquals("completed", next.lastMessage)
    }

    @Test
    fun gatewayCallsAreDelegated() =
        runTest {
            controller.setDelayed("order-1", true)
            controller.resetToPendingWater("order-2")
            controller.markDispatchStarted("order-3")
            controller.markDispatchResult("order-4", success = true, message = "ok")
            controller.markCancelled("order-5")
            controller.markCompleted(deviceCode = "device-1", orderId = "order-6")
            controller.markPendingWater("order-7")

            assertEquals(
                listOf(
                    "setDelayed:order-1:true",
                    "resetToPendingWater:order-2",
                    "markDispatchStarted:order-3",
                    "markDispatchResult:order-4:true:ok",
                    "markCancelled:order-5",
                    "markCompleted:device-1:order-6",
                    "markPendingWater:order-7",
                ),
                gateway.calls,
            )
        }

    private fun order(
        id: String,
        status: OrderStatus = OrderStatus.PendingWater,
    ): Order =
        Order(
            id = id,
            recipeCode = "recipe-$id",
            quantity = 1,
            targetTemperature = 0,
            cookSeconds = 0,
            spiceLevel = 0,
            status = status,
        )

    private class FakeOrderLifecycleGateway : OrderLifecycleGateway {
        val calls = mutableListOf<String>()

        override suspend fun setDelayed(
            orderId: String,
            delayed: Boolean,
        ) {
            calls += "setDelayed:$orderId:$delayed"
        }

        override suspend fun resetToPendingWater(orderId: String) {
            calls += "resetToPendingWater:$orderId"
        }

        override suspend fun markDispatchStarted(orderId: String) {
            calls += "markDispatchStarted:$orderId"
        }

        override suspend fun markDispatchResult(
            orderId: String,
            success: Boolean,
            message: String,
        ) {
            calls += "markDispatchResult:$orderId:$success:$message"
        }

        override suspend fun markCancelled(orderId: String) {
            calls += "markCancelled:$orderId"
        }

        override suspend fun markCompleted(
            deviceCode: String,
            orderId: String,
        ) {
            calls += "markCompleted:$deviceCode:$orderId"
        }

        override suspend fun markPendingWater(orderId: String) {
            calls += "markPendingWater:$orderId"
        }
    }
}
