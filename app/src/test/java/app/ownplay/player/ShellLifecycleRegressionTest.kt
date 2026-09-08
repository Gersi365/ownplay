package app.ownplay.player

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceBlockAfter
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLifecycleRegressionTest {
    @Test
    fun mainActivityUsesProcessScopedRuntimeAndDoesNotCloseIt() {
        val source = sourceText("src/main/java/app/ownplay/player/MainActivity.kt")
        assertTrue(source.contains("runtime = (application as OwnPlayApplication).runtime"))

        val onDestroy = sourceBlockAfter(source, "override fun onDestroy()")
        assertFalse(onDestroy.contains("runtime.close()"))
    }

    @Test
    fun mobileAndTvActiveSourceHelpersPersistAndDoNotRecurse() {
        activeShellPaths.forEach { path ->
            val source = sourceText(path)
            val helper = sourceBlockAfter(
                source,
                "fun rememberActiveSource(sourceId: String?)",
            )

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
            val normalized = normalizedSource(sourceText(path))
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
            "src/mobile/java/app/ownplay/player/ui/TargetLiveRoute.kt",
            "src/tv/java/app/ownplay/player/ui/LiveRoute.kt",
        ).forEach { path ->
            val normalized = normalizedSource(sourceText(path))
            assertTrue(
                "$path must ignore sync status emitted by another playlist",
                normalized.contains("syncState.sourceId == sourceId"),
            )
        }
    }

    @Test
    fun mobileBackHierarchyFallsThroughToExitOnlyAtLiveRoot() {
        val path = "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt"
        val source = sourceText(path)
        assertTrue("$path must install a Compose back handler", source.contains("import androidx.activity.compose.BackHandler"))

        val block = normalizedSource(
            sourceBlockAfter(
                source,
                "BackHandler(enabled = section != MobileSection.LIVE)",
            ),
        )

        assertTrue("$path must give detail/playback back actions priority", block.contains("PlaybackInteractionBridge.handleBack()"))
        assertTrue(
            "$path must return Movies/Series catalog roots to Library",
            block.contains("MobileSection.MOVIES, MobileSection.SERIES, -> openSection(MobileSection.LIBRARY)"),
        )
        assertTrue(
            "$path must return Library/Settings roots to Live",
            block.contains("MobileSection.LIBRARY, MobileSection.SETTINGS, -> openSection(MobileSection.LIVE)"),
        )
        assertFalse("$path shell fallback must never show exit itself", block.contains("showExitConfirmation"))
    }

    @Test
    fun tvBackHierarchyFallsThroughToExitOnlyAtHomeRoot() {
        val path = "src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"
        val source = sourceText(path)
        assertTrue("$path must install a Compose back handler", source.contains("import androidx.activity.compose.BackHandler"))

        val block = normalizedSource(
            sourceBlockAfter(
                source,
                "BackHandler(enabled = destination != TvDestination.HOME)",
            ),
        )

        assertTrue(
            "$path must give Movie/Series detail and playback back actions priority",
            block.contains("PlaybackInteractionBridge.handleBack()"),
        )
        assertTrue(
            "$path must return non-Home shell destinations to Home after deeper interactions decline Back",
            block.contains("openDestination(TvDestination.HOME)"),
        )
        assertFalse(
            "$path must not restore the removed Library destination",
            block.contains("TvDestination.LIBRARY"),
        )
        assertFalse("$path shell fallback must never show exit itself", block.contains("showExitConfirmation"))
    }

    private companion object {
        val activeShellPaths = listOf(
            "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt",
            "src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt",
        )
    }
}
