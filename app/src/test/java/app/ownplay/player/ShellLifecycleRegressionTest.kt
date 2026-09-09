package app.ownplay.player

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceBlockAfter
import app.ownplay.player.testing.sourceText
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
    fun mobileActiveSourceHelperPersistsAndDoesNotRecurse() {
        val path = "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt"
        val source = sourceText(path)
        val helper = sourceBlockAfter(
            source,
            "fun rememberActiveSource(sourceId: String?)",
        )

        assertTrue(helper.contains("activeSourceId = sourceId"))
        assertTrue(helper.contains("activePlaylistStore.set(sourceId)"))
        assertFalse(helper.contains("rememberActiveSource(sourceId)"))
    }

    @Test
    fun mobileWaitsForPersistedSelectionBeforeResolvingFallback() {
        val path = "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt"
        val normalized = normalizedSource(sourceText(path))
        assertTrue(
            normalized.contains(
                "val persistedSelection = activePlaylistSelection as? ActivePlaylistSelection.Ready ?: return@LaunchedEffect",
            ),
        )
        assertTrue(
            normalized.contains(
                "if (resolvedSourceId != null && previousSourceId != resolvedSourceId) { runtime.onActiveSourceSelected(resolvedSourceId)",
            ),
        )
    }

    @Test
    fun mobileLiveSyncStatusIsScopedToDisplayedSource() {
        val path = "src/mobile/java/app/ownplay/player/ui/TargetLiveRoute.kt"
        val normalized = normalizedSource(sourceText(path))
        assertTrue(normalized.contains("syncState.sourceId == sourceId"))
    }

    @Test
    fun mobileBackHierarchyFallsThroughToExitOnlyAtLiveRoot() {
        val path = "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt"
        val source = sourceText(path)
        assertTrue(source.contains("import androidx.activity.compose.BackHandler"))

        val block = normalizedSource(
            sourceBlockAfter(
                source,
                "BackHandler(enabled = section != MobileSection.LIVE)",
            ),
        )

        assertTrue(block.contains("PlaybackInteractionBridge.handleBack()"))
        assertTrue(block.contains("MobileSection.MOVIES, MobileSection.SERIES, -> openSection(MobileSection.LIBRARY)"))
        assertTrue(block.contains("MobileSection.LIBRARY, MobileSection.SETTINGS, -> openSection(MobileSection.LIVE)"))
        assertFalse(block.contains("showExitConfirmation"))
    }

    @Test
    fun rebuiltTvFoundationOwnsPrimaryBackAndFocusWithoutLegacyShellState() {
        val target = sourceText("src/tv/java/app/ownplay/player/ui/TargetOwnPlayApp.kt")
        val root = sourceText(
            "src/tv/java/app/ownplay/player/ui/rebuild/RebuiltOwnPlayTvApp.kt",
        )
        val sharedRoot = sourceText("src/main/java/app/ownplay/player/ui/OwnPlayRoot.kt")

        assertTrue(target.contains("RebuiltOwnPlayTvApp("))
        assertFalse(target.contains("TVOwnPlayApp("))
        assertTrue(root.contains("BackHandler(enabled = destination != TvDestination.HOME)"))
        assertTrue(root.contains("returnToRail(TvDestination.HOME)"))
        assertTrue(root.contains("runtime.observeSourceSummaries().collectAsState"))
        assertFalse(root.contains("ActivePlaylistStore"))
        assertFalse(root.contains("rememberActiveSource"))
        assertFalse(root.contains("TvDestination.LIBRARY"))
        assertFalse(sharedRoot.contains("focusManager.moveFocus(FocusDirection.Next)"))
    }
}
