package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvOwnPlayVisualIdentityContractTest {
    private val theme by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/theme/Theme.kt"),
        )
    }

    @Test
    fun `tv build uses the dedicated ownplay dark navy and cyan palette from startup`() {
        assertTrue("TV must own a dedicated visual palette.", "OwnPlayTvDarkColors = darkColorScheme(" in theme)
        assertTrue("OwnPlay cyan must remain the primary TV accent.", "primary = Color(0xFF18C7FF)" in theme)
        assertTrue("TV background must remain dark navy.", "background = Color(0xFF030B14)" in theme)
        assertTrue("TV surfaces must remain in the same dark navy family.", "surface = Color(0xFF07131F)" in theme)
        assertTrue(
            "The TV palette must be selected by the build identity even before the device profile finishes loading.",
            "activeColors = if (BuildConfig.IS_TV_BUILD) OwnPlayTvDarkColors else OwnPlayDarkColors" in theme,
        )
        assertTrue(
            "Remote focus indication must use the same active OwnPlay accent.",
            "focusColor = activeColors.primary" in theme,
        )
    }

    @Test
    fun `legacy purple does not leak into the tv palette`() {
        val tvPalette = theme
            .substringAfter("private val OwnPlayTvDarkColors")
            .substringBefore("private val OwnPlayShapes")

        assertFalse(
            "The old purple primary must not be part of the TV palette.",
            "0xFF9B7BFF" in tvPalette,
        )
        assertFalse(
            "The old purple container must not be part of the TV palette.",
            "0xFF2A1F45" in tvPalette,
        )
    }

    @Test
    fun `primary tv shell keeps the approved ownplay labels and brand`() {
        val destinations = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvDestination.kt"),
        )
        val shell = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )

        assertTrue("OwnPlay branding must remain visible in the TV rail.", "\"OwnPlay\"" in shell)
        assertTrue("Home must remain a primary destination.", "label = \"Home\"" in destinations)
        assertTrue("Live must use the approved concise TV label.", "label = \"Live\"" in destinations)
        assertTrue("Movies must remain a primary destination.", "label = \"Movies\"" in destinations)
        assertTrue("Series must remain a primary destination.", "label = \"Series\"" in destinations)
        assertTrue("Settings must remain a primary destination.", "label = \"Settings\"" in destinations)
    }
}
