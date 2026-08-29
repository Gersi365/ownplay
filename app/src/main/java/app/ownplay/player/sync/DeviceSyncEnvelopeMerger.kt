package app.ownplay.player.sync

/**
 * Combines two complete sync snapshots without knowing how either snapshot was transported.
 * Field-level conflicts are delegated to [DeviceSyncMergePolicy].
 */
internal object DeviceSyncEnvelopeMerger {
    fun merge(
        left: DeviceSyncEnvelope,
        right: DeviceSyncEnvelope,
        outputDeviceId: String,
        generatedAtEpochMillis: Long,
    ): DeviceSyncEnvelope {
        require(left.contractVersion == right.contractVersion) {
            "Cannot merge different sync contract versions"
        }
        require(outputDeviceId.isNotBlank())
        require(generatedAtEpochMillis >= 0L)

        return DeviceSyncEnvelope(
            contractVersion = left.contractVersion,
            generatedAtEpochMillis = generatedAtEpochMillis,
            deviceId = outputDeviceId,
            sources = mergeByKey(
                left = left.sources,
                right = right.sources,
                key = { it.identity.syncSourceId },
                merge = DeviceSyncMergePolicy::mergeSource,
            ),
            channels = mergeByKey(
                left = left.channels,
                right = right.channels,
                key = SyncChannelState::key,
                merge = DeviceSyncMergePolicy::mergeChannel,
            ),
            groups = mergeByKey(
                left = left.groups,
                right = right.groups,
                key = SyncGroupState::key,
                merge = DeviceSyncMergePolicy::mergeGroup,
            ),
            memberships = mergeByKey(
                left = left.memberships,
                right = right.memberships,
                key = SyncGroupMembershipState::key,
                merge = DeviceSyncMergePolicy::mergeMembership,
            ),
        )
    }

    private fun <K : Comparable<K>, T> mergeByKey(
        left: List<T>,
        right: List<T>,
        key: (T) -> K,
        merge: (T, T) -> T,
    ): List<T> {
        val leftByKey = left.associateBy(key)
        val rightByKey = right.associateBy(key)
        return (leftByKey.keys + rightByKey.keys)
            .toSortedSet()
            .map { itemKey ->
                val leftItem = leftByKey[itemKey]
                val rightItem = rightByKey[itemKey]
                when {
                    leftItem == null -> requireNotNull(rightItem)
                    rightItem == null -> leftItem
                    else -> merge(leftItem, rightItem)
                }
            }
    }
}
