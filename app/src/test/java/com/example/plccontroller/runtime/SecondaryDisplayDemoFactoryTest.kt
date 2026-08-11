package com.example.plccontroller.runtime

import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.PotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryDisplayDemoFactoryTest {
    @Test
    fun buildWaitingTransferOrdersCreatesStableDemoTransferOrders() {
        val orders = SecondaryDisplayDemoFactory.buildWaitingTransferOrders(orderCount = 5)

        assertEquals(5, orders.size)
        assertEquals((1..5).map { "secondary-demo-$it" }, orders.map { it.id })
        assertTrue(orders.all { it.status == OrderStatus.WaitingTransfer })
        assertEquals(
            listOf(PotMode.Single, PotMode.Split, PotMode.FourGrid, PotMode.ThreeGrid, PotMode.Single),
            orders.map { it.potMode },
        )
    }

    @Test
    fun buildWaitingTransferOrdersKeepsSectionSummaryForMultiPotDemoCards() {
        val orders = SecondaryDisplayDemoFactory.buildWaitingTransferOrders(orderCount = 4)

        assertEquals(null, orders[0].slotSummary?.takeUnless { it.startsWith("整锅") })
        assertTrue(orders[1].slotSummary.orEmpty().contains("左锅"))
        assertTrue(orders[2].slotSummary.orEmpty().contains("右下"))
        assertTrue(orders[3].slotSummary.orEmpty().contains("下锅"))
    }
}
