package app.ownplay.player.epg

import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.CredentialStore
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import app.ownplay.player.source.xtream.XtreamXmlTvClient
import app.ownplay.player.source.xtream.XtreamXmlTvProgram
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EpgProgram(
    val title: String,
    val description: String?,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
    val startLabel: String?,
    val endLabel: String?,
)

data class EpgSnapshot(
    val current: EpgProgram?,
    val next: EpgProgram?,
    val programs: List<EpgProgram>,
)

data class EpgRefreshResult(
    val matchedChannelCount: Int,
    val programCount: Int,
)

class XtreamEpgRepository(
    private val database: OwnPlayDatabase,
    private val sensitiveValueStore: SensitiveValueStore,
    private val credentialStore: CredentialStore,
    private val xmlTvClient: XtreamXmlTvClient = XtreamXmlTvClient(),
) {
    private data class SourceCache(
        val channelIdsByEpgChannelId: Map<String, List<String>>,
        val programsByEpgChannelId: Map<String, List<EpgProgram>>,
    )

    private val cache = ConcurrentHashMap<String, SourceCache>()

    suspend fun refreshSource(sourceId: String): EpgRefreshResult? = withContext(Dispatchers.IO) {
        val source = database.playlistSourceDao().getById(sourceId) ?: return@withContext null
        if (source.sourceKind != SourceKinds.XTREAM) {
            cache.remove(sourceId)
            return@withContext EpgRefreshResult(0, 0)
        }
        val channels = database.providerCatalogDao().channelsForSource(sourceId)
        val channelIdsByEpgChannelId = channels.asSequence()
            .mapNotNull { channel ->
                val epgId = channel.tvgId?.trim()?.takeIf(String::isNotBlank)
                epgId?.let { it to channel.channelId }
            }
            .groupBy(
                keySelector = Pair<String, String>::first,
                valueTransform = Pair<String, String>::second,
            )
        val epgIds = channelIdsByEpgChannelId.keys
        if (epgIds.isEmpty()) {
            cache[sourceId] = SourceCache(
                channelIdsByEpgChannelId = emptyMap(),
                programsByEpgChannelId = emptyMap(),
            )
            return@withContext EpgRefreshResult(0, 0)
        }

        val locatorValue = runCatching {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
        }.getOrNull() ?: return@withContext null
        val locator = XtreamSourceLocatorCodec.parse(locatorValue) ?: return@withContext null
        val credentialRef = source.credentialRef?.let(::CredentialRef) ?: return@withContext null
        val credentials = runCatching { credentialStore.get(credentialRef) }.getOrNull()
            ?: return@withContext null

        val snapshot = when (
            val result = xmlTvClient.load(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                channelIds = epgIds,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> return@withContext null
        }

        val mapped = snapshot.programsByChannelId.mapValues { (_, entries) ->
            EpgTimelineProjector.normalize(entries.map(::toProgram))
        }
        cache[sourceId] = SourceCache(
            channelIdsByEpgChannelId = channelIdsByEpgChannelId,
            programsByEpgChannelId = mapped,
        )
        EpgRefreshResult(
            matchedChannelCount = snapshot.matchedChannelCount,
            programCount = mapped.values.sumOf(List<EpgProgram>::size),
        )
    }

    suspend fun snapshot(
        sourceId: String,
        channelId: String,
    ): EpgSnapshot? = withContext(Dispatchers.IO) {
        val channel = database.providerCatalogDao().channelById(channelId) ?: return@withContext null
        if (channel.sourceId != sourceId) return@withContext null
        val epgId = channel.tvgId?.trim()?.takeIf(String::isNotBlank) ?: return@withContext null
        val programs = cache[sourceId]?.programsByEpgChannelId?.get(epgId).orEmpty()
        if (programs.isEmpty()) return@withContext EpgSnapshot(null, null, emptyList())

        val timeline = EpgTimelineProjector.project(
            programs = programs,
            nowEpochSeconds = System.currentTimeMillis() / 1_000L,
        )
        EpgSnapshot(
            current = timeline.current,
            next = timeline.future.firstOrNull(),
            programs = timeline.programs,
        )
    }

    fun currentPrograms(
        sourceId: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): Map<String, EpgProgram> {
        val sourceCache = cache[sourceId] ?: return emptyMap()
        return buildMap {
            sourceCache.channelIdsByEpgChannelId.forEach { (epgChannelId, channelIds) ->
                val current = sourceCache.programsByEpgChannelId[epgChannelId]
                    ?.firstOrNull { program -> program.isCurrentAt(nowEpochSeconds) }
                    ?: return@forEach
                channelIds.forEach { channelId -> put(channelId, current) }
            }
        }
    }

    fun invalidateSource(sourceId: String) {
        cache.remove(sourceId)
    }

    private fun toProgram(program: XtreamXmlTvProgram): EpgProgram = EpgProgram(
        title = program.title,
        description = program.description,
        startEpochSeconds = program.startEpochSeconds,
        endEpochSeconds = program.endEpochSeconds,
        startLabel = program.startEpochSeconds?.let(::formatTime),
        endLabel = program.endEpochSeconds?.let(::formatTime),
    )

    private fun formatTime(epochSeconds: Long): String =
        TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

    private fun EpgProgram.isCurrentAt(nowEpochSeconds: Long): Boolean {
        val start = startEpochSeconds ?: return false
        val end = endEpochSeconds ?: return false
        return nowEpochSeconds >= start && nowEpochSeconds < end
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
