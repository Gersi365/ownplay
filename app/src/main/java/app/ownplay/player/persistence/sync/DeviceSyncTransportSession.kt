package app.ownplay.player.persistence.sync

import app.ownplay.player.sync.DeviceSyncPushRequest
import app.ownplay.player.sync.DeviceSyncPushResult
import app.ownplay.player.sync.DeviceSyncRemoteSnapshot
import app.ownplay.player.sync.DeviceSyncTransport
import java.util.UUID

/**
 * Executes one transport round-trip while keeping conflict resolution inside [DeviceSyncCoordinator].
 *
 * The remote store is treated as a compare-and-set register. A concurrent writer produces a
 * conflict, at which point the latest remote envelope is merged into the already-materialized local
 * state and the push is retried. This makes retries deterministic and avoids last-upload-wins loss.
 */
internal class DeviceSyncTransportSession(
    private val coordinator: DeviceSyncCoordinator,
    private val transport: DeviceSyncTransport,
    private val maxPushAttempts: Int = 4,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    init {
        require(maxPushAttempts >= 1)
    }

    suspend fun synchronize(): DeviceSyncTransportCycleResult {
        var remote = transport.pull()
        var lastCycle: DeviceSyncCycleResult? = null

        repeat(maxPushAttempts) { attemptIndex ->
            val outgoing = if (remote.envelope == null) {
                coordinator.readLocalSnapshot()
            } else {
                coordinator.synchronize(requireNotNull(remote.envelope)).also { cycle ->
                    lastCycle = cycle
                }.mergedEnvelope
            }

            when (
                val push = transport.push(
                    DeviceSyncPushRequest(
                        requestId = requestIdFactory().also { require(it.isNotBlank()) },
                        expectedRemoteRevision = remote.remoteRevision,
                        envelope = outgoing,
                    ),
                )
            ) {
                is DeviceSyncPushResult.Accepted -> {
                    return DeviceSyncTransportCycleResult(
                        attempts = attemptIndex + 1,
                        remoteSnapshot = push.snapshot,
                        applyResult = lastCycle?.applyResult,
                    )
                }

                is DeviceSyncPushResult.Conflict -> {
                    remote = push.snapshot
                }
            }
        }

        throw DeviceSyncTransportConflictException(
            latestRemote = remote,
            attempts = maxPushAttempts,
        )
    }
}

data class DeviceSyncTransportCycleResult(
    val attempts: Int,
    val remoteSnapshot: DeviceSyncRemoteSnapshot,
    val applyResult: DeviceSyncApplyResult?,
) {
    init {
        require(attempts >= 1)
        require(remoteSnapshot.remoteRevision > 0L)
        require(remoteSnapshot.envelope != null)
    }

    val deferred: List<DeviceSyncDeferredMaterialization>
        get() = applyResult?.deferred.orEmpty()

    val fullyMaterialized: Boolean
        get() = deferred.isEmpty()
}

class DeviceSyncTransportConflictException(
    val latestRemote: DeviceSyncRemoteSnapshot,
    val attempts: Int,
) : IllegalStateException(
    "Device sync transport did not converge after $attempts compare-and-set attempts",
)
