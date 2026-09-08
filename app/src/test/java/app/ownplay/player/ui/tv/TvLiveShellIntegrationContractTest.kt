package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveShellIntegrationContractTest {
    private val routeSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/LiveRoute.kt"),
        )
    }
    private val workspaceSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveWorkspace.kt"),
        )
    }

    @Test
    fun `tv live landscape uses a dedicated tv presentation workspace`() {
        assertTrue(
            "TV Live must route landscape presentation through its dedicated workspace.",
            "if (isLandscape) { TvLiveWorkspace(" in routeSource,
        )
        assertFalse(
            "TV Live must not remain coupled to the generic landscape workspace.",
            "LandscapeLiveWorkspaceAdaptive(" in routeSource,
        )
    }

    @Test
    fun `dedicated workspace reuses established browse preview and epg surfaces`() {
        assertTrue("TV Live must reuse established hierarchical browsing.", "HierarchicalLiveBrowse(" in workspaceSource)
        assertTrue("TV Live must reuse the presentation-only Preview surface.", "LivePreviewPanel(" in workspaceSource)
        assertTrue("Selected-channel EPG must remain beside Preview.", "EpgPanel(" in workspaceSource)
        assertTrue(
            "Existing channel search/filter semantics must remain wired through the TV workspace.",
            "onSearchChange = onSearchChange" in workspaceSource,
        )
    }

    @Test
    fun `preview stays presentation only and browser focus is recoverable`() {
        assertTrue("TV Live needs one explicit browser focus requester.", "val channelFocusRequester = remember { FocusRequester() }" in workspaceSource)
        assertTrue(
            "Closing Preview must restore focus toward the previously previewed channel.",
            "if (currentPreviewChannelId == null && previousPreviewChannelId != null) { requestChannelFocus(previousPreviewChannelId) }" in workspaceSource,
        )
        assertTrue(
            "EPG Left must return focus to channel browsing.",
            "event.key == Key.DirectionLeft" in workspaceSource &&
                "channelFocusRequester.requestFocus()" in workspaceSource,
        )
        assertFalse("Preview focus restoration must not scale TV geometry.", ".scale(" in workspaceSource)
        assertFalse("Preview focus restoration must not animate layout size.", "animateContentSize" in workspaceSource)
    }

    @Test
    fun `workspace owns presentation only not playback or providers`() {
        assertFalse("TV workspace must not own the playback controller.", "playbackController" in workspaceSource)
        assertFalse("TV workspace must not own the Live transition gate.", "LivePlaybackTransitionGate" in workspaceSource)
        assertFalse("TV workspace must not instantiate provider clients.", "XtreamClient(" in workspaceSource)
        assertFalse("TV workspace must not stop playback during presentation changes.", "stopPlayback" in workspaceSource)
        assertFalse("TV workspace must not start playback during presentation changes.", "startPlayback" in workspaceSource)
    }

    @Test
    fun `activation and back hierarchy remain owned by existing live policies`() {
        assertTrue(
            "Channel activation must remain routed by the established selection router.",
            "LiveChannelSelectionRouter.route(" in routeSource,
        )
        assertTrue(
            "Same-channel Preview to Full View behavior must remain policy-owned.",
            "LiveBrowseHierarchyPolicy.channelActivationAction(" in routeSource,
        )
        assertTrue(
            "Preview Back must remain mapped through the established hierarchy policy.",
            "LiveBrowseBackAction.CLOSE_PREVIEW -> onPreviewClosed()" in routeSource,
        )
        assertTrue(
            "Channel-level Back must continue to return to categories.",
            "LiveBrowseBackAction.SHOW_CATEGORIES" in routeSource,
        )
    }

    @Test
    fun `tv live keeps deterministic browse and preview proportions`() {
        assertTrue("Browse remains the larger focusable region.", ".weight(0.59f)" in workspaceSource)
        assertTrue("Preview and EPG retain a dedicated secondary region.", ".weight(0.41f)" in workspaceSource)
    }
}
