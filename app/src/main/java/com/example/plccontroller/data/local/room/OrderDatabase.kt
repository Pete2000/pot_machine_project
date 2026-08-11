package com.example.plccontroller.data.local.room

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["status", "order_time"], name = "idx_orders_status_order_time"),
        Index(value = ["base_hash"], name = "idx_orders_base_hash"),
        Index(value = ["ikms_order"], name = "idx_orders_ikms_order"),
        Index(value = ["source_root_id", "root_pos_food_code"], name = "idx_orders_source_root"),
    ],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_code") val recipeCode: String,
    val quantity: Int,
    @ColumnInfo(name = "target_temperature") val targetTemperature: Int,
    @ColumnInfo(name = "cook_seconds") val cookSeconds: Int,
    @ColumnInfo(name = "spice_level") val spiceLevel: Int,
    @ColumnInfo(name = "table_code") val tableCode: String?,
    @ColumnInfo(name = "order_time") val orderTime: String?,
    @ColumnInfo(name = "pot_mode") val potMode: String,
    @ColumnInfo(name = "pot_bottom_name") val potBottomName: String?,
    @ColumnInfo(name = "taste_summary") val tasteSummary: String?,
    @ColumnInfo(name = "slot_summary") val slotSummary: String?,
    val status: String,
    @ColumnInfo(name = "base_hash") val baseHash: String?,
    @ColumnInfo(name = "ikms_order") val ikmsOrder: String?,
    @ColumnInfo(name = "source_root_id") val sourceRootId: String?,
    @ColumnInfo(name = "root_pos_food_code") val rootPosFoodCode: String?,
    @ColumnInfo(name = "slot_code_summary") val slotCodeSummary: String?,
    val operator: String?,
    val operation: String?,
    @ColumnInfo(name = "pot_bottom_summary") val potBottomSummary: String?,
    @ColumnInfo(name = "order_aliases_json") val orderAliasesJson: String,
    @ColumnInfo(name = "structure_status") val structureStatus: String,
    @ColumnInfo(name = "structure_message") val structureMessage: String?,
    @ColumnInfo(name = "expected_slot_count") val expectedSlotCount: Int,
    @ColumnInfo(name = "attached_bottom_count") val attachedBottomCount: Int,
    @ColumnInfo(name = "raw_bottom_candidate_count") val rawBottomCandidateCount: Int,
    @ColumnInfo(name = "missing_slot_labels_json") val missingSlotLabelsJson: String,
    @ColumnInfo(name = "water_completed_at") val waterCompletedAt: String?,
    @ColumnInfo(name = "cancelled_at") val cancelledAt: String?,
    @ColumnInfo(name = "transfer_completed_at") val transferCompletedAt: String?,
    @ColumnInfo(name = "status_updated_at") val statusUpdatedAt: String?,
    @ColumnInfo(name = "is_urged") val isUrged: Boolean,
    @ColumnInfo(name = "urged_at") val urgedAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "order_aliases",
    primaryKeys = ["alias", "order_id"],
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["order_id"], name = "idx_order_aliases_order_id")],
)
data class OrderAliasEntity(
    val alias: String,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "alias_type") val aliasType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "order_events",
    indices = [
        Index(value = ["order_id", "created_at"], name = "idx_order_events_order_id_created"),
        Index(value = ["event_type", "created_at"], name = "idx_order_events_type_created"),
    ],
)
data class OrderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: String?,
    @ColumnInfo(name = "event_type") val eventType: String,
    val operation: String?,
    @ColumnInfo(name = "from_status") val fromStatus: String?,
    @ColumnInfo(name = "to_status") val toStatus: String?,
    @ColumnInfo(name = "table_code") val tableCode: String?,
    @ColumnInfo(name = "source_order_alias") val sourceOrderAlias: String?,
    val message: String,
    @ColumnInfo(name = "raw_payload_json") val rawPayloadJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "plc_command_audits",
    indices = [
        Index(value = ["command_id"], name = "idx_plc_commands_command_id"),
        Index(value = ["order_id"], name = "idx_plc_commands_order_id"),
        Index(value = ["command_type", "created_at"], name = "idx_plc_commands_type_created"),
    ],
)
data class PlcCommandAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "command_id") val commandId: Int,
    @ColumnInfo(name = "order_id") val orderId: String?,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "command_name") val commandName: String,
    @ColumnInfo(name = "request_register_start") val requestRegisterStart: Int?,
    @ColumnInfo(name = "request_payload_json") val requestPayloadJson: String?,
    @ColumnInfo(name = "tx_hex") val txHex: String?,
    @ColumnInfo(name = "rx_hex") val rxHex: String?,
    val result: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
)

@Entity(
    tableName = "plc_fault_events",
    indices = [
        Index(value = ["fault_type", "created_at"], name = "idx_plc_fault_type_created"),
        Index(value = ["resolved_at", "created_at"], name = "idx_plc_fault_resolution_created"),
    ],
)
data class PlcFaultEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "fault_type") val faultType: String,
    val severity: String,
    val message: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
)

@Entity(tableName = "order_store_metadata")
data class OrderStoreMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
abstract class OrderDatabaseDao {
    @Query("SELECT * FROM orders ORDER BY created_at ASC, id ASC")
    abstract fun loadOrders(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAliases(aliases: List<OrderAliasEntity>)

    @Insert
    abstract fun insertOrderEvents(events: List<OrderEventEntity>)

    @Query("DELETE FROM order_aliases")
    abstract fun deleteAliases()

    @Query("DELETE FROM orders")
    abstract fun deleteOrders()

    @Query("DELETE FROM order_events")
    abstract fun deleteOrderEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putMetadata(metadata: OrderStoreMetadataEntity)

    @Query("SELECT value FROM order_store_metadata WHERE `key` = :key LIMIT 1")
    abstract fun metadataValue(key: String): String?

    @Insert
    abstract fun insertPlcCommandAudit(audit: PlcCommandAuditEntity): Long

    @Query("UPDATE plc_command_audits SET result = :result, error_message = :errorMessage, completed_at = :completedAt WHERE id = :auditId")
    abstract fun finishPlcCommandAudit(
        auditId: Long,
        result: String,
        errorMessage: String?,
        completedAt: Long,
    )

    @Insert
    abstract fun insertPlcFaultEvent(event: PlcFaultEventEntity): Long

    @Query("UPDATE plc_fault_events SET resolved_at = :resolvedAt WHERE id = :eventId")
    abstract fun resolvePlcFaultEvent(
        eventId: Long,
        resolvedAt: Long,
    )

    @Query("DELETE FROM orders WHERE status IN ('Completed', 'Cancelled', 'Failed') AND updated_at < :cutoff")
    abstract fun deleteOldTerminalOrders(cutoff: Long)

    @Query("DELETE FROM order_events WHERE created_at < :cutoff")
    abstract fun deleteOldOrderEvents(cutoff: Long)

    @Query("DELETE FROM plc_command_audits WHERE created_at < :cutoff")
    abstract fun deleteOldPlcCommandAudits(cutoff: Long)

    @Query("DELETE FROM plc_fault_events WHERE created_at < :cutoff")
    abstract fun deleteOldPlcFaultEvents(cutoff: Long)

    @Transaction
    open fun replaceOrderSnapshot(
        orders: List<OrderEntity>,
        aliases: List<OrderAliasEntity>,
        events: List<OrderEventEntity>,
    ) {
        deleteAliases()
        deleteOrders()
        if (orders.isNotEmpty()) insertOrders(orders)
        if (aliases.isNotEmpty()) insertAliases(aliases)
        if (events.isNotEmpty()) insertOrderEvents(events)
    }

    @Transaction
    open fun clearAllOrderData() {
        deleteAliases()
        deleteOrders()
        deleteOrderEvents()
    }
}

@Database(
    entities = [
        OrderEntity::class,
        OrderAliasEntity::class,
        OrderEventEntity::class,
        PlcCommandAuditEntity::class,
        PlcFaultEventEntity::class,
        OrderStoreMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OrderDatabase : RoomDatabase() {
    abstract fun dao(): OrderDatabaseDao

    companion object {
        const val DATABASE_NAME = "pot_machine_orders.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(connection: SupportSQLiteDatabase) {
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS idx_plc_fault_resolution_created " +
                            "ON plc_fault_events(resolved_at, created_at)",
                    )
                }
            }

        fun create(context: Context): OrderDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    OrderDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2)
                .build()
    }
}
