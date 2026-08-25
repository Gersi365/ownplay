package app.ownplay.player.epg

internal object EpgCurrentProgramIndex {
    fun currentByChannel(
        channelIdsByEpgChannelId: Map<String, List<String>>,
        programsByEpgChannelId: Map<String, List<EpgProgram>>,
        nowEpochSeconds: Long,
    ): Map<String, EpgProgram> = buildMap {
        channelIdsByEpgChannelId.forEach { (epgChannelId, channelIds) ->
            val current = programsByEpgChannelId[epgChannelId]
                ?.firstOrNull { program -> program.isCurrentAt(nowEpochSeconds) }
                ?: return@forEach
            channelIds.forEach { channelId -> put(channelId, current) }
        }
    }

    private fun EpgProgram.isCurrentAt(nowEpochSeconds: Long): Boolean {
        val start = startEpochSeconds ?: return false
        val end = endEpochSeconds ?: return false
        return nowEpochSeconds >= start && nowEpochSeconds < end
    }
}
