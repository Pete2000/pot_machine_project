package com.example.plccontroller.data.local

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderEventReducerTest {
    private val store = FakeOrderEventStore()
    private val reducer = BlockingOrderEventReducer(OrderEventReducer(store))

    @Test
    fun orderCreatedBy301IsInsertedOnce() {
        val task = task(operation = "301", id = "order-1")

        reducer.reduce(task)
        reducer.reduce(task)

        assertEquals(listOf("order-1"), store.orders.map(Order::id))
    }

    @Test
    fun urgedOrderMarksExistingOrderWithoutInsertingDuplicate() {
        store.orders +=
            order(
                id = "order-1",
                baseHash = "base-1",
                aliases = listOf("alias-1"),
            )

        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "base-1",
                aliases = listOf("alias-1"),
            ),
        )

        assertEquals(1, store.orders.size)
        assertEquals("301", store.orders.single().operation)
        assertTrue(store.orders.single().isUrged)
    }

    @Test
    fun unmatchedUrgeProducesOperatorWarningWithoutCreatingOrder() {
        val result = reducer.reduce(task(operation = "302", baseHash = "missing-base"))

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("停止自动催单"))
        assertTrue(store.orders.isEmpty())
    }

    @Test
    fun transferredOrderThenUrgedOrderStillTargetsSameLocalOrder() {
        store.orders +=
            order(
                id = "order-1",
                baseHash = "old-base",
                tableCode = "11",
                ikmsOrder = "old-order",
                aliases = listOf("old-order"),
                recipeCode = "SPICY",
            )

        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-1",
                baseHash = "new-base",
                previousBaseHash = "old-base",
                previousOrderAlias = "old-order",
                targetTableCode = "22",
                aliases = listOf("new-order"),
                recipeCode = "SPICY",
            ),
        )
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-after-transfer",
                baseHash = "new-base",
                aliases = listOf("new-order"),
                recipeCode = "SPICY",
            ),
        )

        assertEquals(1, store.orders.size)
        val updated = store.orders.single()
        assertEquals("22", updated.tableCode)
        assertEquals("304", updated.operation)
        assertTrue(updated.isUrged)
        assertTrue(updated.orderAliases.contains("new-order"))
    }

    @Test
    fun cancelRunningOrderProducesManualWarning() {
        store.orders +=
            order(
                id = "order-1",
                baseHash = "base-1",
                status = OrderStatus.WaitingTransfer,
            )

        val result = reducer.reduce(task(operation = "303", baseHash = "base-1"))

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("需要人工处理"))
        assertEquals(OrderStatus.WaitingTransfer, store.orders.single().status)
    }

    @Test
    fun unmatchedCancellationProducesOperatorWarningInsteadOfAliasFallback() {
        val result = reducer.reduce(task(operation = "303", baseHash = "missing-base"))

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("停止自动退单"))
        assertTrue(store.orders.isEmpty())
    }

    @Test
    fun transferredOrderThenCancelledOrderCancelsOriginalOrderCorrectly() {
        store.orders +=
            order(
                id = "order-1",
                baseHash = "old-base",
                tableCode = "11",
                ikmsOrder = "old-order",
                aliases = listOf("old-order"),
                recipeCode = "SPICY",
            )

        // 1. Transfer the order, introducing new-order alias
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-1",
                baseHash = "new-base",
                previousBaseHash = "old-base",
                previousOrderAlias = "old-order",
                targetTableCode = "22",
                aliases = listOf("new-order"),
                recipeCode = "SPICY",
            ),
        )

        // 2. Cancel using the new-base and new-order alias
        val result =
            reducer.reduce(
                task(
                    operation = "303",
                    id = "cancel-1",
                    baseHash = "new-base",
                    aliases = listOf("new-order"),
                    recipeCode = "SPICY",
                ),
            )

        assertEquals(0, result.warnings.size)
        assertEquals(1, store.orders.size)
        assertEquals(OrderStatus.Cancelled, store.orders.single().status)
    }

    @Test
    fun historicalCancellationEventDoesNotCancelNewerOrderSharingSameBaseHash() {
        store.orders +=
            order(
                id = "new-order-id",
                baseHash = "shared-base-hash",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-17 20:07:46",
            )

        val result =
            reducer.reduce(
                task(
                    operation = "303",
                    id = "historical-cancel",
                    baseHash = "shared-base-hash",
                    orderTime = "2026-06-17 19:54:17",
                ),
            )

        assertEquals(1, store.orders.size)
        assertEquals(OrderStatus.PendingWater, store.orders.single().status)
    }

    @Test
    fun newerCancellationEventCancelsOlderOrderSharingSameBaseHash() {
        store.orders +=
            order(
                id = "old-order-id",
                baseHash = "shared-base-hash",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-17 19:53:11",
            )

        val result =
            reducer.reduce(
                task(
                    operation = "303",
                    id = "newer-cancel",
                    baseHash = "shared-base-hash",
                    orderTime = "2026-06-17 19:54:17",
                ),
            )

        assertEquals(1, store.orders.size)
        assertEquals(OrderStatus.Cancelled, store.orders.single().status)
    }

    @Test
    fun historicalTransferEventDoesNotAffectNewerOrderSharingSameBaseHash() {
        store.orders +=
            order(
                id = "new-order-id",
                baseHash = "shared-base-hash",
                tableCode = "30",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-17 20:07:46",
            )

        reducer.reduce(
            task(
                operation = "304",
                id = "historical-transfer",
                baseHash = "shared-base-hash",
                previousBaseHash = "shared-base-hash",
                targetTableCode = "40",
                orderTime = "2026-06-17 19:54:17",
            ),
        )

        assertEquals("30", store.orders.single().tableCode)
    }

    @Test
    fun urgedOrderCanBeTransferredByNewerOperateTime() {
        // 1. Insert an order with orderTime = 15:01:21
        store.orders +=
            order(
                id = "order-1",
                baseHash = "old-base",
                tableCode = "28",
                ikmsOrder = "old-order",
                aliases = listOf("old-order"),
                recipeCode = "SPICY",
                orderTime = "2026-06-18 15:01:21",
            )

        // 2. Urge: simulates update to 15:01:59
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "old-base",
                aliases = listOf("old-order"),
                recipeCode = "SPICY",
                orderTime = "2026-06-18 15:01:59",
            ),
        )
        assertEquals("301", store.orders.single().operation)
        assertTrue(store.orders.single().isUrged)

        // 3. Transfer uses its own operateTime.
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-1",
                baseHash = "new-base",
                previousBaseHash = "old-base",
                previousOrderAlias = "old-order",
                targetTableCode = "16",
                aliases = listOf("new-order"),
                recipeCode = "SPICY",
                orderTime = "2026-06-18 15:02:21",
            ),
        )

        assertEquals(1, store.orders.size)
        assertEquals("16", store.orders.single().tableCode)
    }

    @Test
    fun historicalCancellationEventDoesNotCancelRewateredOrder() {
        store.orders +=
            order(
                id = "rewater-order-id",
                baseHash = "shared-base-hash",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-17 19:53:11",
            )

        reducer.reduce(
            task(
                operation = "303",
                id = "cancel-task",
                baseHash = "shared-base-hash",
                orderTime = "2026-06-17 19:54:17",
            ),
        )
        assertEquals(OrderStatus.Cancelled, store.orders.single().status)

        val rewateredOrder =
            store.orders.single().copy(
                status = OrderStatus.PendingWater,
                statusUpdatedAt = "2026-06-17 20:00:00",
            )
        store.orders.clear()
        store.orders += rewateredOrder

        reducer.reduce(
            task(
                operation = "303",
                id = "cancel-task",
                baseHash = "shared-base-hash",
                orderTime = "2026-06-17 19:54:17",
            ),
        )

        assertEquals(OrderStatus.PendingWater, store.orders.single().status)
    }

    @Test
    fun singleCancellationEventOnlyCancelsOneOfMultipleOrdersAndIsIdempotent() {
        // Two active identical orders on table 28 sharing the same base hash
        store.orders +=
            order(
                id = "order-a",
                baseHash = "shared-base-hash",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "shared-base-hash",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // Reduce cancellation event with a specific sourceRootId
        reducer.reduce(
            task(
                operation = "303",
                id = "refund-event-1",
                baseHash = "shared-base-hash",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "refund-event-1",
            ),
        )

        // Verify that only one order is cancelled and the other remains active
        val cancelled = store.orders.filter { it.status == OrderStatus.Cancelled }
        val active = store.orders.filter { it.status == OrderStatus.PendingWater }
        assertEquals(1, cancelled.size)
        assertEquals(1, active.size)
        assertTrue(cancelled.single().orderAliases.contains("refund-event-1"))

        // Run the SAME cancellation event again to simulate polling
        reducer.reduce(
            task(
                operation = "303",
                id = "refund-event-1",
                baseHash = "shared-base-hash",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "refund-event-1",
            ),
        )

        // Verify that it is idempotent: the active order is NOT cancelled
        val cancelledAfterSecondPoll = store.orders.filter { it.status == OrderStatus.Cancelled }
        val activeAfterSecondPoll = store.orders.filter { it.status == OrderStatus.PendingWater }
        assertEquals(1, cancelledAfterSecondPoll.size)
        assertEquals(1, activeAfterSecondPoll.size)
    }

    @Test
    fun singleTransferEventOnlyTransfersOneOfMultipleOrdersAndIsIdempotent() {
        // Two active identical orders on table 28 sharing the same base hash
        store.orders +=
            order(
                id = "order-a",
                baseHash = "shared-base-hash",
                tableCode = "28",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "shared-base-hash",
                tableCode = "28",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // Reduce table transfer event with a specific sourceRootId / transferEventId
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-event-1",
                baseHash = "new-base-hash",
                previousBaseHash = "shared-base-hash",
                targetTableCode = "29",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "transfer-event-1",
            ),
        )

        // Verify that only one order is transferred to table 29 and the other remains on 28
        val transferred = store.orders.filter { it.tableCode == "29" }
        val remaining = store.orders.filter { it.tableCode == "28" }
        assertEquals(1, transferred.size)
        assertEquals(1, remaining.size)
        assertTrue(transferred.single().orderAliases.contains("transfer-event-1"))

        // Run the SAME transfer event again to simulate polling
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-event-1",
                baseHash = "new-base-hash",
                previousBaseHash = "shared-base-hash",
                targetTableCode = "29",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "transfer-event-1",
            ),
        )

        // Verify that it is idempotent: the remaining order is NOT transferred to 29
        val transferredAfterSecondPoll = store.orders.filter { it.tableCode == "29" }
        val remainingAfterSecondPoll = store.orders.filter { it.tableCode == "28" }
        assertEquals(1, transferredAfterSecondPoll.size)
        assertEquals(1, remainingAfterSecondPoll.size)
    }

    @Test
    fun singleTransferByAliasEventOnlyTransfersOneOfMultipleOrdersAndIsIdempotent() {
        // Two active identical orders on table 28 sharing the same alias/ikmsOrder
        store.orders +=
            order(
                id = "order-a",
                baseHash = "shared-base-hash",
                tableCode = "28",
                status = OrderStatus.PendingWater,
                ikmsOrder = "shared-alias",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "shared-base-hash",
                tableCode = "28",
                status = OrderStatus.PendingWater,
                ikmsOrder = "shared-alias",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // Reduce table transfer event by alias (previousBaseHash is null or doesn't match, so it falls back to alias)
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-event-2",
                baseHash = "new-base-hash",
                previousBaseHash = "wrong-base-hash", // force fallback to alias
                previousOrderAlias = "shared-alias",
                recipeCode = "RECIPE-A",
                targetTableCode = "30",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "transfer-event-2",
            ),
        )

        // Verify that only one order is transferred to table 30 and the other remains on 28
        val transferred = store.orders.filter { it.tableCode == "30" }
        val remaining = store.orders.filter { it.tableCode == "28" }
        assertEquals(1, transferred.size)
        assertEquals(1, remaining.size)
        assertTrue(transferred.single().orderAliases.contains("transfer-event-2"))

        // Run the SAME transfer event again to simulate polling
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-event-2",
                baseHash = "new-base-hash",
                previousBaseHash = "wrong-base-hash",
                previousOrderAlias = "shared-alias",
                recipeCode = "RECIPE-A",
                targetTableCode = "30",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "transfer-event-2",
            ),
        )

        // Verify that it is idempotent: the remaining order is NOT transferred to 30
        val transferredAfterSecondPoll = store.orders.filter { it.tableCode == "30" }
        val remainingAfterSecondPoll = store.orders.filter { it.tableCode == "28" }
        assertEquals(1, transferredAfterSecondPoll.size)
        assertEquals(1, remainingAfterSecondPoll.size)
    }

    @Test
    fun consecutiveTransfersOfIdenticalPotsOnSameTable() {
        // 1. Two active identical orders on table 85 sharing the same base hash
        store.orders +=
            order(
                id = "order-a",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // 2. Transfer the first pot from 85 -> 80
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-85-to-80",
                baseHash = "base-80",
                previousBaseHash = "base-85",
                targetTableCode = "80",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "transfer-85-to-80",
            ),
        )

        // Verify that one pot went to 80, and one remains on 85
        val at80AfterFirst = store.orders.filter { it.tableCode == "80" }
        val at85AfterFirst = store.orders.filter { it.tableCode == "85" }
        assertEquals(1, at80AfterFirst.size)
        assertEquals(1, at85AfterFirst.size)
        assertEquals("order-a", at80AfterFirst.single().id) // first match is order-a
        assertEquals("order-b", at85AfterFirst.single().id) // remaining is order-b

        // 3. Transfer the second pot from 85 -> 90
        reducer.reduce(
            task(
                operation = "304",
                id = "transfer-85-to-90",
                baseHash = "base-90",
                previousBaseHash = "base-85",
                targetTableCode = "90",
                orderTime = "2026-06-18 16:02:00",
                sourceRootId = "transfer-85-to-90",
            ),
        )

        // Verify that order-a remains on 80, and order-b went to 90. Neither is left on 85!
        val at80Final = store.orders.filter { it.tableCode == "80" }
        val at90Final = store.orders.filter { it.tableCode == "90" }
        val at85Final = store.orders.filter { it.tableCode == "85" }
        assertEquals(1, at80Final.size)
        assertEquals(1, at90Final.size)
        assertEquals(0, at85Final.size)
        assertEquals("order-a", at80Final.single().id)
        assertEquals("order-b", at90Final.single().id)
    }

    @Test
    fun consecutiveUrgesOfIdenticalPotsOnSameTable() {
        // 1. Two active identical orders on table 85 sharing the same base hash
        store.orders +=
            order(
                id = "order-a",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // Verify neither is urged initially
        assertTrue(store.orders.none { it.isUrged })

        // 2. First urge click (represented by urgeEventId = urge-1)
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "base-85",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "urge-1",
            ),
        )

        // Verify only one order is urged (order-a, since it's matched first)
        val urgedAfterFirst = store.orders.filter { it.isUrged }
        val activeAfterFirst = store.orders.filter { !it.isUrged }
        assertEquals(1, urgedAfterFirst.size)
        assertEquals(1, activeAfterFirst.size)
        assertEquals("order-a", urgedAfterFirst.single().id)
        assertEquals("order-b", activeAfterFirst.single().id)
        assertTrue(urgedAfterFirst.single().orderAliases.contains("urge-1"))

        // 3. Second urge click (represented by urgeEventId = urge-2)
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-2",
                baseHash = "base-85",
                orderTime = "2026-06-18 16:02:00",
                sourceRootId = "urge-2",
            ),
        )

        // Verify both orders are now urged
        val urgedAfterSecond = store.orders.filter { it.isUrged }
        assertEquals(2, urgedAfterSecond.size)
        assertTrue(
            store.orders
                .first { it.id == "order-a" }
                .orderAliases
                .contains("urge-1"),
        )
        assertTrue(
            store.orders
                .first { it.id == "order-b" }
                .orderAliases
                .contains("urge-2"),
        )

        // 4. Test idempotency: Resend first urge event again (e.g. simulated retry/polling)
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "base-85",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "urge-1",
            ),
        )

        // Verify state is unchanged and matches expected aliases
        assertEquals(2, store.orders.filter { it.isUrged }.size)
        assertTrue(
            store.orders
                .first { it.id == "order-a" }
                .orderAliases
                .contains("urge-1"),
        )
        assertTrue(
            store.orders
                .first { it.id == "order-b" }
                .orderAliases
                .contains("urge-2"),
        )
    }

    @Test
    fun consecutiveUrgesOfIdenticalPotsByAlias() {
        // 1. Two active identical orders on table 85 sharing the same alias
        store.orders +=
            order(
                id = "order-a",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                ikmsOrder = "shared-alias",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-a",
            )
        store.orders +=
            order(
                id = "order-b",
                baseHash = "base-85",
                tableCode = "85",
                status = OrderStatus.PendingWater,
                ikmsOrder = "shared-alias",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:00:00",
                sourceRootId = "order-b",
            )

        // 2. First urge click (represented by urgeEventId = urge-1) by alias.
        // In reduceUrged:
        // val updatedCount = eventStore.markUrgedByBaseHash(task.baseHash, urgeEventId, task.order.orderTime)
        // Since we supply a baseHash that doesn't match ("wrong-base"), markUrgedByBaseHash will return 0.
        // It will fallback to markUrgedByOrderAliases.
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "wrong-base",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "urge-1",
            ),
        )

        // Verify only one order is urged (order-a, since it's matched first)
        val urgedAfterFirst = store.orders.filter { it.isUrged }
        val activeAfterFirst = store.orders.filter { !it.isUrged }
        assertEquals(1, urgedAfterFirst.size)
        assertEquals(1, activeAfterFirst.size)
        assertEquals("order-a", urgedAfterFirst.single().id)
        assertEquals("order-b", activeAfterFirst.single().id)
        assertTrue(urgedAfterFirst.single().orderAliases.contains("urge-1"))

        // 3. Second urge click (represented by urgeEventId = urge-2)
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-2",
                baseHash = "wrong-base",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:02:00",
                sourceRootId = "urge-2",
            ),
        )

        // Verify both orders are now urged
        val urgedAfterSecond = store.orders.filter { it.isUrged }
        assertEquals(2, urgedAfterSecond.size)
        assertTrue(
            store.orders
                .first { it.id == "order-a" }
                .orderAliases
                .contains("urge-1"),
        )
        assertTrue(
            store.orders
                .first { it.id == "order-b" }
                .orderAliases
                .contains("urge-2"),
        )

        // 4. Test idempotency: Resend first urge event again
        reducer.reduce(
            task(
                operation = "302",
                id = "urge-1",
                baseHash = "wrong-base",
                aliases = listOf("shared-alias"),
                recipeCode = "RECIPE-A",
                orderTime = "2026-06-18 16:01:00",
                sourceRootId = "urge-1",
            ),
        )

        // Verify state is unchanged
        assertEquals(2, store.orders.filter { it.isUrged }.size)
        assertTrue(
            store.orders
                .first { it.id == "order-a" }
                .orderAliases
                .contains("urge-1"),
        )
        assertTrue(
            store.orders
                .first { it.id == "order-b" }
                .orderAliases
                .contains("urge-2"),
        )
    }

    private fun task(
        operation: String,
        id: String = "$operation-task",
        baseHash: String = "base-$id",
        previousBaseHash: String? = null,
        previousOrderAlias: String? = null,
        targetTableCode: String? = null,
        aliases: List<String> = listOf(id),
        orderTime: String? = null,
        recipeCode: String? = null,
        sourceRootId: String? = null,
    ): PosOrderTask =
        PosOrderTask(
            operation = operation,
            baseHash = baseHash,
            order =
                order(
                    id = id,
                    baseHash = baseHash,
                    tableCode = targetTableCode,
                    aliases = aliases,
                    orderTime = orderTime,
                    recipeCode = recipeCode,
                    sourceRootId = sourceRootId,
                ),
            previousOrderAlias = previousOrderAlias,
            currentOrderAliases = aliases,
            targetTableCode = targetTableCode,
            previousBaseHash = previousBaseHash,
        )

    private fun order(
        id: String,
        baseHash: String,
        tableCode: String? = null,
        status: OrderStatus = OrderStatus.PendingWater,
        ikmsOrder: String? = id,
        aliases: List<String> = listOf(id),
        orderTime: String? = null,
        recipeCode: String? = null,
        sourceRootId: String? = null,
    ): Order =
        Order(
            id = id,
            recipeCode = recipeCode ?: "recipe-$id",
            quantity = 1,
            targetTemperature = 0,
            cookSeconds = 0,
            spiceLevel = 0,
            tableCode = tableCode,
            status = status,
            baseHash = baseHash,
            ikmsOrder = ikmsOrder,
            operation = "301",
            orderAliases = aliases,
            orderTime = orderTime,
            sourceRootId = sourceRootId ?: id,
        )

    private class FakeOrderEventStore : OrderEventStore {
        val orders = mutableListOf<Order>()

        override suspend fun insertIgnore(task: PosOrderTask): Boolean {
            if (orders.any { it.id == task.order.id }) {
                return false
            }
            orders += task.order
            return true
        }

        private fun isChronologicallyValid(
            order: Order,
            eventTime: String?,
            isCancelEvent: Boolean = false,
        ): Boolean {
            if (eventTime.isNullOrBlank()) return true
            val refTime = order.orderTime.orEmpty().trim()
            if (refTime.isBlank()) return true

            val timeValid = refTime <= eventTime.trim()
            if (!timeValid) return false

            if (isCancelEvent) {
                val statusUpdate = order.statusUpdatedAt.orEmpty().trim()
                if (statusUpdate.isNotBlank() && statusUpdate > refTime) {
                    return statusUpdate <= eventTime.trim()
                }
            }
            return true
        }

        override suspend fun markUrgedByBaseHash(
            baseHash: String,
            urgeEventId: String?,
            eventTime: String?,
        ): Int {
            if (!urgeEventId.isNullOrBlank()) {
                val alreadyUrged =
                    orders.count { order ->
                        order.isUrged && (order.sourceRootId == urgeEventId || order.orderAliases.contains(urgeEventId))
                    }
                if (alreadyUrged > 0) {
                    return alreadyUrged
                }
            }

            val matched =
                orders.filter { order ->
                    order.baseHash == baseHash &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime, isCancelEvent = false)
                }
            if (matched.isEmpty()) return 0

            val toUrge =
                if (!urgeEventId.isNullOrBlank()) {
                    (matched.firstOrNull { !it.isUrged } ?: matched.firstOrNull())?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            var count = 0
            orders.replaceAll { order ->
                if (order in toUrge) {
                    count += 1
                    order.copy(
                        isUrged = true,
                        orderAliases = (order.orderAliases + listOfNotNull(urgeEventId)).distinct(),
                    )
                } else {
                    order
                }
            }
            return count
        }

        override suspend fun markUrgedByOrderAliases(
            orderAliases: List<String>,
            recipeCode: String,
            urgeEventId: String?,
            eventTime: String?,
        ): Int {
            val aliases = orderAliases.map(String::trim).filter(String::isNotBlank)
            if (aliases.isEmpty()) return 0

            if (!urgeEventId.isNullOrBlank()) {
                val alreadyUrged =
                    orders.count { order ->
                        order.isUrged && (order.sourceRootId == urgeEventId || order.orderAliases.contains(urgeEventId))
                    }
                if (alreadyUrged > 0) {
                    return alreadyUrged
                }
            }

            val matched =
                orders.filter { order ->
                    val knownAliases =
                        (order.orderAliases + listOfNotNull(order.ikmsOrder))
                            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    aliases.any { it in knownAliases } &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime, isCancelEvent = false)
                }
            if (matched.isEmpty()) return 0

            val toUrge =
                if (!urgeEventId.isNullOrBlank()) {
                    (matched.firstOrNull { !it.isUrged } ?: matched.firstOrNull())?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            var count = 0
            orders.replaceAll { order ->
                if (order in toUrge) {
                    count += 1
                    order.copy(
                        isUrged = true,
                        orderAliases = (order.orderAliases + listOfNotNull(urgeEventId)).distinct(),
                    )
                } else {
                    order
                }
            }
            return count
        }

        override suspend fun cancelPendingByBaseHash(
            baseHash: String,
            cancelEventId: String?,
            eventTime: String?,
        ): CancelResult {
            if (!cancelEventId.isNullOrBlank()) {
                val alreadyCancelled =
                    orders.any { order ->
                        order.status == OrderStatus.Cancelled &&
                            (order.sourceRootId == cancelEventId || order.orderAliases.contains(cancelEventId))
                    }
                if (alreadyCancelled) {
                    return CancelResult.Canceled
                }
            }

            val matched = orders.filter { it.baseHash == baseHash && isChronologicallyValid(it, eventTime, isCancelEvent = true) }
            if (matched.isEmpty()) return CancelResult.NotFound

            val toCancel =
                if (!cancelEventId.isNullOrBlank()) {
                    matched
                        .firstOrNull { order ->
                            order.status == OrderStatus.Pending ||
                                order.status == OrderStatus.PendingWater ||
                                order.status == OrderStatus.Delayed
                        }?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            if (toCancel.isEmpty()) {
                return CancelResult.RequiresManualIntervention
            }

            var changed = false
            var blocked = false
            orders.replaceAll { order ->
                if (order !in toCancel) {
                    order
                } else if (order.status == OrderStatus.Pending ||
                    order.status == OrderStatus.PendingWater ||
                    order.status == OrderStatus.Delayed
                ) {
                    changed = true
                    val newAliases =
                        if (!cancelEventId.isNullOrBlank() && !order.orderAliases.contains(cancelEventId)) {
                            order.orderAliases + cancelEventId
                        } else {
                            order.orderAliases
                        }
                    order.copy(status = OrderStatus.Cancelled, orderAliases = newAliases)
                } else {
                    blocked = true
                    order
                }
            }
            return when {
                blocked -> CancelResult.RequiresManualIntervention
                changed -> CancelResult.Canceled
                else -> CancelResult.NotFound
            }
        }

        override suspend fun cancelPendingByOrderAliases(
            orderAliases: List<String>,
            recipeCode: String,
            cancelEventId: String?,
            eventTime: String?,
        ): CancelResult {
            val aliases = orderAliases.map(String::trim).filter(String::isNotBlank)
            if (aliases.isEmpty()) return CancelResult.NotFound

            if (!cancelEventId.isNullOrBlank()) {
                val alreadyCancelled =
                    orders.any { order ->
                        order.status == OrderStatus.Cancelled &&
                            (order.sourceRootId == cancelEventId || order.orderAliases.contains(cancelEventId))
                    }
                if (alreadyCancelled) {
                    return CancelResult.Canceled
                }
            }

            val matched =
                orders.filter { order ->
                    val knownAliases =
                        (order.orderAliases + listOfNotNull(order.ikmsOrder))
                            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    aliases.any { it in knownAliases } &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        isChronologicallyValid(order, eventTime, isCancelEvent = true)
                }
            if (matched.isEmpty()) return CancelResult.NotFound

            val toCancel =
                if (!cancelEventId.isNullOrBlank()) {
                    matched
                        .firstOrNull { order ->
                            order.status == OrderStatus.Pending ||
                                order.status == OrderStatus.PendingWater ||
                                order.status == OrderStatus.Delayed
                        }?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            if (toCancel.isEmpty()) {
                return CancelResult.RequiresManualIntervention
            }

            var changed = false
            var blocked = false
            orders.replaceAll { order ->
                if (order !in toCancel) {
                    order
                } else if (order.status == OrderStatus.Pending ||
                    order.status == OrderStatus.PendingWater ||
                    order.status == OrderStatus.Delayed
                ) {
                    changed = true
                    val newAliases =
                        if (!cancelEventId.isNullOrBlank() && !order.orderAliases.contains(cancelEventId)) {
                            order.orderAliases + cancelEventId
                        } else {
                            order.orderAliases
                        }
                    order.copy(status = OrderStatus.Cancelled, orderAliases = newAliases)
                } else {
                    blocked = true
                    order
                }
            }
            return when {
                blocked -> CancelResult.RequiresManualIntervention
                changed -> CancelResult.Canceled
                else -> CancelResult.NotFound
            }
        }

        override suspend fun updateTableByBaseHash(
            baseHash: String,
            newBaseHash: String?,
            transferEventId: String?,
            newTableCode: String,
            newOrderAliases: List<String>,
            eventTime: String?,
        ): Int {
            // 1. Idempotency check: if transferEventId is already processed, skip
            if (!transferEventId.isNullOrBlank()) {
                val alreadyTransferred =
                    orders.count { order ->
                        order.orderAliases.contains(transferEventId) || order.sourceRootId == transferEventId
                    }
                if (alreadyTransferred > 0) {
                    return alreadyTransferred
                }
            }

            // 2. Find matching active orders (non-terminal statuses)
            val matched =
                orders.filter { order ->
                    order.baseHash == baseHash &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime, isCancelEvent = false)
                }

            if (matched.isEmpty()) {
                return 0
            }

            // 3. Single targeting vs batch default
            val toTransfer =
                if (!transferEventId.isNullOrBlank()) {
                    matched.firstOrNull()?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            var count = 0
            orders.replaceAll { order ->
                if (order in toTransfer) {
                    count += 1
                    order.copy(
                        baseHash = newBaseHash ?: order.baseHash,
                        tableCode = newTableCode,
                        ikmsOrder = newOrderAliases.firstOrNull() ?: order.ikmsOrder,
                        operation = "304",
                        statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                        orderAliases = (order.orderAliases + newOrderAliases + listOfNotNull(transferEventId)).distinct(),
                    )
                } else {
                    order
                }
            }
            return count
        }

        override suspend fun updateTableByOrderAlias(
            previousOrderAlias: String,
            recipeCode: String,
            newBaseHash: String?,
            transferEventId: String?,
            newTableCode: String,
            newOrderAliases: List<String>,
            eventTime: String?,
        ): Int {
            val alias = previousOrderAlias.trim()
            if (alias.isBlank() || newTableCode.isBlank()) {
                return 0
            }

            // 1. Idempotency check: if transferEventId is already processed, skip
            if (!transferEventId.isNullOrBlank()) {
                val alreadyTransferred =
                    orders.count { order ->
                        order.orderAliases.contains(transferEventId) || order.sourceRootId == transferEventId
                    }
                if (alreadyTransferred > 0) {
                    return alreadyTransferred
                }
            }

            // 2. Find matching active orders
            val matched =
                orders.filter { order ->
                    order.ikmsOrder == alias &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime, isCancelEvent = false)
                }

            if (matched.isEmpty()) {
                return 0
            }

            // 3. Single targeting vs batch default
            val toTransfer =
                if (!transferEventId.isNullOrBlank()) {
                    matched.firstOrNull()?.let { listOf(it) } ?: emptyList()
                } else {
                    matched
                }

            var count = 0
            orders.replaceAll { order ->
                if (order in toTransfer) {
                    count += 1
                    order.copy(
                        baseHash = newBaseHash ?: order.baseHash,
                        tableCode = newTableCode,
                        ikmsOrder = newOrderAliases.firstOrNull() ?: order.ikmsOrder,
                        operation = "304",
                        statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                        orderAliases =
                            (
                                order.orderAliases + previousOrderAlias + newOrderAliases +
                                    listOfNotNull(
                                        transferEventId,
                                    )
                            ).distinct(),
                    )
                } else {
                    order
                }
            }
            return count
        }
    }

    private class BlockingOrderEventReducer(
        private val delegate: OrderEventReducer,
    ) {
        fun reduce(task: PosOrderTask): OrderEventReductionResult =
            kotlinx.coroutines.runBlocking {
                delegate.reduce(task)
            }
    }
}
