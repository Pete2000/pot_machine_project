package com.example.plccontroller.data.local

import com.example.plccontroller.domain.Order

interface OrderPersistenceStore {
    suspend fun insertIgnore(order: Order): Boolean

    suspend fun cancelPendingByBaseHash(
        baseHash: String,
        cancelEventId: String? = null,
        eventTime: String? = null,
    ): CancelResult

    suspend fun cancelPendingByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        cancelEventId: String? = null,
        eventTime: String? = null,
    ): CancelResult

    suspend fun updateTableByBaseHash(
        baseHash: String,
        newBaseHash: String? = null,
        transferEventId: String? = null,
        newTableCode: String,
        newOrderAliases: List<String> = emptyList(),
        eventTime: String? = null,
    ): Int

    suspend fun updateTableByOrderAlias(
        previousOrderAlias: String,
        recipeCode: String,
        newBaseHash: String? = null,
        transferEventId: String? = null,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String? = null,
    ): Int

    suspend fun markUrgedByBaseHash(
        baseHash: String,
        urgeEventId: String? = null,
        eventTime: String? = null,
    ): Int

    suspend fun markUrgedByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        urgeEventId: String? = null,
        eventTime: String? = null,
    ): Int

    suspend fun markDispatching(orderId: String)

    suspend fun markPendingDelivery(orderId: String)

    suspend fun markCompleted(orderId: String)

    suspend fun markCancelled(orderId: String)

    suspend fun markFailed(orderId: String)

    suspend fun markDelayed(orderId: String)

    suspend fun markPendingWater(orderId: String)

    suspend fun activeOrders(): List<Order>

    suspend fun allOrders(): List<Order>

    /** Debug/maintenance escape hatch. Removes every local order and its order audit history. */
    suspend fun clearAllOrdersForDebug()
}
