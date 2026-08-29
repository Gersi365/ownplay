package app.ownplay.player.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncMergePolicyTest {
    private val channelKey = SyncChannelKey(
        syncSourceId = "source-1",
        providerKey = "xtream:live:42",
    )

    @Test
    fun `newer field value wins independently`() {
        val older = SyncChannelState(
            key = channelKey,
            hidden = SyncValue(
                value = true,
                clock = clock(time = 100L, revision = 1L, device = "phone"),
            ),
            manualOrder = SyncValue(
                value = 7L,
                clock = clock(time = 300L, revision = 1L, device = "phone"),
            ),
        )
        val newer = SyncChannelState(
            key = channelKey,
            hidden = SyncValue(
                value = false,
                clock = clock(time = 200L, revision = 1L, device = "tv"),
            ),
            manualOrder = SyncValue(
                value = 2L,
                clock = clock(time = 250L, revision = 1L, device = "tv"),
            ),
        )

        val merged = DeviceSyncMergePolicy.mergeChannel(older, newer)

        assertEquals(false, merged.hidden?.value)
        assertEquals(7L, merged.manualOrder?.value)
    }

    @Test
    fun `tombstone can remove older favorite`() {
        val favorite = SyncValue(
            value = SyncFavoriteState(order = 1L, addedAtEpochMillis = 50L),
            clock = clock(time = 100L, revision = 1L, device = "phone"),
        )
        val removed = SyncValue<SyncFavoriteState>(
            value = null,
            clock = clock(time = 200L, revision = 1L, device = "tv"),
        )

        val merged = DeviceSyncMergePolicy.mergeChannel(
            SyncChannelState(key = channelKey, favorite = favorite),
            SyncChannelState(key = channelKey, favorite = removed),
        )

        assertNull(merged.favorite?.value)
        assertEquals(200L, merged.favorite?.clock?.updatedAtEpochMillis)
    }

    @Test
    fun `same timestamp and revision resolve identically by device id`() {
        val phone = SyncValue(
            value = true,
            clock = clock(time = 100L, revision = 4L, device = "phone"),
        )
        val tv = SyncValue(
            value = false,
            clock = clock(time = 100L, revision = 4L, device = "tv"),
        )

        val leftToRight = DeviceSyncMergePolicy.newer(phone, tv)
        val rightToLeft = DeviceSyncMergePolicy.newer(tv, phone)

        assertEquals(leftToRight, rightToLeft)
        assertEquals(false, leftToRight?.value)
    }

    @Test
    fun `newer encrypted source secret wins without exposing plaintext`() {
        val phone = source(
            fingerprint = "fingerprint-1",
            secretRef = SyncValue(
                value = "ciphertext-ref-old",
                clock = clock(100L, 1L, "phone"),
            ),
        )
        val tv = source(
            fingerprint = "fingerprint-1",
            secretRef = SyncValue(
                value = "ciphertext-ref-new",
                clock = clock(200L, 1L, "tv"),
            ),
        )

        val merged = DeviceSyncMergePolicy.mergeSource(phone, tv)

        assertEquals("ciphertext-ref-new", merged.encryptedSecretRef?.value)
    }

    @Test
    fun `same sync source id rejects conflicting locator fingerprint`() {
        val phone = source(fingerprint = "fingerprint-a")
        val tv = source(fingerprint = "fingerprint-b")

        val result = runCatching { DeviceSyncMergePolicy.mergeSource(phone, tv) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `duplicate manual positions remain deterministically ordered`() {
        val channelB = SyncChannelState(
            key = SyncChannelKey("source-1", "channel-b"),
            manualOrder = SyncValue(1L, clock(100L, 1L, "phone")),
        )
        val channelA = SyncChannelState(
            key = SyncChannelKey("source-1", "channel-a"),
            manualOrder = SyncValue(1L, clock(100L, 1L, "tv")),
        )
        val unordered = SyncChannelState(
            key = SyncChannelKey("source-1", "channel-c"),
        )

        val ordered = DeviceSyncMergePolicy.orderedChannels(
            listOf(channelB, unordered, channelA),
        )

        assertEquals(
            listOf("channel-a", "channel-b", "channel-c"),
            ordered.map { it.key.providerKey },
        )
    }

    private fun source(
        fingerprint: String?,
        secretRef: SyncValue<String>? = null,
    ) = SyncSourceState(
        identity = SyncSourceIdentity(
            syncSourceId = "source-1",
            sourceKind = "xtream",
            locatorFingerprint = fingerprint,
        ),
        displayName = SyncValue("Home TV", clock(100L, 1L, "phone")),
        enabled = SyncValue(true, clock(100L, 1L, "phone")),
        deleted = SyncValue(false, clock(100L, 1L, "phone")),
        encryptedSecretRef = secretRef,
    )

    private fun clock(
        time: Long,
        revision: Long,
        device: String,
    ) = SyncClock(
        updatedAtEpochMillis = time,
        revision = revision,
        deviceId = device,
    )
}
