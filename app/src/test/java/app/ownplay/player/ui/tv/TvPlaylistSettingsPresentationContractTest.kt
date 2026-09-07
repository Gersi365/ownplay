package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistSettingsPresentationContractTest {
    @Test
    fun `television playlist management routes to dedicated tv presentation`() {
        val router = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/SettingsPlaylist.kt"),
        )

        assertTrue(
            "Television playlist management must route to the TV-specific presentation.",
            "if (isTelevision) { TvPlaylistSettingsScreen(" in router,
        )
        assertTrue(
            "The TV route must return before the generic playlist presentation is composed.",
            router.indexOf("TvPlaylistSettingsScreen(") in 0 until router.indexOf("PlaylistSettingsScreen("),
        )
    }

    @Test
    fun `tv playlists use dedicated pages instead of generic add edit dialogs`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue("TV Playlists must expose a dedicated source list page.", "TvPlaylistPage.List" in source)
        assertTrue("TV Playlists must expose a dedicated source detail page.", "TvPlaylistPage.Detail" in source)
        assertTrue("TV Playlists must expose a dedicated source-type page.", "TvPlaylistPage.AddType" in source)
        assertTrue("TV Playlists must use a full-page Add form.", "private fun TvPlaylistAddForm(" in source)
        assertTrue("TV Playlists must use a full-page Edit form.", "private fun TvPlaylistEditForm(" in source)
        assertFalse("TV Add must not reuse the generic Add dialog.", "AddPlaylistDialog(" in source)
        assertFalse("TV Edit must not reuse the generic Edit dialog.", "EditPlaylistDialog(" in source)
    }

    @Test
    fun `tv playlist list opens one source action page instead of dense inline actions`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "Source rows must open a dedicated detail destination.",
            "page = TvPlaylistPage.Detail(sourceId)" in source,
        )
        assertTrue(
            "Source actions must include Live, refresh, edit and delete through the detail page.",
            listOf(
                "TvPlaylistDetailAction.OPEN_LIVE",
                "TvPlaylistDetailAction.REFRESH",
                "TvPlaylistDetailAction.EDIT",
                "TvPlaylistDetailAction.DELETE",
            ).all { it in source },
        )
    }

    @Test
    fun `tv playlist entry focus starts on add playlist`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "The stable initial list focus key must be Add playlist.",
            "listRestoreKey by remember { mutableStateOf(ADD_PLAYLIST_FOCUS_KEY) }" in source,
        )
        assertTrue(
            "The Add playlist row must have an explicit focus requester.",
            "addFocusRequester.requestFocus()" in source,
        )
    }
}
