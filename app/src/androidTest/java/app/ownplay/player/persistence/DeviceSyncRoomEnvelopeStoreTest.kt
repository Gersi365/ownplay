package app.ownplay.player.persistence

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownplay.player.persistence.sync.DeviceSyncDeferredReason
import app.ownplay.player.persistence.sync.DeviceSyncLocalStateEntity
import app.ownplay.player.persistence.sync.DeviceSyncRoomEnvelopeStore
import app.ownplay.player.sync.DeviceSyncEnvelope
import app.ownplay.player.sync.SyncChannelKey
import app.ownplay.player.sync.SyncChannelState
import app.ownplay.player.sync.SyncClock
import app.ownplay.player.sync.SyncFavoriteState
import app.ownplay.player.sync.SyncGroupKey
import app.ownplay.player.sync.SyncGroupMembershipKey
import app.ownplay.player.sync.SyncGroupMembershipState
import app.ownplay.player.sync.SyncGroupState
import app.ownplay.player.sync.SyncSourceIdentity
import app.ownplay.player.sync.SyncSourceState
import app.ownplay.player.sync.SyncValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSyncRoomEnvelopeStoreTest {
    @Test
    fun mergedStateMaterializesThenTombstonesRemoveLocalPersonalization() = runBlocking {
        val database = createDatabase()
        try {
            seedLocalState(database)
            seedSourceWithChannel(database)
            seedSourceSyncMapping(database)
            val store = DeviceSyncRoomEnvelopeStore(database, now = { 9_000L })

            val first = store.applyMergedEnvelope(
                envelope(
                    channel = SyncChannelState(
                        key = SyncChannelKey("source-1", "provider-1"),
                        localDisplayName = SyncValue("Remote name", remoteClock(2_000L, 2L)),
                        manualOrder = SyncValue(7L, remoteClock(2_100L, 3L)),
                        hidden = SyncValue(true, remoteClock(2_200L, 4L)),
                        favorite = SyncValue(
                            SyncFavoriteState(order = 3L, addedAtEpochMillis = 1_500L),
                            remoteClock(2_300L, 5L),
                        ),
                    ),
                    group = activeGroup(),
                    membership = SyncGroupMembershipState(
                        key = membershipKey(),
                        order = SyncValue(4L, remoteClock(2_500L, 7L)),
                    ),
                ),
            )

            assertTrue(first.deferred.isEmpty())
            assertEquals(1, first.sourcesMaterialized)
            assertEquals(1, first.channelsMaterialized)
            assertEquals(1, first.groupsMaterialized)
            assertEquals(1, first.membershipsMaterialized)

            val source = requireNotNull(database.playlistSourceDao().getById("source-1"))
            assertEquals("Remote source", source.name)
            assertTrue(source.enabled)

            val customization = requireNotNull(
                database.personalizationDao().customizationForChannel("source-1", "channel-1"),
            )
            assertEquals("Remote name", customization.localDisplayName)
            assertEquals(7L, customization.manualOrder)

            assertEquals(
                listOf("channel-1"),
                database.personalizationDao().hiddenEntriesForSource("source-1").map { it.channelId },
            )
            val favorite = database.personalizationDao().favoriteEntriesForSource("source-1").single()
            assertEquals(3L, favorite.favoriteOrder)
            assertEquals(1_500L, favorite.addedAtEpochMillis)
            assertEquals(4L, database.personalizationDao().groupMemberships("group-1").single().groupOrder)

            val tombstones = store.applyMergedEnvelope(
                envelope(
                    channel = SyncChannelState(
                        key = SyncChannelKey("source-1", "provider-1"),
                        localDisplayName = SyncValue(null, remoteClock(3_000L, 8L)),
                        manualOrder = SyncValue(null, remoteClock(3_100L, 9L)),
                        hidden = SyncValue(false, remoteClock(3_200L, 10L)),
                        favorite = SyncValue(null, remoteClock(3_300L, 11L)),
                    ),
                    group = activeGroup().copy(
                        deleted = SyncValue(true, remoteClock(3_400L, 12L)),
                    ),
                    membership = SyncGroupMembershipState(
                        key = membershipKey(),
                        order = SyncValue(null, remoteClock(3_500L, 13L)),
                    ),
                ),
            )

            assertTrue(tombstones.deferred.isEmpty())
            val cleared = requireNotNull(
                database.personalizationDao().customizationForChannel("source-1", "channel-1"),
            )
            assertNull(cleared.localDisplayName)
            assertNull(cleared.manualOrder)
            assertTrue(database.personalizationDao().hiddenEntriesForSource("source-1").isEmpty())
            assertTrue(database.personalizationDao().favoriteEntriesForSource("source-1").isEmpty())
            assertNull(database.personalizationDao().customGroupById("group-1"))

            val syncChannel = requireNotNull(
                database.deviceSyncDao().channel("source-1", "provider-1"),
            )
            assertNull(syncChannel.localDisplayName)
            assertEquals(3_000L, syncChannel.localDisplayNameUpdatedAtEpochMillis)
            assertEquals(false, syncChannel.hidden)
            assertNull(syncChannel.favoriteOrder)
            assertEquals(3_300L, syncChannel.favoriteUpdatedAtEpochMillis)

            val roundTrip = store.readEnvelope()
            val roundTripChannel = roundTrip.channels.single()
            assertNull(roundTripChannel.localDisplayName?.value)
            assertEquals(false, roundTripChannel.hidden?.value)
            assertNull(roundTripChannel.favorite?.value)

            val localState = requireNotNull(database.deviceSyncDao().localState())
            assertEquals(5L, localState.nextRevision)
        } finally {
            database.close()
        }
    }

    @Test
    fun remoteSourceWithoutPortableSecretIsPersistedButDeferred() = runBlocking {
        val database = createDatabase()
        try {
            seedLocalState(database)
            val store = DeviceSyncRoomEnvelopeStore(database)
            val remoteSource = SyncSourceState(
                identity = SyncSourceIdentity("remote-source", SourceKinds.XTREAM),
                displayName = SyncValue("Remote only", remoteClock(1_000L, 1L)),
                enabled = SyncValue(true, remoteClock(1_000L, 1L)),
                deleted = SyncValue(false, remoteClock(1_000L, 1L)),
                encryptedSecretRef = SyncValue("encrypted-envelope-ref", remoteClock(1_100L, 2L)),
            )

            val result = store.applyMergedEnvelope(
                DeviceSyncEnvelope(
                    generatedAtEpochMillis = 2_000L,
                    deviceId = "remote-device",
                    sources = listOf(remoteSource),
                    channels = emptyList(),
                    groups = emptyList(),
                    memberships = emptyList(),
                ),
            )

            assertEquals(0, result.sourcesMaterialized)
            assertTrue(
                result.deferred.any {
                    it.key == "remote-source" &&
                        it.reason == DeviceSyncDeferredReason.SOURCE_SECRET_REQUIRED
                },
            )
            assertNull(database.playlistSourceDao().getById("remote-source"))
            val persisted = requireNotNull(database.deviceSyncDao().sourceBySyncId("remote-source"))
            assertNull(persisted.localSourceId)
            assertEquals("encrypted-envelope-ref", persisted.encryptedSecretRef)
        } finally {
            database.close()
        }
    }

    @Test
    fun sourceDeleteSoftDisablesLocalSourceAndKeepsSecurePurgeDeferred() = runBlocking {
        val database = createDatabase()
        try {
            seedLocalState(database)
            seedSourceWithChannel(database)
            seedSourceSyncMapping(database)
            val store = DeviceSyncRoomEnvelopeStore(database)

            val result = store.applyMergedEnvelope(
                envelope(
                    sourceDeleted = true,
                    channel = SyncChannelState(SyncChannelKey("source-1", "provider-1")),
                    group = activeGroup().copy(
                        deleted = SyncValue(true, remoteClock(2_600L, 8L)),
                    ),
                    membership = SyncGroupMembershipState(
                        key = membershipKey(),
                        order = SyncValue(null, remoteClock(2_700L, 9L)),
                    ),
                ),
            )

            val source = requireNotNull(database.playlistSourceDao().getById("source-1"))
            assertFalse(source.enabled)
            assertTrue(
                result.deferred.any {
                    it.key == "source-1" &&
                        it.reason == DeviceSyncDeferredReason.SOURCE_SECURE_PURGE_REQUIRED
                },
            )
            assertTrue(requireNotNull(database.deviceSyncDao().sourceBySyncId("source-1")).deleted)
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

    private suspend fun seedLocalState(database: OwnPlayDatabase) {
        database.deviceSyncDao().upsertLocalState(
            DeviceSyncLocalStateEntity(
                deviceId = "local-device",
                nextRevision = 5L,
                updatedAtEpochMillis = 500L,
            ),
        )
    }

    private suspend fun seedSourceWithChannel(database: OwnPlayDatabase) {
        database.playlistSourceDao().upsert(
            PlaylistSourceEntity(
                sourceId = "source-1",
                name = "Local source",
                sourceKind = SourceKinds.REMOTE_M3U,
                locatorRef = "local-locator-ref",
                enabled = true,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L,
            ),
        )
        database.providerCatalogDao().upsertChannels(
            listOf(
                ProviderChannelEntity(
                    channelId = "channel-1",
                    sourceId = "source-1",
                    providerKey = "provider-1",
                    providerName = "One",
                    streamLocatorRef = "stream-ref",
                    providerOrder = 0L,
                    lastSeenGeneration = 1L,
                ),
            ),
        )
    }

    private suspend fun seedSourceSyncMapping(database: OwnPlayDatabase) {
        val source = sourceState(sourceDeleted = false)
        val store = DeviceSyncRoomEnvelopeStore(database)
        store.applyMergedEnvelope(
            DeviceSyncEnvelope(
                generatedAtEpochMillis = 1_100L,
                deviceId = "remote-device",
                sources = listOf(source),
                channels = emptyList(),
                groups = emptyList(),
                memberships = emptyList(),
            ),
        )
        // The first generic apply cannot infer a local mapping for a never-seen sync source. Link the
        // legacy/local source explicitly, matching what migration/write-through does in production.
        val row = requireNotNull(database.deviceSyncDao().sourceBySyncId("source-1"))
        database.deviceSyncDao().upsertSource(row.copy(localSourceId = "source-1"))
    }

    private fun envelope(
        sourceDeleted: Boolean = false,
        channel: SyncChannelState,
        group: SyncGroupState,
        membership: SyncGroupMembershipState,
    ): DeviceSyncEnvelope = DeviceSyncEnvelope(
        generatedAtEpochMillis = 8_000L,
        deviceId = "remote-device",
        sources = listOf(sourceState(sourceDeleted)),
        channels = listOf(channel),
        groups = listOf(group),
        memberships = listOf(membership),
    )

    private fun sourceState(sourceDeleted: Boolean): SyncSourceState = SyncSourceState(
        identity = SyncSourceIdentity("source-1", SourceKinds.REMOTE_M3U),
        displayName = SyncValue("Remote source", remoteClock(1_000L, 1L)),
        enabled = SyncValue(true, remoteClock(1_100L, 2L)),
        deleted = SyncValue(sourceDeleted, remoteClock(1_200L, 3L)),
    )

    private fun activeGroup(): SyncGroupState = SyncGroupState(
        key = SyncGroupKey("group-1"),
        name = SyncValue("Remote group", remoteClock(2_400L, 6L)),
        groupOrder = SyncValue(2L, remoteClock(2_400L, 6L)),
        deleted = SyncValue(false, remoteClock(2_400L, 6L)),
    )

    private fun membershipKey(): SyncGroupMembershipKey = SyncGroupMembershipKey(
        groupKey = SyncGroupKey("group-1"),
        channelKey = SyncChannelKey("source-1", "provider-1"),
    )

    private fun remoteClock(time: Long, revision: Long) = SyncClock(
        updatedAtEpochMillis = time,
        revision = revision,
        deviceId = "remote-device",
    )
}
