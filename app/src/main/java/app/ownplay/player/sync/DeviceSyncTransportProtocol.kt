package app.ownplay.player.sync

/**
 * Transport-only protocol for exchanging complete sync envelopes.
 *
 * Implementations may use cloud, LAN, pairing, or another mechanism, but they must preserve the
 * compare-and-set semantics below. Merge policy and Room materialization are deliberately outside
 * the transport boundary.
 */
internal interface DeviceSyncTransport {
    suspend fun pull(): DeviceSyncRemoteSnapshot

    suspend fun push(request: DeviceSyncPushRequest): DeviceSyncPushResult
}

data class DeviceSyncRemoteSnapshot(
    val remoteRevision: Long,
    val envelope: DeviceSyncEnvelope?,
) {
    init {
        require(remoteRevision >= 0L)
        if (remoteRevision == 0L) require(envelope == null) {
            "Remote revision zero is reserved for an empty transport"
        }
        if (remoteRevision > 0L) require(envelope != null) {
            "A non-zero remote revision requires an envelope"
        }
    }
}

data class DeviceSyncPushRequest(
    val requestId: String,
    val expectedRemoteRevision: Long,
    val envelope: DeviceSyncEnvelope,
) {
    init {
        require(requestId.isNotBlank())
        require(expectedRemoteRevision >= 0L)
    }
}

sealed interface DeviceSyncPushResult {
    val snapshot: DeviceSyncRemoteSnapshot

    /** The compare-and-set succeeded. */
    data class Accepted(
        override val snapshot: DeviceSyncRemoteSnapshot,
    ) : DeviceSyncPushResult {
        init {
            require(snapshot.remoteRevision > 0L)
            require(snapshot.envelope != null)
        }
    }

    /** Another writer advanced the remote revision; caller must pull/merge/retry. */
    data class Conflict(
        override val snapshot: DeviceSyncRemoteSnapshot,
    ) : DeviceSyncPushResult
}
