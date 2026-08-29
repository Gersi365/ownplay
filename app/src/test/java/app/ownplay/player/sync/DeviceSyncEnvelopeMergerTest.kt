package app.ownplay.player.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSyncEnvelopeMergerTest {
    @Test
    fun `merge keeps disjoint channel changes and resolves shared fields`() {
        val sharedKey = SyncChannelKey("source-1", "channel-shared")
        val phoneOnlyKey = SyncChannelKey("source-1", "channel-phone")
        val tvOnlyKey = SyncChannelKey("source-1", "channel-tv")

        val phone = envelope(
            deviceId = "phone",
            channels = listOf(
                SyncChannelState(
                    key = sharedKey,
                    hidden = SyncValue(true, clock(100L, "phone")),
                    manualOrder = SyncValue(8L, clock(300L, "phone")),
                ),
                SyncChannelState(
                    key = phoneOnlyKey,
                    favorite = SyncValue(
                        SyncFavoriteState(order = 1L, addedAtEpochMillis = 50L),
                        clock(100L, "phone"),
                    ),
                ),
            ),
        )
        val tv = envelope(
            deviceId = "tv",
            channels = listOf(
                SyncChannelState(
                    key = sharedKey,
                    hidden = SyncValue(false, clock(200L, "tv")),
                    manualOrder = SyncValue(3L, clock(250L, "tv")),
                ),
                SyncChannelState(
                    key = tvOnlyKey,
                    localDisplayName = SyncValue("News", clock(100L, "tv")),
                ),
            ),
        )

        val merged = DeviceSyncEnvelopeMerger.merge(
            left = phone,
            right = tv,
            outputDeviceId = "phone",
            generatedAtEpochMillis = 500L,
        )

        assertEquals(3, merged.channels.size)
        val shared = merged.channels.single { it.key == sharedKey }
        assertEquals(false, shared.hidden?.value)
        assertEquals(8L, shared.manualOrder?.value)
        assertEquals("News", merged.channels.single { it.key == tvOnlyKey }.localDisplayName?.value)
    }

    @Test
    fun `membership tombstone survives full envelope merge`() {
        val membershipKey = SyncGroupMembershipKey(
            groupKey = SyncGroupKey("group-1"),
            channelKey = SyncChannelKey("source-1", "channel-1"),
        )
        val phone = envelope(
            deviceId = "phone",
            memberships = listOf(
                SyncGroupMembershipState(
                    key = membershipKey,
                    order = SyncValue(2L, clock(100L, "phone")),
                ),
            ),
        )
        val tv = envelope(
            deviceId = "tv",
            memberships = listOf(
                SyncGroupMembershipState(
                    key = membershipKey,
                    order = SyncValue<Long>(null, clock(200L, "tv")),
                ),
            ),
        )

        val merged = DeviceSyncEnvelopeMerger.merge(
            left = phone,
            right = tv,
            outputDeviceId = "tv",
            generatedAtEpochMillis = 500L,
        )

        assertNull(merged.memberships.single().order.value)
    }

    private fun envelope(
        deviceId: String,
        channels: List<SyncChannelState> = emptyList(),
        memberships: List<SyncGroupMembershipState> = emptyList(),
    ) = DeviceSyncEnvelope(
        generatedAtEpochMillis = 100L,
        deviceId = deviceId,
        sources = emptyList(),
        channels = channels,
        groups = emptyList(),
        memberships = memberships,
    )

    private fun clock(
        time: Long,
        deviceId: String,
    ) = SyncClock(
        updatedAtEpochMillis = time,
        revision = 1L,
        deviceId = deviceId,
    )
}
