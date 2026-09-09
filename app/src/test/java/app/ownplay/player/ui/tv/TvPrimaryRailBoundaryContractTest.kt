package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPrimaryRailBoundaryContractTest {
    private val boundarySource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvShellFocusBoundary.kt"),
        )
    }
    private val shellSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )
    }
    private val moviesSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/movies/TvMoviesRoute.kt"),
        )
    }
    private val seriesSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/series/TvSeriesRoute.kt"),
        )
    }
    private val liveWorkspaceSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveWorkspace.kt"),
        )
    }
    private val liveBrowserSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveChannelBrowser.kt"),
        )
    }
    private val settingsSource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
    }

    @Test
    fun `shared boundary exposes only explicit entry and visible rail return`() {
        assertTrue("The boundary must expose an explicit entry generation.", "val contentEntryGeneration: Int = 0" in boundarySource)
        assertTrue("The boundary must expose rail visibility.", "val railVisible: Boolean = false" in boundarySource)
        assertTrue("The boundary must expose a direct rail return callback.", "val requestRailFocus: () -> Unit = {}" in boundarySource)
        assertTrue(
            "The shell must scope entry to the destination that received Right.",
            "contentEntryGeneration = if (contentEntryDestination == activeDestination)" in shellSource,
        )
        assertFalse(
            "Primary rail entry must never regress to spatial focus search.",
            "moveFocus(FocusDirection.Right)" in shellSource,
        )
    }

    @Test
    fun `movies uses selected section and real actions as deterministic shell targets`() {
        assertTrue("Movies must consume the shared shell boundary.", "LocalTvShellFocusBoundary.current" in moviesSource)
        assertTrue("Movies entry must explicitly focus the selected section.", "categoryFocusRequester.requestFocus()" in moviesSource)
        assertTrue("Movies refresh failure may explicitly focus Retry.", "retryFocusRequester.requestFocus()" in moviesSource)
        assertTrue("Movies section Left must delegate to the shell boundary.", "Key.DirectionLeft -> onLeft()" in moviesSource)
        assertTrue("Movies missing-source action must be explicitly focusable.", "actionFocusRequester.requestFocus()" in moviesSource)
        assertFalse("Movies shell focus must not scale cards.", ".scale(" in moviesSource)
    }

    @Test
    fun `series uses selected section and real actions as deterministic shell targets`() {
        assertTrue("Series must consume the shared shell boundary.", "LocalTvShellFocusBoundary.current" in seriesSource)
        assertTrue("Series entry must explicitly focus the selected section.", "sectionFocusRequester.requestFocus()" in seriesSource)
        assertTrue("Series refresh failure may explicitly focus Retry.", "retryFocusRequester.requestFocus()" in seriesSource)
        assertTrue("Series section Left must delegate to the shell boundary.", "Key.DirectionLeft -> onLeft()" in seriesSource)
        assertTrue("Series missing-source action must be explicitly focusable.", "actionFocusRequester.requestFocus()" in seriesSource)
        assertFalse("Series shell focus must not scale cards.", ".scale(" in seriesSource)
    }

    @Test
    fun `live entry and rail return remain explicit without breaking preview isolation`() {
        assertTrue("Live workspace must consume the shared shell boundary.", "LocalTvShellFocusBoundary.current" in liveWorkspaceSource)
        assertTrue(
            "Channel-level shell entry must use the existing explicit channel requester.",
            "if (hierarchyLevel == LiveBrowseHierarchyLevel.CHANNELS) { requestChannelFocus(" in liveWorkspaceSource,
        )
        assertTrue(
            "Live rail return must only be armed while the shell rail is visible.",
            "if (shellFocusBoundary.railVisible) { shellFocusBoundary.requestRailFocus() true } else { false }" in liveWorkspaceSource,
        )
        assertTrue("Category rows must expose explicit Left handling.", "onLeftBoundary = onLeftBoundary" in liveBrowserSource)
        assertTrue("Live Left handling must be key-driven.", "event.key == Key.DirectionLeft" in liveBrowserSource)
        assertFalse("Live shell focus must not scale browse geometry.", ".scale(" in liveBrowserSource)
    }

    @Test
    fun `settings preserves root restoration while supporting explicit shell reentry`() {
        assertTrue("Settings must consume the shared shell boundary.", "LocalTvShellFocusBoundary.current" in settingsSource)
        assertTrue(
            "Settings shell reentry must request the currently focused root destination.",
            "rootFocusRequesters.getValue(focusedDestination).requestFocus()" in settingsSource,
        )
        assertTrue(
            "Settings Left must return through the shell only while the rail is visible.",
            "if (shellFocusBoundary.railVisible) { shellFocusBoundary.requestRailFocus() true } else { false }" in settingsSource,
        )
        assertTrue(
            "Subpage return must still restore the originating root destination.",
            "rootFocusRequesters.getValue(originatingDestination).requestFocus()" in settingsSource,
        )
        assertFalse("Settings shell focus must not scale root rows.", ".scale(" in settingsSource)
    }
}
