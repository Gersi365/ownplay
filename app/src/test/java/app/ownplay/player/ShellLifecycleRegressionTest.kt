package app.ownplay.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLifecycleRegressionTest {
    @Test
    fun mainActivityUsesProcessScopedRuntimeAndDoesNotCloseIt() {
        val source = sourceText("src/main/java/app/ownplay/player/MainActivity.kt")
        assertTrue(source.contains("runtime = (application as OwnPlayApplication).runtime"))

        val onDestroy = source
            .substringAfter("override fun onDestroy() {")
            .substringBefore("private fun currentPlaybackMediaKind")
        assertFalse(onDestroy.contains("runtime.close()"))
    }

    @Test
    fun mobileAndTvActiveSourceHelpersPersistAndDoNotRecurse() {
        activeShellPaths.forEach { path ->
            val source = sourceText(path)
            val helper = source
                .substringAfter("fun rememberActiveSource(sourceId: String?) {")
                .substringBefore("\n    }")

            assertTrue("$path must update local active source", helper.contains("activeSourceId = sourceId"))
            assertTrue(
                "$path must persist active source",
                helper.contains("activePlaylistStore.set(sourceId)"),
            )
            assertFalse(
                "$path must not recursively call rememberActiveSource",
                helper.contains("rememberActiveSource(sourceId)"),
            )

            // Direct writes using the callback sourceId must stay inside the persistence helper.
            // This catches the Settings -> Live regression where local state changed but DataStore did not.
            assertEquals(
                "$path must route sourceId selection through rememberActiveSource",
                1,
                Regex("activeSourceId\\s*=\\s*sourceId").findAll(source).count(),
            )
        }
    }

    @Test
    fun mobileAndTvWaitForPersistedSelectionBeforeResolvingFallback() {
        activeShellPaths.forEach { path ->
            val normalized = sourceText(path).replace(Regex("\\s+"), " ")
            assertTrue(
                "$path must not resolve the first playlist while DataStore selection is Loading",
                normalized.contains(
                    "val persistedSelection = activePlaylistSelection as? ActivePlaylistSelection.Ready ?: return@LaunchedEffect",
                ),
            )
            assertTrue(
                "$path must refresh only after a resolved active source changes",
                normalized.contains(
                    "if (resolvedSourceId != null && previousSourceId != resolvedSourceId) { runtime.onActiveSourceSelected(resolvedSourceId)",
                ),
            )
        }
    }

    @Test
    fun mobileAndTvLiveSyncStatusIsScopedToDisplayedSource() {
        listOf(
            "src/mobile/java/app/ownplay/player/ui/MobileTargetLiveRoute.kt",
            "src/tv/java/app/ownplay/player/ui/LiveRoute.kt",
        ).forEach { path ->
            val normalized = sourceText(path).replace(Regex("\\s+"), " ")
            assertTrue(
                "$path must ignore sync status emitted by another playlist",
                normalized.contains("syncState.sourceId == sourceId"),
            )
        }
    }

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("Could not locate source file: $relativePath")
        return source.readText()
    }

    private companion object {
        val activeShellPaths = listOf(
            "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt",
            "src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt",
        )
    }
}
