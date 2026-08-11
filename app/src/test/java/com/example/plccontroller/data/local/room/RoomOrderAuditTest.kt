package com.example.plccontroller.data.local.room

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOrderAuditTest {
    @Test
    fun auditDetectsReceiveUrgeTransferAndLifecycleChanges() {
        val original = order().copy(status = OrderStatus.PendingWater, tableCode = "10")
        val received = buildOrderAuditEvents(emptyList(), listOf(original), now = 100L)
        assertEquals("ORDER_RECEIVED", received.single().eventType)

        val changed =
            original.copy(
                status = OrderStatus.WaitingTransfer,
                tableCode = "20",
                isUrged = true,
                operation = "304",
            )
        val events = buildOrderAuditEvents(listOf(original), listOf(changed), now = 200L)

        assertTrue(events.any { it.eventType == "ORDER_WAITING_TRANSFER" })
        assertTrue(events.any { it.eventType == "ORDER_TRANSFERRED" })
        assertTrue(events.any { it.eventType == "ORDER_URGED" })
    }

    @Test
    fun legacyImportUsesDedicatedAuditType() {
        val events =
            buildOrderAuditEvents(
                before = emptyList(),
                after = listOf(order()),
                now = 100L,
                importMode = true,
            )

        assertEquals("ORDER_IMPORTED", events.single().eventType)
    }

    private fun order(): Order =
        Order(
            id = "root-1",
            recipeCode = "POT",
            quantity = 1,
            targetTemperature = 180,
            cookSeconds = 60,
            spiceLevel = 0,
            orderTime = "2026-06-19 10:00:00",
            baseHash = "hash-1",
            ikmsOrder = "bill-1",
            sourceRootId = "root-1",
            orderAliases = listOf("bill-1"),
        )
}
