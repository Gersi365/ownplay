package app.ownplay.player.source

sealed interface SourceSyncStage {
    data object Idle : SourceSyncStage
    data object LoadingChannels : SourceSyncStage
    data object LoadingEpg : SourceSyncStage
    data object Ready : SourceSyncStage
    data object ChannelsFailed : SourceSyncStage
    data object EpgFailed : SourceSyncStage
}

data class SourceSyncState(
    val sourceId: String? = null,
    val sourceName: String? = null,
    val stage: SourceSyncStage = SourceSyncStage.Idle,
    val channelCount: Int = 0,
    val epgChannelCount: Int = 0,
)
