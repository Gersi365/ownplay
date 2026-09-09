package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvEpgVisualIsolationContractTest {
    private val source by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/EpgGuideSheet.kt"),
        )
    }

    @Test
    fun `tv full epg uses a dedicated ownplay overlay before the mobile sheet path`() {
        assertTrue(
            "TV must branch into the dedicated full-screen EPG presentation.",
            "if (isTelevision) { TvEpgGuideOverlay(" in source,
        )
        assertTrue(
            "TV EPG must use a full-width dialog surface instead of platform sheet geometry.",
            "usePlatformDefaultWidth = false" in source &&
                "MaterialTheme.colorScheme.background.copy(alpha = 0.97f)" in source,
        )

        val tvBlock = source
            .substringAfter("private fun TvEpgGuideOverlay(")
            .substringBefore("private fun TvGuideDetailPane(")
        assertFalse(
            "The dedicated TV EPG overlay must not embed the touch-derived bottom sheet.",
            "ModalBottomSheet(" in tvBlock,
        )
        assertFalse("TV EPG focus must not scale layout geometry.", ".scale(" in tvBlock)
        assertFalse("TV EPG rows must not animate their size on focus.", "animateContentSize" in tvBlock)
    }

    @Test
    fun `mobile keeps its existing bottom sheet independently of tv presentation`() {
        assertTrue(
            "The mobile EPG presentation may retain its existing bottom sheet.",
            "ModalBottomSheet(" in source,
        )
        assertTrue(
            "TV-selected programme state must not force a mobile details dialog on sheet open.",
            "isTelevision && (selectedProgram == null || selectedProgram !in timeline.programs)" in source,
        )
    }

    @Test
    fun `tv epg exposes stable focus and contextual detail without another modal`() {
        assertTrue(
            "Focused TV programme rows must use the OwnPlay primary container.",
            "focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)" in source,
        )
        assertTrue(
            "Programme focus must update the stable contextual detail pane.",
            "onFocused = { onProgramFocused(program) }" in source &&
                "TvGuideDetailPane(" in source,
        )
        assertTrue(
            "Back or Done must dismiss the full guide without replacing playback.",
            "dismissOnBackPress = true" in source && "Text(\"Done\")" in source,
        )
    }
}
