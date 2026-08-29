package app.ownplay.player.persistence.sync

import app.ownplay.player.sync.DeviceSyncEnvelope
import app.ownplay.player.sync.DeviceSyncEnvelopeMerger

/**
 * Single orchestration boundary for one synchronization cycle.
 *
 * Transports must not merge or materialize state themselves. They only supply a remote envelope
 * and consume the returned merged envelope for the next upload/acknowledgement step.
 */
internal class DeviceSyncCoordinator(
    private val readLocalEnvelope: suspend () -> DeviceSyncEnvelope,
    private val applyMergedEnvelope: suspend (DeviceSyncEnvelope) -> DeviceSyncApplyResult,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(
        store: DeviceSyncRoomEnvelopeStore,
        now: () -> Long = System::currentTimeMillis,
    ) : this(
        readLocalEnvelope = store::readEnvelope,
        applyMergedEnvelope = store::applyMergedEnvelope,
        now = now,
    )

    /**
     * Returns the current local transport snapshot without modifying Room state.
     *
     * This is used only when a transport has no remote envelope yet. Conflict resolution remains
     * inside [synchronize]; transports must never merge snapshots themselves.
     */
    suspend fun readLocalSnapshot(): DeviceSyncEnvelope = readLocalEnvelope()

    suspend fun synchronize(remoteEnvelope: DeviceSyncEnvelope): DeviceSyncCycleResult {
        val localEnvelope = readLocalEnvelope()
        val generatedAt = maxOf(
            now().coerceAtLeast(0L),
            localEnvelope.generatedAtEpochMillis,
            remoteEnvelope.generatedAtEpochMillis,
        )
        val mergedEnvelope = DeviceSyncEnvelopeMerger.merge(
            left = localEnvelope,
            right = remoteEnvelope,
            outputDeviceId = localEnvelope.deviceId,
            generatedAtEpochMillis = generatedAt,
        )
        val applyResult = applyMergedEnvelope(mergedEnvelope)
        return DeviceSyncCycleResult(
            mergedEnvelope = mergedEnvelope,
            applyResult = applyResult,
        )
    }
}

data class DeviceSyncCycleResult(
    val mergedEnvelope: DeviceSyncEnvelope,
    val applyResult: DeviceSyncApplyResult,
) {
    val deferred: List<DeviceSyncDeferredMaterialization>
        get() = applyResult.deferred

    val fullyMaterialized: Boolean
        get() = deferred.isEmpty()
}
