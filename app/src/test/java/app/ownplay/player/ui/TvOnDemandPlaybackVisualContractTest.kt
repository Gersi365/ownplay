package app.ownplay.player.ui

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvOnDemandPlaybackVisualContractTest {
    private val controlsSource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/TvOnDemandPlaybackControls.kt"),
        )
    }
    private val sharedSurfaceSource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/OnDemandPlaybackSurface.kt"),
        )
    }

    @Test
    fun `tv on demand controls use stable OwnPlay actions without touch chrome`() {
        assertTrue("TV controls must use the OwnPlay theme.", "MaterialTheme.colorScheme.primaryContainer" in controlsSource)
        assertTrue("TV actions must keep a fixed height.", ".height(54.dp)" in controlsSource)
        assertTrue("TV progress must be informational rather than a touch slider.", ".fillMaxWidth(progress)" in controlsSource)
        assertFalse("TV controls must not use Slider.", "Slider(" in controlsSource)
        assertFalse("TV controls must not use IconButton.", "IconButton(" in controlsSource)
        assertFalse("TV controls must never scale focused actions.", ".scale(" in controlsSource)
    }

    @Test
    fun `series fullscreen routes television presentation through dedicated tv controls`() {
        assertTrue(
            "Shared on-demand playback must branch explicitly for television presentation.",
            "if (isTelevision) { TvOnDemandPlaybackControls(" in sharedSurfaceSource,
        )
        assertTrue(
            "TV remote wake behavior must remain available when controls are hidden.",
            "if (isTelevision && !controlsVisible)" in sharedSurfaceSource,
        )
        assertTrue(
            "TV playback must keep native PlayerView controls disabled.",
            "view.useController = false" in sharedSurfaceSource,
        )
    }
}
