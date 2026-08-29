package app.ownplay.player.persistence.sync

import app.ownplay.player.sync.DeviceSyncEnvelope
import app.ownplay.player.sync.SyncChannelKey
import app.ownplay.player.sync.SyncChannelState
import app.ownplay.player.sync.SyncClock
import app.ownplay.player.sync.SyncFavoriteState
import app.ownplay.player.sync.SyncSourceIdentity
import app.ownplay.player.sync.SyncSourceState
import app.ownplay.player.sync.SyncValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncCoordinatorTest {
    @Test
    fun synchronizeReadsMergesAppliesAndReturnsMergedEnvelope() = runBlocking {
        val local = envelope(
            deviceId = "phone",
            generatedAt = 100L,
            displayName = SyncValue("Living room", clock(100L, 1L, "phone")),
            hidden = SyncValue(true, clock(200L, 2L, "phone")),
            favorite = null,
        )
        val remote = envelope(
            deviceId = "tv",
            generatedAt = 300L,
            displayName = SyncValue("TV source", clock(400L, 4L, "tv")),
            hidden = SyncValue(false, clock(150L, 1L, "tv")),
            favorite = SyncValue(
                SyncFavoriteState(order = 2L, addedAtEpochMillis = 250L),
                clock(500L, 5L, "tv"),
            ),
        )
        val expectedApply = DeviceSyncApplyResult(
            sourcesMaterialized = 1,
            channelsMaterialized = 1,
            groupsMaterialized = 0,
            membershipsMaterialized = 0,
            deferred = listOf(
                DeviceSyncDeferredMaterialization(
                    key = "source-1",
                    reason = DeviceSyncDeferredReason.SOURCE_SECRET_REQUIRED,
                ),
            ),
        )
        var reads = 0
        var applied: DeviceSyncEnvelope? = null
        val coordinator = DeviceSyncCoordinator(
            readLocalEnvelope = {
                reads += 1
                local
            },
            applyMergedEnvelope = {
                applied = it
                expectedApply
            },
            now = { 250L },
        )

        val result = coordinator.synchronize(remote)

        assertEquals(1, reads)
        assertSame(applied, result.mergedEnvelope)
        assertEquals("phone", result.mergedEnvelope.deviceId)
        assertEquals(300L, result.mergedEnvelope.generatedAtEpochMillis)
        assertEquals("TV source", result.mergedEnvelope.sources.single().displayName.value)
        assertEquals(true, result.mergedEnvelope.channels.single().hidden?.value)
        assertEquals(2L, result.mergedEnvelope.channels.single().favorite?.value?.order)
        assertSame(expectedApply, result.applyResult)
        assertEquals(expectedApply.deferred, result.deferred)
        assertFalse(result.fullyMaterialized)
    }

    @Test
    fun synchronizeUsesCurrentTimeWhenItIsNewestAndReportsFullyMaterialized() = runBlocking {
        val local = envelope(
            deviceId = "phone",
            generatedAt = 100L,
            displayName = SyncValue("Source", clock(100L, 1L, "phone")),
            hidden = null,
            favorite = null,
        )
        val remote = envelope(
            deviceId = "tv",
            generatedAt = 200L,
            displayName = SyncValue("Source", clock(100L, 1L, "phone")),
            hidden = null,
            favorite = null,
        )
        val coordinator = DeviceSyncCoordinator(
            readLocalEnvelope = { local },
            applyMergedEnvelope = {
                DeviceSyncApplyResult(0, 0, 0, 0, emptyList())
            },
            now = { 900L },
        )

        val result = coordinator.synchronize(remote)

        assertEquals(900L, result.mergedEnvelope.generatedAtEpochMillis)
        assertTrue(result.fullyMaterialized)
    }

    @Test
    fun identityConflictFailsBeforeApply() = runBlocking {
        val local = envelope(
            deviceId = "phone",
            generatedAt = 100L,
            displayName = SyncValue("Source", clock(100L, 1L, "phone")),
            hidden = null,
            favorite = null,
            locatorFingerprint = "fingerprint-a",
        )
        val remote = envelope(
            deviceId = "tv",
            generatedAt = 200L,
            displayName = SyncValue("Source", clock(200L, 2L, "tv")),
            hidden = null,
            favorite = null,
            locatorFingerprint = "fingerprint-b",
        )
        var applyCalled = false
        val coordinator = DeviceSyncCoordinator(
            readLocalEnvelope = { local },
            applyMergedEnvelope = {
                applyCalled = true
                DeviceSyncApplyResult(0, 0, 0, 0, emptyList())
            },
            now = { 300L },
        )

        var failed = false
        try {
            coordinator.synchronize(remote)
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
        assertFalse(applyCalled)
    }

    private fun envelope(
        deviceId: String,
        generatedAt: Long,
        displayName: SyncValue<String>,
        hidden: SyncValue<Boolean>?,
        favorite: SyncValue<SyncFavoriteState>?,
        locatorFingerprint: String? = "fingerprint",
    ): DeviceSyncEnvelope = DeviceSyncEnvelope(
        generatedAtEpochMillis = generatedAt,
        deviceId = deviceId,
        sources = listOf(
            SyncSourceState(
                identity = SyncSourceIdentity(
                    syncSourceId = "source-1",
                    sourceKind = "xtream",
                    locatorFingerprint = locatorFingerprint,
                ),
                displayName = displayName,
                enabled = SyncValue(true, clock(50L, 0L, "seed")),
                deleted = SyncValue(false, clock(50L, 0L, "seed")),
            ),
        ),
        channels = listOf(
            SyncChannelState(
                key = SyncChannelKey("source-1", "channel-1"),
                hidden = hidden,
                favorite = favorite,
            ),
        ),
        groups = emptyList(),
        memberships = emptyList(),
    )

    private fun clock(updatedAt: Long, revision: Long, deviceId: String) = SyncClock(
        updatedAtEpochMillis = updatedAt,
        revision = revision,
        deviceId = deviceId,
    )
}
