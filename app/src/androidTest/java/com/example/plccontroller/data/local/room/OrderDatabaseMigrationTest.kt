package com.example.plccontroller.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            OrderDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2PreservesOrdersAndAuditRecords() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO orders(
                    id, recipe_code, quantity, target_temperature, cook_seconds, spice_level,
                    pot_mode, status, order_aliases_json, structure_status,
                    expected_slot_count, attached_bottom_count, raw_bottom_candidate_count,
                    missing_slot_labels_json, is_urged, created_at, updated_at
                ) VALUES(
                    'order-1', 'recipe-1', 1, 180, 60, 0,
                    'Single', 'PendingWater', '[]', 'Complete',
                    1, 1, 1, '[]', 0, 1000, 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO plc_command_audits(
                    command_id, command_type, command_name, result, created_at
                ) VALUES(42, 'PHASE', 'migration-test', 'SUCCESS', 1000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO plc_fault_events(
                    fault_type, severity, message, created_at
                ) VALUES('COMMUNICATION', 'ERROR', 'migration-test', 1000)
                """.trimIndent(),
            )
            close()
        }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                2,
                true,
                OrderDatabase.MIGRATION_1_2,
            ).use { database ->
                database.query("SELECT id, status FROM orders").use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals("order-1", cursor.getString(0))
                    assertEquals("PendingWater", cursor.getString(1))
                }
                database.query("SELECT command_id, result FROM plc_command_audits").use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals(42, cursor.getInt(0))
                    assertEquals("SUCCESS", cursor.getString(1))
                }
                database.query("SELECT fault_type FROM plc_fault_events").use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals("COMMUNICATION", cursor.getString(0))
                }
                database
                    .query(
                        "SELECT name FROM sqlite_master WHERE type='index' " +
                            "AND name='idx_plc_fault_resolution_created'",
                    ).use { cursor -> assertEquals(1, cursor.count) }
            }
    }

    private companion object {
        const val TEST_DATABASE = "order-migration-test"
    }
}
