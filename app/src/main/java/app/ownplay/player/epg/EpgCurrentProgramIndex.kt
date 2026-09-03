package app.ownplay.player.epg

internal object EpgCurrentProgramIndex {
    internal class Prepared internal constructor(
        private val programs: List<EpgProgram>,
    ) {
        private val searchableProgramCount = programs
            .indexOfFirst { program -> program.startEpochSeconds == null }
            .let { index -> if (index == -1) programs.size else index }

        private val maxEndEpochSecondsThroughIndex = LongArray(searchableProgramCount).also { maxEnds ->
            var maxEnd = Long.MIN_VALUE
            for (index in 0 until searchableProgramCount) {
                val end = programs[index].endEpochSeconds ?: Long.MIN_VALUE
                if (end > maxEnd) maxEnd = end
                maxEnds[index] = maxEnd
            }
        }

        fun currentProgram(nowEpochSeconds: Long): EpgProgram? {
            var low = 0
            var high = searchableProgramCount - 1
            var latestStartedIndex = -1
            while (low <= high) {
                val middle = (low + high).ushr(1)
                val start = programs[middle].startEpochSeconds ?: break
                if (start <= nowEpochSeconds) {
                    latestStartedIndex = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }

            var index = latestStartedIndex
            while (index >= 0) {
                if (maxEndEpochSecondsThroughIndex[index] <= nowEpochSeconds) return null
                val program = programs[index]
                val end = program.endEpochSeconds
                if (end != null && nowEpochSeconds < end) return program
                index -= 1
            }
            return null
        }
    }

    fun prepareNormalized(programs: List<EpgProgram>): Prepared = Prepared(programs)

    fun currentByChannel(
        channelIdsByEpgChannelId: Map<String, List<String>>,
        programsByEpgChannelId: Map<String, List<EpgProgram>>,
        nowEpochSeconds: Long,
    ): Map<String, EpgProgram> = buildMap {
        channelIdsByEpgChannelId.forEach { (epgChannelId, channelIds) ->
            val current = currentProgram(
                programs = programsByEpgChannelId[epgChannelId].orEmpty(),
                nowEpochSeconds = nowEpochSeconds,
            ) ?: return@forEach
            channelIds.forEach { channelId -> put(channelId, current) }
        }
    }

    fun currentByChannelPrepared(
        channelIdsByEpgChannelId: Map<String, List<String>>,
        preparedByEpgChannelId: Map<String, Prepared>,
        nowEpochSeconds: Long,
    ): Map<String, EpgProgram> = buildMap {
        channelIdsByEpgChannelId.forEach { (epgChannelId, channelIds) ->
            val current = preparedByEpgChannelId[epgChannelId]
                ?.currentProgram(nowEpochSeconds)
                ?: return@forEach
            channelIds.forEach { channelId -> put(channelId, current) }
        }
    }

    fun currentProgram(
        programs: List<EpgProgram>,
        nowEpochSeconds: Long,
    ): EpgProgram? = selectCurrentEpgProgram(programs, nowEpochSeconds)
}
