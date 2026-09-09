package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPrimaryActionPresentationContractTest {
    private val actionSurface by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvActionSurface.kt"),
        )
    }

    @Test
    fun `shared tv action surface keeps fixed geometry and color driven focus`() {
        assertTrue("TV actions must keep the approved stable height.", ".height(64.dp)" in actionSurface)
        assertTrue("TV actions must retain a useful minimum remote target width.", ".widthIn(min = 132.dp)" in actionSurface)
        assertTrue("TV actions must observe focus explicitly.", ".onFocusChanged { focused = it.isFocused }" in actionSurface)
        assertTrue(
            "TV action focus must use the OwnPlay primary container.",
            "focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)" in actionSurface,
        )
        assertFalse("TV action focus must not scale geometry.", ".scale(" in actionSurface)
        assertFalse("TV action focus must not animate layout size.", "animateContentSize" in actionSurface)
        assertFalse("The TV action primitive must not wrap Material TextButton.", "TextButton(" in actionSurface)
        assertFalse("The TV action primitive must not wrap Material Button.", " Button(" in actionSurface)
    }

    @Test
    fun `primary tv routes do not render generic material text buttons`() {
        val tvSources = listOf(
            "src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt",
            "src/tv/java/app/ownplay/player/ui/home/TvHomeScreen.kt",
            "src/tv/java/app/ownplay/player/ui/movies/TvMoviesRoute.kt",
            "src/tv/java/app/ownplay/player/ui/series/TvSeriesRoute.kt",
            "src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt",
        ).map(::sourceText)

        tvSources.forEach { source ->
            assertFalse(
                "Primary TV presentation must not import Material TextButton.",
                "androidx.compose.material3.TextButton" in source,
            )
            assertFalse(
                "Primary TV presentation must not render generic TextButton.",
                "TextButton(" in source,
            )
        }
    }

    @Test
    fun `tv epg done action uses ownplay action surface while mobile stays independent`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/EpgGuideSheet.kt"),
        )
        val tvBlock = source
            .substringAfter("private fun TvEpgGuideOverlay(")
            .substringBefore("private fun TvGuideDetailPane(")

        assertTrue("TV EPG must expose its Done action through the OwnPlay TV primitive.", "TvActionSurface(" in tvBlock)
        assertTrue("TV EPG must retain the Done action.", "label = \"Done\"" in tvBlock)
        assertFalse("TV EPG must not render Material TextButton.", "TextButton(" in tvBlock)
        assertTrue("Mobile EPG must retain its independent bottom-sheet presentation.", "ModalBottomSheet(" in source)
        assertTrue("Mobile EPG may retain Material TextButton independently.", "TextButton(" in source)
    }

    @Test
    fun `series unavailable fallback is tv native without changing mobile fallback`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/series/SeriesRoute.kt"),
        )
        val fallback = source
            .substringAfter("private fun SeriesUnavailableState(")
            .substringBefore("private fun downloadProgressLabel(")

        assertTrue("The fallback must receive the device presentation decision.", "isTelevision: Boolean" in fallback)
        assertTrue("TV fallback must use the OwnPlay TV action primitive.", "if (isTelevision) { TvActionSurface(" in fallback)
        assertTrue("TV fallback must retain the Settings action.", "label = \"Open Settings\"" in fallback)
        assertTrue("Mobile fallback must remain independent.", "else { Button(" in fallback)
        assertTrue("SeriesRoute must pass its verified TV mode into the fallback.", "isTelevision = isTelevision" in source)
    }

    @Test
    fun `live no source action participates in shell focus transfer`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )
        val noSourceBlock = source
            .substringAfter("private fun TVNoSourceScreen(")
            .substringBefore("private fun TVConfigurationBoundary(")

        assertTrue("Live no-source must use the shared TV action primitive.", "TvActionSurface(" in noSourceBlock)
        assertTrue("Live no-source must observe shell content entry.", "shellFocusBoundary.contentEntryGeneration" in noSourceBlock)
        assertTrue("Live no-source must request focus on its action after content entry.", "actionFocusRequester.requestFocus()" in noSourceBlock)
        assertTrue("Left from the action must return to the shell rail when available.", "shellFocusBoundary.requestRailFocus()" in noSourceBlock)
    }
}
