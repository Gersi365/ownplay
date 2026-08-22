package app.ownplay.player.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OwnPlayDatabaseMigrationHarnessTest {
    @Test
    fun schemaV1CanBeCreatedFromExportedSchema() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val databaseName = "ownplay-migration-v1-${UUID.randomUUID()}.db"
        val helper = MigrationTestHelper(
            instrumentation,
            OwnPlayDatabase::class.java,
        )

        try {
            helper.createDatabase(databaseName, 1).use { database ->
                assertTrue(database.isOpen)
            }
        } finally {
            instrumentation.targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migration1To2PreservesProviderRowsAndCreatesRecentHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val databaseName = "ownplay-migration-v1-v2-${UUID.randomUUID()}.db"
        val helper = MigrationTestHelper(
            instrumentation,
            OwnPlayDatabase::class.java,
        )

        try {
            helper.createDatabase(databaseName, 1).use { database ->
                database.execSQL(
                    """
                    INSERT INTO playlist_sources (
                        sourceId, name, sourceKind, locatorRef, enabled,
                        createdAtEpochMillis, updatedAtEpochMillis
                    ) VALUES (
                        'source-1', 'Source', 'remote_m3u', 'locator-ref', 1, 1, 1
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO provider_channels (
                        channelId, sourceId, providerKey, providerName,
                        streamLocatorRef, providerOrder, availability, lastSeenGeneration
                    ) VALUES (
                        'channel-1', 'source-1', 'provider-1', 'News One',
                        'stream-ref', 0, 'available', 1
                    )
                    """.trimIndent(),
                )
            }

            helper.runMigrationsAndValidate(
                databaseName,
                2,
                true,
                MIGRATION_1_2,
            ).use { database ->
                database.query(
                    "SELECT providerName FROM provider_channels WHERE channelId = 'channel-1'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("News One", cursor.getString(0))
                }

                database.execSQL(
                    "INSERT INTO recent_channels (channelId, watchedAtEpochMillis) " +
                        "VALUES ('channel-1', 1234)",
                )
                database.query(
                    "SELECT watchedAtEpochMillis FROM recent_channels " +
                        "WHERE channelId = 'channel-1'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1234L, cursor.getLong(0))
                }
            }
        } finally {
            instrumentation.targetContext.deleteDatabase(databaseName)
        }
    }
}
