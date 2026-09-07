package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistEditWorkingFocusContractTest {
    @Test
    fun `tv playlist edit disables all back activation while save is working`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "Remote Back must remain owned while the save mutation is working.",
            "BackHandler(enabled = working) { }" in source,
        )
        assertTrue(
            "The edit page must disable its visible Back control during the same mutation.",
            "onBack = onBack, backEnabled = !working" in source,
        )
        assertTrue(
            "The shared playlist scaffold must expose an enabled gate for its Back button.",
            "backEnabled: Boolean = true" in source &&
                "TextButton( onClick = onBack, enabled = backEnabled" in source,
        )
        assertTrue(
            "Save and Cancel must remain disabled while the mutation is working.",
            "Button( enabled = !working" in source &&
                "OutlinedButton( enabled = !working, onClick = onBack" in source,
        )
    }
}
