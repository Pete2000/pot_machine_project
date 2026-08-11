package com.example.plccontroller.data.local

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOrderStoreTest {
    @Test
    fun clearAllOrdersForDebugRemovesActiveAndTerminalOrders() {
        val store = testStore()
        store.insertIgnore(order(id = "pending"))
        store.insertIgnore(order(id = "completed", status = OrderStatus.Completed))

        store.clearAllOrdersForDebug()

        assertTrue(store.allOrders().isEmpty())
        assertTrue(store.activeOrders().isEmpty())
    }

    @Test
    fun insertIgnorePersistsOrderForReloadedStore() {
        val storage = InMemoryOrderStorage()
        val codec = InMemoryOrderCodec()
        val firstStore = LocalOrderStore(storage, codec)
        val order =
            order(
                id = "order-1",
                aliases = listOf("pos-1"),
                structureStatus = OrderStructureStatus.ParseRecovered,
                missingSlotLabels = listOf("右下"),
            )

        assertTrue(firstStore.insertIgnore(order))

        val reloadedStore = LocalOrderStore(storage, codec)
        val reloaded = reloadedStore.allOrders().single()

        assertEquals("order-1", reloaded.id)
        assertEquals(listOf("pos-1"), reloaded.orderAliases)
        assertEquals(OrderStructureStatus.ParseRecovered, reloaded.structureStatus)
        assertEquals(listOf("右下"), reloaded.missingSlotLabels)
    }

    @Test
    fun delayedCancelledAndRewateredOrderTransitionsArePersisted() {
        val store = testStore()
        store.insertIgnore(order("order-1"))

        store.markDelayed("order-1")
        assertEquals(OrderStatus.Delayed, store.allOrders().single().status)
        assertTrue(
            store
                .allOrders()
                .single()
                .statusUpdatedAt
                ?.isNotBlank() == true,
        )

        store.markPendingWater("order-1")
        assertEquals(OrderStatus.PendingWater, store.allOrders().single().status)

        store.markCancelled("order-1")
        val cancelled = store.allOrders().single()
        assertEquals(OrderStatus.Cancelled, cancelled.status)
        assertTrue(cancelled.cancelledAt?.isNotBlank() == true)
        assertTrue(store.activeOrders().isEmpty())

        store.markPendingWater("order-1")
        val rewatered = store.activeOrders().single()
        assertEquals(OrderStatus.PendingWater, rewatered.status)
        assertEquals(null, rewatered.cancelledAt)
    }

    @Test
    fun waitingTransferAndCompletedTimestampsArePersisted() {
        val store = testStore()
        store.insertIgnore(order("order-1"))

        store.markPendingDelivery("order-1")
        val waitingTransfer = store.allOrders().single()
        assertEquals(OrderStatus.WaitingTransfer, waitingTransfer.status)
        assertTrue(waitingTransfer.waterCompletedAt?.isNotBlank() == true)

        store.markCompleted("order-1")
        val completed = store.allOrders().single()
        assertEquals(OrderStatus.Completed, completed.status)
        assertTrue(completed.transferCompletedAt?.isNotBlank() == true)
    }

    @Test
    fun transferAliasAllowsLaterUrgeWithoutDuplicatingOrder() {
        val store = testStore()
        store.insertIgnore(
            order(
                id = "old-order",
                baseHash = "base-1",
                aliases = listOf("old-pos"),
                ikmsOrder = "old-pos",
            ),
        )

        assertEquals(
            1,
            store.updateTableByOrderAlias(
                previousOrderAlias = "old-pos",
                recipeCode = "recipe-old-order",
                newTableCode = "22",
                newOrderAliases = listOf("new-pos"),
            ),
        )
        assertEquals(1, store.markUrgedByOrderAliases(listOf("new-pos"), "recipe-old-order"))

        val updated = store.allOrders().single()
        assertEquals("22", updated.tableCode)
        assertEquals("304", updated.operation)
        assertTrue(updated.isUrged)
        assertEquals(setOf("old-pos", "new-pos"), updated.orderAliases.toSet())
    }

    @Test
    fun cancelPendingByBaseHashBlocksWaitingTransferOrder() {
        val store = testStore()
        store.insertIgnore(
            order(
                id = "order-1",
                baseHash = "base-1",
                status = OrderStatus.WaitingTransfer,
            ),
        )

        val result = store.cancelPendingByBaseHash("base-1")

        assertEquals(CancelResult.RequiresManualIntervention, result)
        assertEquals(OrderStatus.WaitingTransfer, store.allOrders().single().status)
    }

    @Test
    fun unTimestampedCancelEventIgnoredForRewateredOrderButAllowedForNormalOrder() {
        val store = testStore()

        // 1. A normal order that was not manually updated/re-watered
        val normalOrder = order(id = "normal-1", baseHash = "hash-1")
        store.insertIgnore(normalOrder)
        assertEquals(OrderStatus.PendingWater, store.allOrders().first { it.id == "normal-1" }.status)
        // Untimestamped cancel event cancels the normal order
        val result1 = store.cancelPendingByBaseHash("hash-1", eventTime = null)
        assertEquals(CancelResult.Canceled, result1)
        assertEquals(OrderStatus.Cancelled, store.allOrders().first { it.id == "normal-1" }.status)

        // 2. A re-watered order (manually updated)
        val orderToRewater = order(id = "rewater-1", baseHash = "hash-2")
        store.insertIgnore(orderToRewater)
        store.markCancelled("rewater-1")
        store.markPendingWater("rewater-1")

        val rewatered = store.allOrders().first { it.id == "rewater-1" }
        assertEquals(OrderStatus.PendingWater, rewatered.status)
        assertTrue(rewatered.statusUpdatedAt != rewatered.orderTime)

        // Untimestamped cancel event should be ignored for the re-watered order
        val result2 = store.cancelPendingByBaseHash("hash-2", eventTime = null)
        assertEquals(CancelResult.NotFound, result2)
        assertEquals(OrderStatus.PendingWater, store.allOrders().first { it.id == "rewater-1" }.status)

        // A newer timestamped cancel event should cancel it
        val futureTime = "3000-01-01 00:00:00"
        val result3 = store.cancelPendingByBaseHash("hash-2", eventTime = futureTime)
        assertEquals(CancelResult.Canceled, result3)
        assertEquals(OrderStatus.Cancelled, store.allOrders().first { it.id == "rewater-1" }.status)
    }

    @Test
    fun insertIgnoreRefreshesBlockingStructureIssueWhenIncomingOrderIsComplete() {
        val store = testStore()
        val incomplete =
            order(
                id = "order-1",
                aliases = listOf("pos-1"),
                sourceRootId = "root-1",
                rootPosFoodCode = "root-food",
                structureStatus = OrderStructureStatus.IncompleteFromApi,
            )
        val complete =
            incomplete.copy(
                potBottomName = "番茄锅",
                structureStatus = OrderStructureStatus.Complete,
                structureMessage = null,
            )

        assertTrue(store.insertIgnore(incomplete))
        assertTrue(store.insertIgnore(complete))

        val updated = store.allOrders().single()
        assertEquals(OrderStructureStatus.Complete, updated.structureStatus)
        assertEquals("番茄锅", updated.potBottomName)
    }

    @Test
    fun aliasOperationsOnlyMatchWhenRecipeCodeMatchesToPreventCrossPotMixedUp() {
        val store = testStore()
        val order1 = order(id = "pot-1", baseHash = "hash-1", aliases = listOf("pos-bill-1"), rootPosFoodCode = "TOMATO")
        val order2 = order(id = "pot-2", baseHash = "hash-2", aliases = listOf("pos-bill-1"), rootPosFoodCode = "SPICY")
        store.insertIgnore(order1)
        store.insertIgnore(order2)

        // 1. Urge only POT-1 (TOMATO) by Alias and recipeCode
        val urgedCount = store.markUrgedByOrderAliases(listOf("pos-bill-1"), "TOMATO")
        assertEquals(1, urgedCount)
        assertTrue(store.allOrders().first { it.id == "pot-1" }.isUrged)
        assertTrue(!store.allOrders().first { it.id == "pot-2" }.isUrged)

        // 2. Transfer only POT-2 (SPICY) table by Alias and recipeCode
        val transferCount =
            store.updateTableByOrderAlias(
                previousOrderAlias = "pos-bill-1",
                recipeCode = "SPICY",
                newTableCode = "A99",
                newOrderAliases = listOf("pos-bill-1-transferred"),
            )
        assertEquals(1, transferCount)
        assertEquals("A99", store.allOrders().first { it.id == "pot-2" }.tableCode)
        assertEquals("11", store.allOrders().first { it.id == "pot-1" }.tableCode)

        // 3. Cancel only POT-1 (TOMATO) by Alias and recipeCode
        val cancelResult = store.cancelPendingByOrderAliases(listOf("pos-bill-1"), "TOMATO")
        assertEquals(CancelResult.Canceled, cancelResult)
        assertEquals(OrderStatus.Cancelled, store.allOrders().first { it.id == "pot-1" }.status)
        assertEquals(OrderStatus.PendingWater, store.allOrders().first { it.id == "pot-2" }.status)
    }

    @Test
    fun mixedCancellationPrioritizesRequiresManualIntervention() {
        val store = testStore()
        val order1 = order(id = "pot-1", baseHash = "hash-1", status = OrderStatus.PendingWater)
        val order2 = order(id = "pot-2", baseHash = "hash-1", status = OrderStatus.WaitingTransfer) // Already watered
        store.insertIgnore(order1)
        store.insertIgnore(order2)

        val result = store.cancelPendingByBaseHash("hash-1")
        assertEquals(CancelResult.RequiresManualIntervention, result)
        assertEquals(OrderStatus.Cancelled, store.allOrders().first { it.id == "pot-1" }.status)
        assertEquals(OrderStatus.WaitingTransfer, store.allOrders().first { it.id == "pot-2" }.status)
    }

    @Test
    fun delayedOrderIsAutomaticallyCancelableAndWakesUpWhenUrged() {
        val store = testStore()
        val order1 = order(id = "pot-1", baseHash = "hash-1", status = OrderStatus.Delayed)
        store.insertIgnore(order1)
        assertEquals(OrderStatus.Delayed, store.allOrders().first { it.id == "pot-1" }.status)

        // 1. Urge the delayed order -> wakes it up to PendingWater
        val urgedCount = store.markUrgedByBaseHash("hash-1")
        assertEquals(1, urgedCount)
        val updatedUrged = store.allOrders().first { it.id == "pot-1" }
        assertEquals(OrderStatus.PendingWater, updatedUrged.status)
        assertTrue(updatedUrged.isUrged)

        // 2. Cancel a delayed order directly
        val order2 = order(id = "pot-2", baseHash = "hash-2", status = OrderStatus.Delayed)
        store.insertIgnore(order2)
        assertEquals(OrderStatus.Delayed, store.allOrders().first { it.id == "pot-2" }.status)

        val cancelResult = store.cancelPendingByBaseHash("hash-2")
        assertEquals(CancelResult.Canceled, cancelResult)
        assertEquals(OrderStatus.Cancelled, store.allOrders().first { it.id == "pot-2" }.status)
    }

    @Test
    fun newerTransferOperateTimeCanFollowUrge() {
        val store = testStore()
        val order =
            order(
                id = "order-1",
                baseHash = "old-base",
                aliases = listOf("old-pos"),
                ikmsOrder = "old-pos",
                rootPosFoodCode = "recipe-1",
            ).copy(orderTime = "2026-06-18 15:01:21")
        store.insertIgnore(order)

        // Urge: updates statusUpdatedAt to urge time
        store.markUrgedByBaseHash("old-base", eventTime = "2026-06-18 15:01:59")

        // Every POS event uses its own operateTime as the event timestamp.
        val updatedCount =
            store.updateTableByBaseHash(
                baseHash = "old-base",
                newTableCode = "16",
                newOrderAliases = listOf("new-pos"),
                eventTime = "2026-06-18 15:02:21",
            )

        assertEquals(1, updatedCount)
        assertEquals("16", store.allOrders().single().tableCode)
    }

    @Test
    fun staleTransferOperateTimeCannotOverwriteNewerEventWatermark() {
        val store = testStore()
        store.insertIgnore(
            order(id = "order-1", baseHash = "old-base")
                .copy(orderTime = "2026-06-18 15:01:21"),
        )
        store.markUrgedByBaseHash("old-base", eventTime = "2026-06-18 15:01:59")

        val updatedCount =
            store.updateTableByBaseHash(
                baseHash = "old-base",
                newTableCode = "16",
                newOrderAliases = listOf("new-pos"),
                eventTime = "2026-06-18 15:01:30",
            )

        assertEquals(0, updatedCount)
        assertEquals("11", store.allOrders().single().tableCode)
    }

    @Test
    fun transferEventTargetsOldestMatchingOrderByOperateTime() {
        val store = testStore()
        store.insertIgnore(
            order(id = "newer", baseHash = "same-base")
                .copy(orderTime = "2026-06-18 15:02:00"),
        )
        store.insertIgnore(
            order(id = "older", baseHash = "same-base")
                .copy(orderTime = "2026-06-18 15:01:00"),
        )

        assertEquals(
            1,
            store.updateTableByBaseHash(
                baseHash = "same-base",
                newTableCode = "88",
                transferEventId = "transfer-event-1",
                newOrderAliases = listOf("new-pos"),
                eventTime = "2026-06-18 15:03:00",
            ),
        )

        assertEquals("88", store.allOrders().first { it.id == "older" }.tableCode)
        assertEquals("11", store.allOrders().first { it.id == "newer" }.tableCode)
    }

    @Test
    fun old301PollingCannotOverwriteTransferredAndUrgedIdentity() {
        val store = testStore()
        store.insertIgnore(
            order(
                id = "original",
                baseHash = "old-base",
                aliases = listOf("old-pos"),
                ikmsOrder = "old-pos",
                sourceRootId = "root-1",
            ).copy(orderTime = "2026-06-18 15:00:00"),
        )
        store.updateTableByBaseHash(
            baseHash = "old-base",
            newBaseHash = "new-base",
            transferEventId = "transfer-1",
            newTableCode = "88",
            newOrderAliases = listOf("new-pos"),
            eventTime = "2026-06-18 15:01:00",
        )
        store.markUrgedByBaseHash(
            baseHash = "new-base",
            urgeEventId = "urge-1",
            eventTime = "2026-06-18 15:02:00",
        )

        store.insertIgnore(
            order(
                id = "old-poll-copy",
                baseHash = "old-base",
                aliases = listOf("old-pos"),
                ikmsOrder = "old-pos",
                sourceRootId = "root-1",
            ).copy(orderTime = "2026-06-18 15:00:00"),
        )

        val updated = store.allOrders().single()
        assertEquals("88", updated.tableCode)
        assertEquals("new-base", updated.baseHash)
        assertEquals("304", updated.operation)
        assertTrue(updated.isUrged)
    }

    @Test
    fun cancelEventDoesNotSkipOlderProducedOrderToCancelNewerPendingOrder() {
        val store = testStore()
        store.insertIgnore(
            order(id = "newer", baseHash = "same-base", status = OrderStatus.PendingWater)
                .copy(orderTime = "2026-06-18 15:02:00"),
        )
        store.insertIgnore(
            order(id = "older", baseHash = "same-base", status = OrderStatus.WaitingTransfer)
                .copy(orderTime = "2026-06-18 15:01:00"),
        )

        val result =
            store.cancelPendingByBaseHash(
                baseHash = "same-base",
                cancelEventId = "cancel-event-1",
                eventTime = "2026-06-18 15:03:00",
            )

        assertEquals(CancelResult.RequiresManualIntervention, result)
        assertEquals(OrderStatus.PendingWater, store.allOrders().first { it.id == "newer" }.status)
    }

    @Test
    fun insertIgnorePreventsDuplicatesForSameBusinessIdentity() {
        val store = testStore()
        val original =
            order(
                id = "order-1",
                baseHash = "old-base",
                aliases = listOf("old-pos"),
                ikmsOrder = "old-pos",
                sourceRootId = "root-1",
                rootPosFoodCode = "recipe-1",
            )
        store.insertIgnore(original)

        // Incoming duplicate with different ID (e.g. after table transfer was merged)
        val duplicate =
            order(
                id = "order-2",
                baseHash = "new-base",
                aliases = listOf("old-pos", "new-pos"),
                ikmsOrder = "new-pos",
                sourceRootId = "root-1",
                rootPosFoodCode = "recipe-1",
            ).copy(tableCode = "16")

        // insertIgnore should merge the tableCode to 16 but not add a new entry
        assertTrue(store.insertIgnore(duplicate))
        assertEquals(1, store.allOrders().size)
        assertEquals("16", store.allOrders().single().tableCode)
    }

    private fun order(
        id: String,
        status: OrderStatus = OrderStatus.PendingWater,
        baseHash: String = "base-$id",
        aliases: List<String> = listOf(id),
        ikmsOrder: String = aliases.firstOrNull().orEmpty(),
        sourceRootId: String? = null,
        rootPosFoodCode: String? = null,
        structureStatus: OrderStructureStatus = OrderStructureStatus.Complete,
        missingSlotLabels: List<String> = emptyList(),
    ): Order =
        Order(
            id = id,
            recipeCode = "recipe-$id",
            quantity = 1,
            targetTemperature = 180,
            cookSeconds = 60,
            spiceLevel = 0,
            tableCode = "11",
            orderTime = "2026-06-17 10:00:00",
            potMode = PotMode.FourGrid,
            potBottomName = "清水锅",
            tasteSummary = "标准",
            slotSummary = "清水,番茄,三鲜,清水",
            status = status,
            baseHash = baseHash,
            ikmsOrder = ikmsOrder,
            rootPosFoodCode = rootPosFoodCode,
            slotCodeSummary = "A,B,C,D",
            operator = "测试员",
            operation = "301",
            potBottomSummary = "清水锅",
            orderAliases = aliases,
            sourceRootId = sourceRootId,
            structureStatus = structureStatus,
            structureMessage = structureStatus.name,
            expectedSlotCount = 4,
            attachedBottomCount = 4,
            rawBottomCandidateCount = 4,
            missingSlotLabels = missingSlotLabels,
        )

    private fun testStore(): LocalOrderStore = LocalOrderStore(InMemoryOrderStorage(), InMemoryOrderCodec())

    private class InMemoryOrderStorage : LocalOrderKeyValueStorage {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }

    private class InMemoryOrderCodec : LocalOrderCodec {
        private val snapshots = mutableMapOf<String, List<Order>>()
        private var nextSnapshotId = 0

        override fun encode(orders: List<Order>): String {
            val key = "snapshot-${++nextSnapshotId}"
            snapshots[key] =
                orders.map {
                    it.copy(
                        orderAliases = it.orderAliases.toList(),
                        missingSlotLabels = it.missingSlotLabels.toList(),
                    )
                }
            return key
        }

        override fun decode(payload: String): List<Order> =
            snapshots[payload]
                ?.map {
                    it.copy(
                        orderAliases = it.orderAliases.toList(),
                        missingSlotLabels = it.missingSlotLabels.toList(),
                    )
                }.orEmpty()
    }
}
