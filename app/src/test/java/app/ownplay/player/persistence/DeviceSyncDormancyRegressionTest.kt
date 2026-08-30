package app.ownplay.player.persistence

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncDormancyRegressionTest {
    @Test
    fun activeLocalMutationPathsDoNotWriteDeviceSyncState() {
        val activeMutationPaths = listOf(
            "src/main/java/app/ownplay/player/personalization",
            "src/main/java/app/ownplay/player/source/onboarding",
            "src/main/java/app/ownplay/player/source/management",
        )
        val offenders = activeMutationPaths
            .flatMap { relative -> appFile(relative).walkTopDown().filter(File::isFile).toList() }
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("DeviceSyncLocalMutationWriter") }
            .map(File::path)

        assertTrue(
            "Deferred Device Sync write-through returned to active product paths: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun playlistRefreshStateRemainsSeparateFromDormantDeviceSync() {
        val sourceSyncState = appFile(
            "src/main/java/app/ownplay/player/source/SourceSyncState.kt",
        ).readText()
        assertTrue(sourceSyncState.contains("SourceSyncState"))
        assertFalse(sourceSyncState.contains("DeviceSync"))
    }

    @Test
    fun compatibilitySchemaAndDeviceSyncImplementationRemainDormant() {
        val database = appFile(
            "src/main/java/app/ownplay/player/persistence/OwnPlayDatabase.kt",
        ).readText()
        val writer = appFile(
            "src/main/java/app/ownplay/player/persistence/sync/DeviceSyncLocalMutationWriter.kt",
        )

        assertTrue(database.contains("version = 6"))
        assertTrue(database.contains("DeviceSyncLocalStateEntity::class"))
        assertTrue(writer.isFile)
    }

    private fun appFile(relativeToApp: String): File {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"))
        return candidates.firstOrNull(File::exists)
            ?: error("Source path not found: $relativeToApp")
    }
}
