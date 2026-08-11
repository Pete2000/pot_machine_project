package com.example.plccontroller.data.local

interface OrderEventStore {
    suspend fun insertIgnore(task: PosOrderTask): Boolean

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

    suspend fun cancelPendingByBaseHash(
        baseHash: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult

    suspend fun cancelPendingByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult

    suspend fun updateTableByBaseHash(
        baseHash: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int

    suspend fun updateTableByOrderAlias(
        previousOrderAlias: String,
        recipeCode: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int
}

class LocalOrderEventStore(
    private val localOrderStore: OrderPersistenceStore,
) : OrderEventStore {
    override suspend fun insertIgnore(task: PosOrderTask): Boolean = localOrderStore.insertIgnore(task.order)

    override suspend fun markUrgedByBaseHash(
        baseHash: String,
        urgeEventId: String?,
        eventTime: String?,
    ): Int = localOrderStore.markUrgedByBaseHash(baseHash, urgeEventId, eventTime)

    override suspend fun markUrgedByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        urgeEventId: String?,
        eventTime: String?,
    ): Int = localOrderStore.markUrgedByOrderAliases(orderAliases, recipeCode, urgeEventId, eventTime)

    override suspend fun cancelPendingByBaseHash(
        baseHash: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult = localOrderStore.cancelPendingByBaseHash(baseHash, cancelEventId, eventTime)

    override suspend fun cancelPendingByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult = localOrderStore.cancelPendingByOrderAliases(orderAliases, recipeCode, cancelEventId, eventTime)

    override suspend fun updateTableByBaseHash(
        baseHash: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int =
        localOrderStore.updateTableByBaseHash(
            baseHash = baseHash,
            newBaseHash = newBaseHash,
            transferEventId = transferEventId,
            newTableCode = newTableCode,
            newOrderAliases = newOrderAliases,
            eventTime = eventTime,
        )

    override suspend fun updateTableByOrderAlias(
        previousOrderAlias: String,
        recipeCode: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int =
        localOrderStore.updateTableByOrderAlias(
            previousOrderAlias = previousOrderAlias,
            recipeCode = recipeCode,
            newBaseHash = newBaseHash,
            transferEventId = transferEventId,
            newTableCode = newTableCode,
            newOrderAliases = newOrderAliases,
            eventTime = eventTime,
        )
}

data class OrderEventReductionResult(
    val warnings: List<String> = emptyList(),
)

class OrderEventReducer(
    private val eventStore: OrderEventStore,
) {
    suspend fun reduce(task: PosOrderTask): OrderEventReductionResult =
        when (task.operation) {
            "301" -> {
                eventStore.insertIgnore(task)
                OrderEventReductionResult()
            }
            "302" -> reduceUrged(task)
            "303" -> reduceCancelled(task)
            "304" -> reduceTransferred(task)
            else -> {
                eventStore.insertIgnore(task)
                OrderEventReductionResult()
            }
        }

    private suspend fun reduceUrged(task: PosOrderTask): OrderEventReductionResult {
        val urgeEventId = task.order.sourceRootId ?: task.order.id
        val updatedCount =
            eventStore
                .markUrgedByBaseHash(task.baseHash, urgeEventId, task.order.orderTime)
                .takeIf { it > 0 }
                ?: eventStore.markUrgedByOrderAliases(task.currentOrderAliases, task.order.recipeCode, urgeEventId, task.order.orderTime)
        return if (updatedCount == 0) {
            OrderEventReductionResult(
                warnings =
                    listOf(
                        "催单未匹配到待处理订单，已停止自动催单：${task.order.tableCode ?: task.order.id}",
                    ),
            )
        } else {
            OrderEventReductionResult()
        }
    }

    private suspend fun reduceCancelled(task: PosOrderTask): OrderEventReductionResult {
        val cancelEventId = task.order.sourceRootId ?: task.order.id
        val result = eventStore.cancelPendingByBaseHash(task.baseHash, cancelEventId, task.order.orderTime)
        val warnings =
            when (result) {
                CancelResult.RequiresManualIntervention ->
                    listOf(
                        "退单已进入制作或完成，需要人工处理：${task.order.tableCode ?: task.order.id}",
                    )
                CancelResult.NotFound ->
                    listOf(
                        "退单未能唯一匹配，已停止自动退单：${task.order.tableCode ?: task.order.id}",
                    )
                CancelResult.Canceled -> emptyList()
            }
        return OrderEventReductionResult(warnings = warnings)
    }

    private suspend fun reduceTransferred(task: PosOrderTask): OrderEventReductionResult {
        val targetTableCode = task.targetTableCode.orEmpty()
        val transferEventId = task.order.sourceRootId ?: task.order.id
        var updatedCount =
            eventStore.updateTableByBaseHash(
                baseHash = task.previousBaseHash ?: task.baseHash,
                newBaseHash = task.baseHash,
                transferEventId = transferEventId,
                newTableCode = targetTableCode.ifBlank { task.order.tableCode.orEmpty() },
                newOrderAliases = task.currentOrderAliases,
                eventTime = task.order.orderTime,
            )
        if (updatedCount == 0) {
            updatedCount = task.previousOrderAlias?.let { previousOrderAlias ->
                eventStore.updateTableByOrderAlias(
                    previousOrderAlias = previousOrderAlias,
                    recipeCode = task.order.recipeCode,
                    newBaseHash = task.baseHash,
                    transferEventId = transferEventId,
                    newTableCode = targetTableCode,
                    newOrderAliases = task.currentOrderAliases,
                    eventTime = task.order.orderTime,
                )
            } ?: 0
        }
        return OrderEventReductionResult()
    }
}
