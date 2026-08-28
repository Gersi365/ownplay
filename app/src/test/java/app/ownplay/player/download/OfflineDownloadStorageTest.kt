package app.ownplay.player.download

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadStorageTest {
    @Test
    fun contentUriIsRecognizedAsPhoneDownloadsLocation() {
        assertTrue(
            OfflineDownloadStorage.isPublicDownloadsLocation(
                "content://media/external/downloads/42",
            ),
        )
        assertFalse(OfflineDownloadStorage.isPublicDownloadsLocation("offline/movie.mp4"))
        assertFalse(OfflineDownloadStorage.isPublicDownloadsLocation(null))
    }

    @Test
    fun publicFilenameStemRemovesFilesystemSeparatorsAndReservedCharacters() {
        assertEquals(
            "Movie Name Final",
            OfflineDownloadStorage.safeFileStem("Movie/Name:*?\"<>| Final"),
        )
    }

    @Test
    fun extensionNormalizationDoesNotDependOnDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))

            assertEquals("avi", OfflineDownloadStorage.normalizeExtension(" AVI "))
            assertEquals("mp4", OfflineDownloadStorage.normalizeExtension("bad.extension"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
