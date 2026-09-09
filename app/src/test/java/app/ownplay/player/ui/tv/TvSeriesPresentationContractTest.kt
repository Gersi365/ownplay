package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSeriesPresentationContractTest {
    private val appSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )
    }
    private val seriesSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/series/TvSeriesRoute.kt"),
        )
    }

    @Test
    fun `tv series catalog and details use dedicated tv presentation`() {
        assertTrue(
            "TV Series must route non-fullscreen presentation through the dedicated TV route.",
            "if (seriesFullscreen) { SeriesRoute(" in appSource && "else { TvSeriesRoute(" in appSource,
        )
        assertTrue("The dedicated route must own TV catalog geometry.", "private fun TvSeriesCatalogScreen(" in seriesSource)
        assertTrue("The dedicated route must own TV detail geometry.", "private fun TvSeriesDetailsScreen(" in seriesSource)
    }

    @Test
    fun `existing series playback remains the fullscreen owner`() {
        assertTrue(
            "Fullscreen Series playback must continue through the established shared route.",
            "if (seriesFullscreen) { SeriesRoute(" in appSource,
        )
        assertFalse("The TV catalog route must not own a PlayerView.", "PlayerView(" in seriesSource)
        assertFalse("The TV catalog route must not bind playback output directly.", "playbackVideoOutput.bind" in seriesSource)
    }

    @Test
    fun `tv series exposes real sections without all or download ui`() {
        assertTrue("Continue Watching must remain first-class.", "\"Continue Watching\"" in seriesSource)
        assertTrue("Favorites must remain directly reachable.", "\"Favorites\"" in seriesSource)
        assertTrue(
            "Provider categories must remain represented in the TV section rail.",
            "SERIES_CATEGORY_PREFIX + category.providerCategoryKey" in seriesSource,
        )
        assertFalse("The removed All section must not be introduced.", "\"All Series\"" in seriesSource)
        assertFalse("The removed All key must not be introduced.", "SERIES_ALL_KEY" in seriesSource)
        assertTrue(
            "A neutral Series fallback may exist only when provider categories are unavailable.",
            "if (catalog.categories.isEmpty() && catalog.series.isNotEmpty())" in seriesSource &&
                "TvSeriesSection(SERIES_CATALOG_KEY, \"Series\")" in seriesSource,
        )
        assertFalse("TV Series must not expose Offline download models.", "OfflineDownload" in seriesSource)
        assertFalse("TV Series must not instantiate download runtime.", "downloadRuntime" in seriesSource)
        assertFalse("TV Series must not render a Download action.", "\"Download\"" in seriesSource)
    }

    @Test
    fun `series details preserve seasons episodes continue and start from beginning`() {
        assertTrue("Series details must expose Seasons.", "text = \"Seasons\"" in seriesSource)
        assertTrue("Series details must expose Episodes.", "text = \"Episodes\"" in seriesSource)
        assertTrue(
            "A resume-capable episode must be selected from real saved progress.",
            ".filter(SeriesEpisode::resumeAvailable)" in seriesSource,
        )
        assertTrue(
            "Series details must expose Start from Episode 1.",
            "label = \"Start from Episode 1\"" in seriesSource,
        )
        assertTrue(
            "Starting from the beginning must remove only the playback snapshot resume position.",
            "episode.copy( positionMs = null, progressCompleted = false" in seriesSource,
        )
        assertFalse(
            "Start from Episode 1 must not prematurely delete persisted progress.",
            "clearEpisodeProgress(" in seriesSource,
        )
    }

    @Test
    fun `series detail back restores the originating series focus`() {
        assertTrue(
            "Series details must register Back with the established interaction bridge.",
            "PlaybackInteractionBridge.registerBackAction(detailsBackOwner, ::closeSeriesDetails)" in seriesSource,
        )
        assertTrue("Closing details must remember the originating series.", "val originSeriesId = selectedSeries?.seriesId" in seriesSource)
        assertTrue(
            "Closing details must arm explicit series focus restoration.",
            "seriesFocusTargetId = originSeriesId seriesFocusRequestGeneration += 1" in seriesSource,
        )
        assertTrue(
            "Focus restoration must scroll the series into view before requesting focus.",
            "gridState.scrollToItem(index)" in seriesSource && "seriesFocusRequester.requestFocus()" in seriesSource,
        )
    }

    @Test
    fun `series focus changes use stable geometry`() {
        assertTrue("Series cards must track focus explicitly.", ".onFocusChanged { focused = it.isFocused }" in seriesSource)
        assertFalse("Focused Series must not scale.", ".scale(" in seriesSource)
        assertFalse("Focused Series must not animate card size.", "animateContentSize" in seriesSource)
    }

    @Test
    fun `continue watching uses episode progress and established series playback request`() {
        assertTrue("Continue Watching must use stored episode rows.", "catalog.continueWatching" in seriesSource)
        assertTrue("Series playback must remain a SERIES_EPISODE request.", "mediaKind = PlaybackMediaKind.SERIES_EPISODE" in seriesSource)
        assertTrue("Provider episode id must remain attached to playback.", "providerStreamId = episode.providerEpisodeId" in seriesSource)
        assertTrue("Episode container extension must remain attached to playback.", "containerExtension = episode.containerExtension" in seriesSource)
    }
}
