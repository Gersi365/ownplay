package app.ownplay.player.ui.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDetailHierarchyRegressionTest {
    @Test
    fun tvDetailsRequestStandalonePresentationWithoutFakingDeviceConfiguration() {
        val routes = source("src/main/java/app/ownplay/player/ui/library/TvLibraryDetailRoutes.kt")

        assertTrue(routes.contains("standaloneDetailPresentation = true"))
        assertFalse(routes.contains("LocalConfiguration"))
        assertFalse(routes.contains("ORIENTATION_PORTRAIT"))
        assertFalse(routes.contains("CompositionLocalProvider"))
    }

    @Test
    fun movieRailCannotLeakIntoStandaloneDetailPresentation() {
        val movies = source("src/main/java/app/ownplay/player/ui/vod/VodRoute.kt")
        val series = source("src/main/java/app/ownplay/player/ui/series/SeriesRoute.kt")
        val mobileRoot = source("src/main/java/app/ownplay/player/ui/OwnPlayApp.kt")

        assertTrue(movies.contains("if (isLandscape && !standaloneDetailPresentation)"))
        assertTrue(series.contains("standaloneDetailPresentation || !isLandscape"))
        assertTrue(
            mobileRoot.contains("standaloneDetailPresentation = movieDetailReturnToLibrary"),
        )
        assertTrue(
            mobileRoot.contains("standaloneDetailPresentation = seriesDetailReturnToLibrary"),
        )
    }

    private fun source(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found: $relativeToApp")
    }
}
