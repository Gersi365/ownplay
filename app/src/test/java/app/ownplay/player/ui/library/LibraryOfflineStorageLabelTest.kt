package app.ownplay.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOfflineStorageLabelTest {
    @Test
    fun `public download uses phone downloads wording`() {
        assertEquals(
            "Local file · Phone Downloads",
            libraryOfflineStorageLabel(savedToDownloads = true),
        )
    }

    @Test
    fun `private download uses ownplay private storage wording`() {
        assertEquals(
            "Local file · OwnPlay private storage",
            libraryOfflineStorageLabel(savedToDownloads = false),
        )
    }
}
