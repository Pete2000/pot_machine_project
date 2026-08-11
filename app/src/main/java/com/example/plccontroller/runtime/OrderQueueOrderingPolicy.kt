package com.example.plccontroller.runtime

import com.example.plccontroller.domain.Order

object OrderQueueOrderingPolicy {
    fun pendingWater(
        orders: List<Order>,
        pinnedOrderIds: Set<String> = emptySet(),
    ): List<Order> =
        orders.sortedWith(
            compareBy<Order> { it.status != com.example.plccontroller.domain.OrderStatus.Dispatching }
                .thenBy { it.id !in pinnedOrderIds }
                .thenBy { it.orderTime.ascendingSortKey() }
                .thenBy { it.id },
        )

    fun delayed(orders: List<Order>): List<Order> = pendingWater(orders)

    fun waitingTransfer(
        orders: List<Order>,
        mode: TransferDisplayMode = TransferDisplayMode.OldestFirst,
    ): List<Order> {
        val oldestFirst =
            orders.sortedWith(
                compareBy<Order> { it.waitingTransferTime().ascendingSortKey() }
                    .thenBy { it.id },
            )
        return when (mode) {
            TransferDisplayMode.OldestFirst -> oldestFirst
            TransferDisplayMode.LatestFirst -> oldestFirst.asReversed()
        }
    }

    fun completedHistory(orders: List<Order>): List<Order> =
        orders.sortedWith(
            compareByDescending<Order> { it.completedTime().descendingSortKey() }
                .thenByDescending { it.id },
        )

    fun cancelledHistory(orders: List<Order>): List<Order> =
        orders.sortedWith(
            compareByDescending<Order> { it.cancelledTime().descendingSortKey() }
                .thenByDescending { it.id },
        )

    private fun Order.waitingTransferTime(): String? = waterCompletedAt ?: statusUpdatedAt ?: orderTime

    private fun Order.completedTime(): String? = transferCompletedAt ?: statusUpdatedAt ?: orderTime

    private fun Order.cancelledTime(): String? = cancelledAt ?: statusUpdatedAt ?: orderTime

    private fun String?.ascendingSortKey(): String =
        this?.trim().takeUnless { it.isNullOrBlank() }
            ?: MISSING_ASCENDING_SORT_KEY

    private fun String?.descendingSortKey(): String = this?.trim().orEmpty()

    private const val MISSING_ASCENDING_SORT_KEY = "9999-12-31 23:59:59"
}
