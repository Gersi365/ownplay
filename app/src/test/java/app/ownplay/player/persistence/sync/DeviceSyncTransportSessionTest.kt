package app.ownplay.player.persistence.sync

import app.ownplay.player.sync.DeviceSyncEnvelope
import app.ownplay.player.sync.DeviceSyncPushRequest
import app.ownplay.player.sync.DeviceSyncPushResult
import app.ownplay.player.sync.DeviceSyncRemoteSnapshot
import app.ownplay.player.sync.DeviceSyncTransport
import app.ownplay.player.sync.SyncChannelKey
import app.ownplay.player.sync.SyncChannelState
import app.ownplay.player.sync.SyncClock
import app.ownplay.player.sync.SyncFavoriteState
import app.ownplay.player.sync.SyncSourceIdentity
import app.ownplay.player.sync.SyncSourceState
import app.ownplay.player.sync.SyncValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncTransportSessionTest {
    @Test
    fun phoneAndTvOfflineChangesConvergeThroughRelay() = runBlocking {
        val relay = InMemoryRelay()
        val phone = FakeDevice(
            envelope = envelope(
                deviceId = "phone",
                generatedAt = 1_000L,
                channel = SyncChannelState(
                    key = channelKey,
                    favorite = SyncValue(
                        value = SyncFavoriteState(order = 0L, addedAtEpochMillis = 900L),
                        clock = clock(1_000L, 1L, "phone"),
                    ),
                ),
            ),
        )
        val tv = FakeDevice(
            envelope = envelope(
                deviceId = "tv",
                generatedAt = 1_100L,
                channel = SyncChannelState(
                    key = channelKey,
                    hidden = SyncValue(true, clock(1_100L, 1L, "tv")),
                ),
            ),
        )

        DeviceSyncTransportSession(phone.coordinator, relay, requestIdFactory = ids("phone")).synchronize()
        DeviceSyncTransportSession(tv.coordinator, relay, requestIdFactory = ids("tv")).synchronize()
        DeviceSyncTransportSession(phone.coordinator, relay, requestIdFactory = ids("phone-final")).synchronize()

        val remoteChannel = requireNotNull(relay.pull().envelope).channels.single()
        val phoneChannel = phone.envelope.channels.single()
        val tvChannel = tv.envelope.channels.single()

        assertEquals(true, remoteChannel.hidden?.value)
        assertNotNull(remoteChannel.favorite?.value)
        assertEquals(remoteChannel, phoneChannel)
        assertEquals(remoteChannel, tvChannel)
    }

    @Test
    fun compareAndSetConflictPullsLatestMergesAndRetries() = runBlocking {
        val relay = InMemoryRelay()
        val seed = envelope(
            deviceId = "phone",
            generatedAt = 2_000L,
            channel = SyncChannelState(
                key = channelKey,
                favorite = SyncValue(
                    SyncFavoriteState(order = 0L, addedAtEpochMillis = 1_900L),
                    clock(2_000L, 2L, "phone"),
                ),
            ),
        )
        relay.push(
            DeviceSyncPushRequest(
                requestId = "seed",
                expectedRemoteRevision = 0L,
                envelope = seed,
            ),
        )

        val tv = FakeDevice(
            envelope = envelope(
                deviceId = "tv",
                generatedAt = 2_100L,
                channel = SyncChannelState(
                    key = channelKey,
                    hidden = SyncValue(true, clock(2_100L, 2L, "tv")),
                ),
            ),
        )
        val racingTransport = OneShotConcurrentWriteTransport(relay) {
            val current = relay.pull()
            val currentEnvelope = requireNotNull(current.envelope)
            val currentChannel = currentEnvelope.channels.single()
            val external = currentEnvelope.copy(
                generatedAtEpochMillis = 2_200L,
                deviceId = "phone",
                channels = listOf(
                    currentChannel.copy(
                        localDisplayName = SyncValue(
                            "Living Room",
                            clock(2_200L, 3L, "phone"),
                        ),
                    ),
                ),
            )
            val accepted = relay.push(
                DeviceSyncPushRequest(
                    requestId = "concurrent-phone-write",
                    expectedRemoteRevision = current.remoteRevision,
                    envelope = external,
                ),
            )
            assertTrue(accepted is DeviceSyncPushResult.Accepted)
        }

        val result = DeviceSyncTransportSession(
            coordinator = tv.coordinator,
            transport = racingTransport,
            maxPushAttempts = 3,
            requestIdFactory = ids("tv-race"),
        ).synchronize()

        assertEquals(2, result.attempts)
        val channel = requireNotNull(result.remoteSnapshot.envelope).channels.single()
        assertEquals(true, channel.hidden?.value)
        assertNotNull(channel.favorite?.value)
        assertEquals("Living Room", channel.localDisplayName?.value)
        assertEquals(channel, tv.envelope.channels.single())
    }

    @Test
    fun duplicateRequestIdIsIdempotent() = runBlocking {
        val relay = InMemoryRelay()
        val request = DeviceSyncPushRequest(
            requestId = "stable-request",
            expectedRemoteRevision = 0L,
            envelope = envelope(deviceId = "phone", generatedAt = 3_000L),
        )

        val first = relay.push(request)
        val second = relay.push(request)

        assertTrue(first is DeviceSyncPushResult.Accepted)
        assertEquals(first, second)
        assertEquals(1L, relay.pull().remoteRevision)
    }

    @Test
    fun newerUnfavoriteTombstoneWinsOverStaleFavorite() = runBlocking {
        val relay = InMemoryRelay()
        val staleFavorite = envelope(
            deviceId = "tv",
            generatedAt = 4_000L,
            channel = SyncChannelState(
                key = channelKey,
                favorite = SyncValue(
                    SyncFavoriteState(order = 0L, addedAtEpochMillis = 3_000L),
                    clock(4_000L, 4L, "tv"),
                ),
            ),
        )
        relay.push(
            DeviceSyncPushRequest(
                requestId = "stale-tv",
                expectedRemoteRevision = 0L,
                envelope = staleFavorite,
            ),
        )

        val phone = FakeDevice(
            envelope = envelope(
                deviceId = "phone",
                generatedAt = 4_500L,
                channel = SyncChannelState(
                    key = channelKey,
                    favorite = SyncValue(
                        value = null,
                        clock = clock(4_500L, 5L, "phone"),
                    ),
                ),
            ),
        )

        DeviceSyncTransportSession(phone.coordinator, relay, requestIdFactory = ids("unfavorite")).synchronize()

        val mergedFavorite = requireNotNull(relay.pull().envelope)
            .channels.single().favorite
        assertNotNull(mergedFavorite)
        assertNull(mergedFavorite?.value)
        assertEquals("phone", mergedFavorite?.clock?.deviceId)
    }

    private class FakeDevice(
        var envelope: DeviceSyncEnvelope,
    ) {
        val coordinator = DeviceSyncCoordinator(
            readLocalEnvelope = { envelope },
            applyMergedEnvelope = { merged ->
                envelope = merged
                DeviceSyncApplyResult(
                    sourcesMaterialized = 0,
                    channelsMaterialized = 0,
                    groupsMaterialized = 0,
                    membershipsMaterialized = 0,
                    deferred = emptyList(),
                )
            },
            now = { envelope.generatedAtEpochMillis + 1L },
        )
    }

    private class InMemoryRelay : DeviceSyncTransport {
        private var current = DeviceSyncRemoteSnapshot(remoteRevision = 0L, envelope = null)
        private val requests = mutableMapOf<String, Pair<DeviceSyncPushRequest, DeviceSyncPushResult>>()

        override suspend fun pull(): DeviceSyncRemoteSnapshot = current

        override suspend fun push(request: DeviceSyncPushRequest): DeviceSyncPushResult {
            requests[request.requestId]?.let { (previousRequest, previousResult) ->
                require(previousRequest == request) {
                    "A requestId cannot be reused for different sync payloads"
                }
                return previousResult
            }

            val result = if (request.expectedRemoteRevision != current.remoteRevision) {
                DeviceSyncPushResult.Conflict(current)
            } else {
                current = DeviceSyncRemoteSnapshot(
                    remoteRevision = current.remoteRevision + 1L,
                    envelope = request.envelope,
                )
                DeviceSyncPushResult.Accepted(current)
            }
            requests[request.requestId] = request to result
            return result
        }
    }

    private class OneShotConcurrentWriteTransport(
        private val relay: InMemoryRelay,
        private val beforeFirstPush: suspend () -> Unit,
    ) : DeviceSyncTransport {
        private var injected = false

        override suspend fun pull(): DeviceSyncRemoteSnapshot = relay.pull()

        override suspend fun push(request: DeviceSyncPushRequest): DeviceSyncPushResult {
            if (!injected) {
                injected = true
                beforeFirstPush()
            }
            return relay.push(request)
        }
    }

    companion object {
        private val channelKey = SyncChannelKey("source-1", "channel-1")

        private fun envelope(
            deviceId: String,
            generatedAt: Long,
            channel: SyncChannelState = SyncChannelState(key = channelKey),
        ): DeviceSyncEnvelope = DeviceSyncEnvelope(
            generatedAtEpochMillis = generatedAt,
            deviceId = deviceId,
            sources = listOf(source(deviceId)),
            channels = listOf(channel),
            groups = emptyList(),
            memberships = emptyList(),
        )

        private fun source(deviceId: String): SyncSourceState {
            val stableClock = clock(100L, 0L, deviceId)
            return SyncSourceState(
                identity = SyncSourceIdentity(
                    syncSourceId = "source-1",
                    sourceKind = "xtream",
                    locatorFingerprint = "same-locator",
                ),
                displayName = SyncValue("Source", stableClock),
                enabled = SyncValue(true, stableClock),
                deleted = SyncValue(false, stableClock),
            )
        }

        private fun clock(time: Long, revision: Long, deviceId: String) = SyncClock(
            updatedAtEpochMillis = time,
            revision = revision,
            deviceId = deviceId,
        )

        private fun ids(prefix: String): () -> String {
            var next = 0
            return { "$prefix-${next++}" }
        }
    }
}
