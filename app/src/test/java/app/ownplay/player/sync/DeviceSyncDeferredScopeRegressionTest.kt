package app.ownplay.player.sync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncDeferredScopeRegressionTest {
    @Test
    fun `local mutations do not activate deferred Device Sync`() {
        val writer = appSource(
            "src/main/java/app/ownplay/player/persistence/sync/DeviceSyncLocalMutationWriter.kt",
        )

        assertTrue(writer.contains("Compatibility shim for the deferred cross-device sync design"))
        assertTrue(writer.contains("recordSourceCreatedOrRestored(localSourceId: String) = Unit"))
        assertTrue(writer.contains("recordSourceDeleted(localSourceId: String) = Unit"))
        assertTrue(writer.contains("recordLocalDisplayName("))
        assertTrue(writer.contains("recordHidden("))
        assertTrue(writer.contains("recordFavorites("))
        assertTrue(writer.contains("recordManualOrder("))
        assertTrue(writer.contains("recordGroupCreated(group: CustomGroupEntity) = Unit"))
        assertFalse(writer.contains("nextLocalVersion("))
    }

    @Test
    fun `product UI exposes playlist refresh but not device pairing`() {
        val settings = appSource("src/main/java/app/ownplay/player/ui/SettingsContent.kt")
        val playlistSettings = appSource("src/main/java/app/ownplay/player/ui/SettingsPlaylist.kt")

        assertFalse(settings.contains("Device Sync"))
        assertFalse(settings.contains("Pair device"))
        assertFalse(settings.contains("Pairing"))
        assertTrue(playlistSettings.contains("SourceSyncState"))
    }

    @Test
    fun `repository target contract records Device Sync as deferred`() {
        val contract = repoSource("docs/device-targets.md")

        assertTrue(contract.contains("Cross-device Device Sync is deferred"))
        assertTrue(contract.contains("SourceSyncState"))
        assertTrue(contract.contains("Backup/Restore remains the explicit portability mechanism"))
        assertTrue(contract.contains("must not create or advance Device Sync metadata"))
    }

    private fun appSource(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Source file not found: $relativeToApp")
        return file.readText()
    }

    private fun repoSource(path: String): String {
        val candidates = listOf(File(path), File("../$path"))
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Repository file not found: $path")
        return file.readText()
    }
}
