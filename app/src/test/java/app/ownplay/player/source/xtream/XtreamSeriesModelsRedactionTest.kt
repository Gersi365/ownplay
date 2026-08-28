package app.ownplay.player.source.xtream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamSeriesModelsRedactionTest {
    @Test
    fun seriesModelsDoNotExposeArtworkTokensOrDescriptions() {
        val episode = XtreamSeriesEpisode(
            episodeId = 1001,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot",
            containerExtension = "mkv",
            durationSeconds = 3_600L,
            description = "private episode description",
            posterUrl = "https://images.example/episode.jpg?token=episode-secret",
            rating = 8.2,
            addedAtEpochSeconds = 1_700_000_000L,
        )
        val info = XtreamSeriesInfo(
            seriesId = 501,
            name = "Series One",
            description = "private series description",
            posterUrl = "https://images.example/series.jpg?token=poster-secret",
            backdropUrls = listOf("https://images.example/backdrop.jpg?token=backdrop-secret"),
            releaseDate = "2026-01-01",
            genre = "Drama",
            country = "AL",
            director = "Director",
            cast = "Actor One, Actor Two",
            rating = 8.5,
            seasons = listOf(
                XtreamSeriesSeason(
                    seasonNumber = 1,
                    name = "Season 1",
                    airDate = "2026-01-01",
                    posterUrl = "https://images.example/season.jpg?token=season-secret",
                ),
            ),
            episodes = listOf(episode),
        )

        val rendered = listOf(
            episode.toString(),
            info.toString(),
            XtreamSeriesSummary(
                seriesId = 501,
                name = "Series One",
                categoryId = "10",
                posterUrl = "https://images.example/summary.jpg?token=summary-secret",
                rating = 8.5,
                lastModifiedEpochSeconds = 1_700_000_000L,
                description = "private summary description",
            ).toString(),
            info.seasons.single().toString(),
        ).joinToString("\n")

        assertFalse(rendered.contains("episode-secret"))
        assertFalse(rendered.contains("poster-secret"))
        assertFalse(rendered.contains("backdrop-secret"))
        assertFalse(rendered.contains("season-secret"))
        assertFalse(rendered.contains("summary-secret"))
        assertFalse(rendered.contains("private series description"))
        assertFalse(rendered.contains("private episode description"))
        assertTrue(rendered.contains("<redacted>"))
        assertTrue(rendered.contains("backdropUrls=<1 refs>"))
    }
}
