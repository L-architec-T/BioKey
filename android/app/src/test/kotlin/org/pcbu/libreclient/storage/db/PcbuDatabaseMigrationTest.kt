package org.pcbu.libreclient.storage.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** First Room migration this codebase has ever needed (see PcbuDatabase.MIGRATION_1_2) —
 * verifies existing v1 rows survive the v1->v2 bump with the new column defaulting to NULL. */
@RunWith(RobolectricTestRunner::class)
class PcbuDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PcbuDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrate 1 to 2 adds macroStepsJson column and preserves existing rows`() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """INSERT INTO paired_devices
                   (deviceId, deviceName, deviceOS, userName, unlockServerPort, macAddressesCsv, pairingMethod, pairedAtEpochMs)
                   VALUES ('dev-1', 'debian', 'Linux', 'larchitect', 43296, 'aa:bb:cc:dd:ee:ff', 'UDP', 1700000000000)""",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDbName, 2, true, PcbuDatabase.MIGRATION_1_2)

        migrated.query("SELECT deviceId, deviceName, macroStepsJson FROM paired_devices WHERE deviceId = 'dev-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("debian", cursor.getString(cursor.getColumnIndexOrThrow("deviceName")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("macroStepsJson")))
        }
    }

    @Test
    fun `migrate 2 to 3 adds customName column and preserves existing rows`() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """INSERT INTO paired_devices
                   (deviceId, deviceName, deviceOS, userName, unlockServerPort, macAddressesCsv, pairingMethod, pairedAtEpochMs, macroStepsJson)
                   VALUES ('dev-1', 'debian', 'Linux', 'larchitect', 43296, 'aa:bb:cc:dd:ee:ff', 'UDP', 1700000000000, '{"steps":[]}')""",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDbName, 3, true, PcbuDatabase.MIGRATION_2_3)

        migrated.query("SELECT macroStepsJson, customName FROM paired_devices WHERE deviceId = 'dev-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"steps\":[]}", cursor.getString(cursor.getColumnIndexOrThrow("macroStepsJson")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("customName")))
        }
    }
}
