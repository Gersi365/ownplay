package app.ownplay.player.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerBrowseNoAggregateCategoryRegressionTest {
    @Test
    fun `Live exposes only real provider categories`() {
        val liveRoute = source("src/main/java/app/ownplay/player/ui/LiveRoute.kt")
        val liveViews = source("src/main/java/app/ownplay/player/ui/live/PortraitLiveViewModes.kt")

        assertTrue(liveRoute.contains("categories.first().providerCategoryKey"))
        assertFalse(liveViews.contains("Text(\"All\")"))
        assertFalse(liveViews.contains("All groups"))
    }

    @Test
    fun `Library defaults to Offline and has no aggregate category control`() {
        val library = source("src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt")

        assertTrue(library.contains("mutableStateOf(true)"))
        assertTrue(library.contains("UnifiedLibraryFilter.ALL -> \"Offline\""))
        assertFalse(library.contains("All categories"))
    }

    @Test
    fun `Movies and Series select real categories without All controls`() {
        val movies = source("src/main/java/app/ownplay/player/ui/vod/VodRoute.kt")
        val series = source("src/main/java/app/ownplay/player/ui/series/SeriesRoute.kt")

        assertTrue(movies.contains("categories.first().providerCategoryKey"))
        assertFalse(movies.contains("label = { Text(\"All\") }"))
        assertFalse(movies.contains("All Movies"))

        assertTrue(series.contains("categories.first().providerCategoryKey"))
        assertFalse(series.contains("label = { Text(\"All\") }"))
    }

    private fun source(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Source file not found: $relativeToApp")
        return file.readText()
    }
}
