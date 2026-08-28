package app.ownplay.player.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSourceSummaryDaoTest {
    private lateinit var database: OwnPlayDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OwnPlayDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun summaryCountsOnlyActiveChannelsAndPrefersAuthoritativeRefreshState() = runBlocking {
        database.playlistSourceDao().upsert(source("source"))
        database.providerCatalogDao().upsertChannels(
            listOf(
                channel(
                    channelId = "available",
                    sourceId = "source",
                    availability = ChannelAvailability.AVAILABLE,
                    generation = 100L,
                ),
                channel(
                    channelId = "removed",
                    sourceId = "source",
                    availability = ChannelAvailability.REMOVED,
                    generation = 900L,
                ),
            ),
        )
        database.refreshStateDao().upsert(
            PlaylistRefreshStateEntity(
                sourceId = "source",
                generation = 1_000L,
                state = RefreshStates.SUCCEEDED,
                lastAttemptAtEpochMillis = 1_000L,
                lastSuccessAtEpochMillis = 1_000L,
            ),
        )

        assertEquals(1, database.providerCatalogDao().activeChannelCount("source"))
        val summary = database.playlistSourceDao().observeSummaries().first().single()
        assertEquals(1, summary.channelCount)
        assertEquals(1_000L, summary.lastLiveRefreshAtEpochMillis)
    }

    @Test
    fun successfulEmptyCatalogStillHasLastRefreshTimestamp() = runBlocking {
        database.playlistSourceDao().upsert(source("empty"))
        database.refreshStateDao().upsert(
            PlaylistRefreshStateEntity(
                sourceId = "empty",
                generation = 2_000L,
                state = RefreshStates.SUCCEEDED,
                lastAttemptAtEpochMillis = 2_000L,
                lastSuccessAtEpochMillis = 2_000L,
            ),
        )

        val summary = database.playlistSourceDao().observeSummaries().first().single()
        assertEquals(0, summary.channelCount)
        assertEquals(2_000L, summary.lastLiveRefreshAtEpochMillis)
    }

    @Test
    fun existingDatabaseWithoutRefreshStateFallsBackToChannelGeneration() = runBlocking {
        database.playlistSourceDao().upsert(source("legacy"))
        database.providerCatalogDao().upsertChannels(
            listOf(
                channel(
                    channelId = "legacy-channel",
                    sourceId = "legacy",
                    availability = ChannelAvailability.AVAILABLE,
                    generation = 3_000L,
                ),
            ),
        )

        val summary = database.playlistSourceDao().observeSummaries().first().single()
        assertEquals(1, summary.channelCount)
        assertEquals(3_000L, summary.lastLiveRefreshAtEpochMillis)
    }

    private fun source(sourceId: String) = PlaylistSourceEntity(
        sourceId = sourceId,
        name = sourceId,
        sourceKind = SourceKinds.XTREAM,
        locatorRef = "locator-$sourceId",
        credentialRef = "credential-$sourceId",
        enabled = true,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun channel(
        channelId: String,
        sourceId: String,
        availability: String,
        generation: Long,
    ) = ProviderChannelEntity(
        channelId = channelId,
        sourceId = sourceId,
        providerKey = "provider-$channelId",
        providerName = channelId,
        streamLocatorRef = "stream-$channelId",
        providerOrder = 0L,
        availability = availability,
        lastSeenGeneration = generation,
    )
}
