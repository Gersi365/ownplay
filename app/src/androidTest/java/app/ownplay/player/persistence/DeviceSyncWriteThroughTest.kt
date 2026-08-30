package app.ownplay.player.persistence

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownplay.player.personalization.ChannelVisibilityMutationResult
import app.ownplay.player.personalization.ChannelVisibilityMutator
import app.ownplay.player.personalization.CustomGroupMutationResult
import app.ownplay.player.personalization.CustomGroupMutator
import app.ownplay.player.personalization.FavoriteChannelMutator
import app.ownplay.player.personalization.FavoriteMutationResult
import app.ownplay.player.personalization.ManualChannelOrderMutator
import app.ownplay.player.personalization.ManualOrderMutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSyncWriteThroughTest {
    @Test
    fun personalizationMutationsRemainLocalWhileDeviceSyncIsDeferred() = runBlocking {
        val database = createDatabase()
        try {
            seedSourceWithChannels(database, "source-1", "One", "channel-1", "provider-1")
            database.providerCatalogDao().upsertChannels(
                listOf(
                    ProviderChannelEntity(
                        channelId = "channel-2",
                        sourceId = "source-1",
                        providerKey = "provider-2",
                        providerName = "Two",
                        streamLocatorRef = "stream-2",
                        providerOrder = 1,
                        lastSeenGeneration = 1,
                    ),
                ),
            )

            val visibility = ChannelVisibilityMutator(database)
            assertTrue(
                visibility.hide("source-1", setOf("channel-1"), 100L) is
                    ChannelVisibilityMutationResult.Success,
            )

            val favorites = FavoriteChannelMutator(database)
            assertTrue(
                favorites.addFavorites(
                    sourceId = "source-1",
                    channelIds = setOf("channel-1", "channel-2"),
                    addedAtEpochMillis = 200L,
                ) is FavoriteMutationResult.Success,
            )

            val order = ManualChannelOrderMutator(database)
            assertTrue(
                order.move("source-1", "channel-2", 0) is ManualOrderMutationResult.Success,
            )

            val groups = CustomGroupMutator(database)
            assertTrue(
                groups.createGroup(
                    name = "Local",
                    createdAtEpochMillis = 300L,
                    groupId = "group-1",
                ) is CustomGroupMutationResult.Success,
            )
            assertTrue(
                groups.addChannels("source-1", "group-1", setOf("channel-1")) is
                    CustomGroupMutationResult.Success,
            )

            assertTrue(database.personalizationDao().hiddenEntriesForSource("source-1").isNotEmpty())
            assertEquals(2, database.personalizationDao().favoriteEntriesForSource("source-1").size)
            assertTrue(database.personalizationDao().customGroupsForMutation().isNotEmpty())

            assertNull(database.deviceSyncDao().localState())
            assertNull(database.deviceSyncDao().sourceByLocalId("source-1"))
            assertNull(database.deviceSyncDao().channel("source-1", "provider-1"))
            assertTrue(database.deviceSyncDao().allGroups().isEmpty())
            assertTrue(database.deviceSyncDao().membershipsForGroup("group-1").isEmpty())
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): OwnPlayDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, OwnPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun seedSourceWithChannels(
        database: OwnPlayDatabase,
        sourceId: String,
        sourceName: String,
        channelId: String,
        providerKey: String,
    ) {
        database.playlistSourceDao().upsert(
            PlaylistSourceEntity(
                sourceId = sourceId,
                name = sourceName,
                sourceKind = SourceKinds.REMOTE_M3U,
                locatorRef = "locator-$sourceId",
                enabled = true,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
        database.providerCatalogDao().upsertChannels(
            listOf(
                ProviderChannelEntity(
                    channelId = channelId,
                    sourceId = sourceId,
                    providerKey = providerKey,
                    providerName = channelId,
                    streamLocatorRef = "stream-$channelId",
                    providerOrder = 0,
                    lastSeenGeneration = 1,
                ),
            ),
        )
    }
}
