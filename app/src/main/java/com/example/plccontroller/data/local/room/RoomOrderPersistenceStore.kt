package com.example.plccontroller.data.local.room

import com.example.plccontroller.data.local.CancelResult
import com.example.plccontroller.data.local.JsonLocalOrderCodec
import com.example.plccontroller.data.local.LocalOrderKeyValueStorage
import com.example.plccontroller.data.local.LocalOrderStore
import com.example.plccontroller.data.local.OrderPersistenceStore
import com.example.plccontroller.domain.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomOrderPersistenceStore(
    database: OrderDatabase,
    legacyOrdersProvider: () -> List<Order>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OrderPersistenceStore {
    private val storage = RoomOrderSnapshotStorage(database, legacyOrdersProvider)
    private val delegate =
        LocalOrderStore(
            storage = storage,
            codec = JsonLocalOrderCodec,
            maxOrders = null,
        )

    override suspend fun insertIgnore(order: Order): Boolean =
        databaseCall {
            delegate.insertIgnore(order)
        }

    override suspend fun cancelPendingByBaseHash(
        baseHash: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult =
        databaseCall {
            delegate.cancelPendingByBaseHash(baseHash, cancelEventId, eventTime)
        }

    override suspend fun cancelPendingByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        cancelEventId: String?,
        eventTime: String?,
    ): CancelResult =
        databaseCall {
            delegate.cancelPendingByOrderAliases(
                orderAliases,
                recipeCode,
                cancelEventId,
                eventTime,
            )
        }

    override suspend fun updateTableByBaseHash(
        baseHash: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int =
        databaseCall {
            delegate.updateTableByBaseHash(
                baseHash,
                newBaseHash,
                transferEventId,
                newTableCode,
                newOrderAliases,
                eventTime,
            )
        }

    override suspend fun updateTableByOrderAlias(
        previousOrderAlias: String,
        recipeCode: String,
        newBaseHash: String?,
        transferEventId: String?,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String?,
    ): Int =
        databaseCall {
            delegate.updateTableByOrderAlias(
                previousOrderAlias,
                recipeCode,
                newBaseHash,
                transferEventId,
                newTableCode,
                newOrderAliases,
                eventTime,
            )
        }

    override suspend fun markUrgedByBaseHash(
        baseHash: String,
        urgeEventId: String?,
        eventTime: String?,
    ): Int = databaseCall { delegate.markUrgedByBaseHash(baseHash, urgeEventId, eventTime) }

    override suspend fun markUrgedByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        urgeEventId: String?,
        eventTime: String?,
    ): Int =
        databaseCall {
            delegate.markUrgedByOrderAliases(orderAliases, recipeCode, urgeEventId, eventTime)
        }

    override suspend fun markDispatching(orderId: String) = databaseCall { delegate.markDispatching(orderId) }

    override suspend fun markPendingDelivery(orderId: String) = databaseCall { delegate.markPendingDelivery(orderId) }

    override suspend fun markCompleted(orderId: String) = databaseCall { delegate.markCompleted(orderId) }

    override suspend fun markCancelled(orderId: String) = databaseCall { delegate.markCancelled(orderId) }

    override suspend fun markFailed(orderId: String) = databaseCall { delegate.markFailed(orderId) }

    override suspend fun markDelayed(orderId: String) = databaseCall { delegate.markDelayed(orderId) }

    override suspend fun markPendingWater(orderId: String) = databaseCall { delegate.markPendingWater(orderId) }

    override suspend fun activeOrders(): List<Order> = databaseCall { delegate.activeOrders() }

    override suspend fun allOrders(): List<Order> = databaseCall { delegate.allOrders() }

    override suspend fun clearAllOrdersForDebug() {
        databaseCall { delegate.clearAllOrdersForDebug() }
    }

    private suspend fun <T> databaseCall(block: () -> T): T = withContext(ioDispatcher) { block() }
}

private class RoomOrderSnapshotStorage(
    private val database: OrderDatabase,
    private val legacyOrdersProvider: () -> List<Order>,
) : LocalOrderKeyValueStorage {
    private val dao = database.dao()
    private val migrationLock = Any()

    @Volatile private var migrationChecked = false

    override fun getString(key: String): String {
        ensureMigrated()
        return JsonLocalOrderCodec.encode(dao.loadOrders().map(OrderEntity::toOrder))
    }

    override fun putString(
        key: String,
        value: String,
    ) {
        ensureMigrated()
        val nextOrders = JsonLocalOrderCodec.decode(value)
        if (nextOrders.isEmpty()) {
            dao.clearAllOrderData()
            return
        }
        val now = System.currentTimeMillis()
        val previousEntities = dao.loadOrders()
        val previousOrders = previousEntities.map(OrderEntity::toOrder)
        val previousById = previousEntities.associateBy(OrderEntity::id)
        val nextEntities =
            nextOrders.map { order ->
                order.toEntity(previous = previousById[order.id], now = now)
            }
        dao.replaceOrderSnapshot(
            orders = nextEntities,
            aliases = nextOrders.toAliasEntities(now),
            events = buildOrderAuditEvents(previousOrders, nextOrders, now),
        )
        pruneExpiredData(now)
    }

    private fun ensureMigrated() {
        if (migrationChecked) return
        synchronized(migrationLock) {
            if (migrationChecked) return
            val currentVersion = dao.metadataValue(MIGRATION_METADATA_KEY)
            if (currentVersion != MIGRATION_VERSION) {
                database.runInTransaction {
                    val existing = dao.loadOrders()
                    if (existing.isEmpty()) {
                        val legacyOrders = legacyOrdersProvider()
                        if (legacyOrders.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            dao.replaceOrderSnapshot(
                                orders = legacyOrders.map { it.toEntity(now = now) },
                                aliases = legacyOrders.toAliasEntities(now),
                                events =
                                    buildOrderAuditEvents(
                                        before = emptyList(),
                                        after = legacyOrders,
                                        now = now,
                                        importMode = true,
                                    ),
                            )
                        }
                    }
                    dao.putMetadata(
                        OrderStoreMetadataEntity(
                            key = MIGRATION_METADATA_KEY,
                            value = MIGRATION_VERSION,
                        ),
                    )
                }
            }
            migrationChecked = true
        }
    }

    private fun pruneExpiredData(now: Long) {
        dao.deleteOldTerminalOrders(now - ORDER_RETENTION_MS)
        dao.deleteOldOrderEvents(now - ORDER_EVENT_RETENTION_MS)
        dao.deleteOldPlcCommandAudits(now - PLC_COMMAND_RETENTION_MS)
        dao.deleteOldPlcFaultEvents(now - PLC_FAULT_RETENTION_MS)
    }

    companion object {
        private const val MIGRATION_METADATA_KEY = "shared_preferences_import_version"
        private const val MIGRATION_VERSION = "1"
        private const val DAY_MS = 24L * 60L * 60L * 1_000L
        private const val ORDER_RETENTION_MS = 7L * DAY_MS
        private const val ORDER_EVENT_RETENTION_MS = 14L * DAY_MS
        private const val PLC_COMMAND_RETENTION_MS = 7L * DAY_MS
        private const val PLC_FAULT_RETENTION_MS = 30L * DAY_MS
    }
}
