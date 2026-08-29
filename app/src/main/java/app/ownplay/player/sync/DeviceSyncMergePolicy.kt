package app.ownplay.player.sync

/**
 * Deterministic field-level merge policy for future phone/TV synchronization.
 *
 * Newer clocks win. Exact timestamp ties are broken by revision and finally deviceId so every
 * device reaches the same result without requiring one device to be permanently authoritative.
 */
internal object DeviceSyncMergePolicy {
    fun compare(left: SyncClock, right: SyncClock): Int {
        val timestamp = left.updatedAtEpochMillis.compareTo(right.updatedAtEpochMillis)
        if (timestamp != 0) return timestamp

        val revision = left.revision.compareTo(right.revision)
        if (revision != 0) return revision

        return left.deviceId.compareTo(right.deviceId)
    }

    fun <T> newer(
        left: SyncValue<T>?,
        right: SyncValue<T>?,
    ): SyncValue<T>? = when {
        left == null -> right
        right == null -> left
        compare(left.clock, right.clock) >= 0 -> left
        else -> right
    }

    fun mergeSource(
        left: SyncSourceState,
        right: SyncSourceState,
    ): SyncSourceState {
        require(left.identity.syncSourceId == right.identity.syncSourceId) {
            "Cannot merge different source identities"
        }
        val newestIdentity = if (
            compare(left.displayName.clock, right.displayName.clock) >= 0
        ) {
            left.identity
        } else {
            right.identity
        }
        return SyncSourceState(
            identity = newestIdentity,
            displayName = requireNotNull(newer(left.displayName, right.displayName)),
            enabled = requireNotNull(newer(left.enabled, right.enabled)),
            deleted = requireNotNull(newer(left.deleted, right.deleted)),
            encryptedSecretRef = when {
                left.encryptedSecretRef == right.encryptedSecretRef -> left.encryptedSecretRef
                compare(left.displayName.clock, right.displayName.clock) >= 0 -> left.encryptedSecretRef
                else -> right.encryptedSecretRef
            },
        )
    }

    fun mergeChannel(
        left: SyncChannelState,
        right: SyncChannelState,
    ): SyncChannelState {
        require(left.key == right.key) { "Cannot merge different channel identities" }
        return SyncChannelState(
            key = left.key,
            localDisplayName = newer(left.localDisplayName, right.localDisplayName),
            manualOrder = newer(left.manualOrder, right.manualOrder),
            hidden = newer(left.hidden, right.hidden),
            favorite = newer(left.favorite, right.favorite),
        )
    }

    fun mergeGroup(
        left: SyncGroupState,
        right: SyncGroupState,
    ): SyncGroupState {
        require(left.key == right.key) { "Cannot merge different group identities" }
        return SyncGroupState(
            key = left.key,
            name = requireNotNull(newer(left.name, right.name)),
            groupOrder = requireNotNull(newer(left.groupOrder, right.groupOrder)),
            deleted = requireNotNull(newer(left.deleted, right.deleted)),
        )
    }

    fun mergeMembership(
        left: SyncGroupMembershipState,
        right: SyncGroupMembershipState,
    ): SyncGroupMembershipState {
        require(left.key == right.key) { "Cannot merge different group memberships" }
        return SyncGroupMembershipState(
            key = left.key,
            order = requireNotNull(newer(left.order, right.order)),
        )
    }

    /**
     * Produces a stable display order after independent devices have edited channel positions.
     * Duplicate numeric positions are intentionally resolved by the stable channel identity.
     */
    fun orderedChannels(states: Collection<SyncChannelState>): List<SyncChannelState> =
        states.sortedWith(
            compareBy<SyncChannelState>(
                { it.manualOrder?.value ?: Long.MAX_VALUE },
                { it.key.syncSourceId },
                { it.key.providerKey },
            ),
        )
}
