package app.ownplay.player.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ownplay.player.persistence.series.ProviderSeriesEntity
import app.ownplay.player.persistence.series.ProviderSeriesEpisodeEntity
import app.ownplay.player.persistence.series.ProviderSeriesSeasonEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesCatalogRefreshPersistenceTest {
    private lateinit var database: OwnPlayDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OwnPlayDatabase::class.java)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshingSeriesParentPreservesLoadedSeasonsAndEpisodes() = runBlocking {
        database.playlistSourceDao().upsert(
            PlaylistSourceEntity(
                sourceId = "source",
                name = "Series Source",
                sourceKind = SourceKinds.XTREAM,
                locatorRef = "locator-ref",
                credentialRef = "credential-ref",
                enabled = true,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )

        val dao = database.seriesCatalogDao()
        dao.upsertSeries(
            listOf(
                seriesEntity(
                    name = "Series One",
                    generation = 10L,
                ),
            ),
        )
        dao.replaceDetails(
            seriesId = "series-1",
            seasons = listOf(
                ProviderSeriesSeasonEntity(
                    seasonId = "season-1",
                    seriesId = "series-1",
                    seasonNumber = 1,
                    name = "Season 1",
                ),
            ),
            episodes = listOf(
                ProviderSeriesEpisodeEntity(
                    episodeId = "episode-1",
                    seriesId = "series-1",
                    seasonId = "season-1",
                    providerEpisodeId = "episode-provider-1",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Pilot",
                    containerExtension = "mkv",
                ),
            ),
        )

        dao.upsertSeries(
            listOf(
                seriesEntity(
                    name = "Series One Updated",
                    generation = 20L,
                ),
            ),
        )

        assertEquals("Series One Updated", dao.series("source", "series-1")?.providerName)
        assertEquals(listOf("season-1"), dao.seasons("series-1").map { it.seasonId })
        assertEquals(listOf("episode-1"), dao.episodes("series-1").map { it.episodeId })
    }

    private fun seriesEntity(
        name: String,
        generation: Long,
    ) = ProviderSeriesEntity(
        seriesId = "series-1",
        sourceId = "source",
        providerSeriesId = "provider-series-1",
        providerCategoryKey = "category-1",
        providerName = name,
        posterRef = null,
        description = null,
        providerRating = null,
        lastModifiedEpochSeconds = null,
        providerOrder = 0L,
        lastSeenGeneration = generation,
    )
}
