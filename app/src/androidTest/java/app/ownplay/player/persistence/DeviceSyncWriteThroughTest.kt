package app.ownplay.player.persistence

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownplay.player.personalization.ChannelVisibilityMutator
import app.ownplay.player.personalization.CustomGroupMutationResult
import app.ownplay.player.personalization.CustomGroupMutator
import app.ownplay.player.personalization.FavoriteChannelMutator
import app.ownplay.player.personalization.FavoriteMutationResult
import app.ownplay.player.personalization.ManualChannelOrderMutator
import app.ownplay.player.personalization.ManualOrderMutationResult
import app.ownplay.player.personalization.ChannelVisibilityMutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSyncWriteThroughTest {
    @Test
    fun personalizationMutationsWriteSyncStateAndTombstones() = runBlocking {
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
            assertTrue(
                visibility.unhide("source-1", setOf("channel-1")) is
                    ChannelVisibilityMutationResult.Success,
            )
            val hiddenSync = requireNotNull(database.deviceSyncDao().channel("source-1", "provider-1"))
            assertEquals(false, hiddenSync.hidden)
            assertNotNull(hiddenSync.hiddenUpdatedAtEpochMillis)

            val favorites = FavoriteChannelMutator(database)
            assertTrue(
                favorites.addFavorites(
                    sourceId = "source-1",
                    channelIds = setOf("channel-1", "channel-2"),
                    addedAtEpochMillis = 200L,
                ) is FavoriteMutationResult.Success,
            )
            assertTrue(
                favorites.removeFavorites(
                    sourceId = "source-1",
                    channelIds = setOf("channel-1"),
                ) is FavoriteMutationResult.Success,
            )
            val removedFavorite = requireNotNull(
                database.deviceSyncDao().channel("source-1", "provider-1"),
            )
            val remainingFavorite = requireNotNull(
                database.deviceSyncDao().channel("source-1", "provider-2"),
            )
            assertNull(removedFavorite.favoriteOrder)
            assertNull(removedFavorite.favoriteAddedAtEpochMillis)
            assertNotNull(removedFavorite.favoriteUpdatedAtEpochMillis)
            assertEquals(0L, remainingFavorite.favoriteOrder)

            val order = ManualChannelOrderMutator(database)
            assertTrue(
                order.move("source-1", "channel-2", 0) is ManualOrderMutationResult.Success,
            )
            assertEquals(
                0L,
                database.deviceSyncDao()
                    .channel("source-1", "provider-2")
                    ?.manualOrder,
            )

            val localState = requireNotNull(database.deviceSyncDao().localState())
            assertTrue(localState.nextRevision > 1L)
            assertTrue(localState.deviceId.isNotBlank())
        } finally {
            database.close()
        }
    }

    @Test
    fun mixedSourceGroupReindexWritesEachMembershipAgainstItsOwnSource() = runBlocking {
        val database = createDatabase()
        try {
            seedSourceWithChannels(database, "source-a", "A", "channel-a", "provider-a")
            seedSourceWithChannels(database, "source-b", "B", "channel-b", "provider-b")

            val groups = CustomGroupMutator(database)
            assertTrue(
                groups.createGroup(
                    name = "Mixed",
                    createdAtEpochMillis = 100L,
                    groupId = "group-1",
                ) is CustomGroupMutationResult.Success,
            )
            assertTrue(
                groups.addChannels("source-a", "group-1", setOf("channel-a")) is
                    CustomGroupMutationResult.Success,
            )
            assertTrue(
                groups.addChannels("source-b", "group-1", setOf("channel-b")) is
                    CustomGroupMutationResult.Success,
            )
            assertTrue(
                groups.removeChannels("source-a", "group-1", setOf("channel-a")) is
                    CustomGroupMutationResult.Success,
            )

            val memberships = database.deviceSyncDao().membershipsForGroup("group-1")
            val removed = memberships.single {
                it.syncSourceId == "source-a" && it.providerKey == "provider-a"
            }
            val remaining = memberships.single {
                it.syncSourceId == "source-b" && it.providerKey == "provider-b"
            }
            assertNull(removed.groupOrder)
            assertEquals(0L, remaining.groupOrder)
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
