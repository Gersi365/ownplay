package app.ownplay.player.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
