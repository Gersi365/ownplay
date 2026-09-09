package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistFormPresentationIsolationContractTest {
    private val screen by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )
    }
    private val controls by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistFormComponents.kt"),
        )
    }

    @Test
    fun `tv playlist forms use fixed tv presentation controls`() {
        assertTrue("TV forms must use the dedicated TV text field.", "TvPlaylistTextField(" in screen)
        assertTrue("TV forms must use OK-toggle rows.", "TvPlaylistToggleRow(" in screen)
        assertTrue("TV forms must use fixed TV actions.", "TvPlaylistFormAction(" in screen)
        assertTrue("Delete confirmation must use the TV confirmation surface.", "TvPlaylistConfirmDialog(" in screen)
        assertTrue("TV text entry must delegate to BasicTextField for platform IME behavior.", "BasicTextField(" in controls)
        assertTrue("TV confirmation must be a dedicated Compose Dialog surface.", "Dialog(" in controls)
    }

    @Test
    fun `tv playlist forms exclude touch oriented material primitives`() {
        val combined = "$screen $controls"
        assertFalse("TV Playlists must not use OutlinedTextField.", "OutlinedTextField(" in combined)
        assertFalse("TV Playlists must not use Checkbox.", "Checkbox(" in combined)
        assertFalse("TV Playlists must not use AlertDialog.", "AlertDialog(" in combined)
        assertFalse("TV Playlists must not use Material Button.", "Button(" in combined)
        assertFalse("TV Playlists must not use OutlinedButton.", "OutlinedButton(" in combined)
        assertFalse("TV Playlists must not use TextButton.", "TextButton(" in combined)
        assertFalse("TV Playlists must not depend on pointer gestures.", "pointerInput(" in combined)
    }

    @Test
    fun `tv playlist focus emphasis preserves geometry`() {
        val combined = "$screen $controls"
        assertFalse("Focused playlist controls must not scale.", ".scale(" in combined)
        assertFalse("Focused playlist controls must not animate size.", "animateContentSize" in combined)
        assertTrue("Navigation rows must keep fixed height.", ".height(72.dp)" in screen)
        assertTrue("Text entry surfaces must keep fixed height.", ".height(78.dp)" in controls)
        assertTrue("Form actions must keep fixed height.", ".height(64.dp)" in controls)
    }
}
