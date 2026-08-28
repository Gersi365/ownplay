package app.ownplay.player.epg

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.CredentialStore
import app.ownplay.player.source.credential.XtreamCredentials
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamEpgProgram
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import app.ownplay.player.source.xtream.XtreamXmlTvClient
import app.ownplay.player.source.xtream.XtreamXmlTvProgram
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
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
    private val xtreamClient: XtreamClient = XtreamClient(),
) {
    private data class SourceCache(
        val channelIdsByEpgChannelId: Map<String, List<String>>,
        val programsByEpgChannelId: Map<String, List<EpgProgram>>,
    )

    private data class ShortCacheKey(
        val sourceId: String,
        val channelId: String,
    )

    private data class ShortCacheEntry(
        val programs: List<EpgProgram>,
        val loadedAtEpochSeconds: Long,
    )

    private data class XtreamAccess(
        val serverUrl: String,
        val credentials: XtreamCredentials,
        val allowCleartext: Boolean,
    )

    private val cache = ConcurrentHashMap<String, SourceCache>()
    private val shortCache = ConcurrentHashMap<ShortCacheKey, ShortCacheEntry>()

    suspend fun refreshSource(sourceId: String): EpgRefreshResult? = withContext(Dispatchers.IO) {
        val source = database.playlistSourceDao().getById(sourceId) ?: return@withContext null
        if (source.sourceKind != SourceKinds.XTREAM) {
            invalidateSource(sourceId)
            return@withContext EpgRefreshResult(0, 0)
        }
        val channels = database.providerCatalogDao().channelsForSource(sourceId)
            .filter { channel -> channel.availability != ChannelAvailability.REMOVED }
        val channelIdsByEpgChannelId = channels.asSequence()
            .mapNotNull { channel ->
                val epgId = channel.tvgId?.trim()?.takeIf(String::isNotBlank)
                epgId?.let { it to channel.channelId }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
        val epgIds = channelIdsByEpgChannelId.keys
        if (epgIds.isEmpty()) {
            cache[sourceId] = SourceCache(
                channelIdsByEpgChannelId = emptyMap(),
                programsByEpgChannelId = emptyMap(),
            )
            return@withContext EpgRefreshResult(0, 0)
        }

        val access = resolveAccess(sourceId) ?: return@withContext null
        val snapshot = when (
            val result = xmlTvClient.load(
                serverUrl = access.serverUrl,
                credentials = access.credentials,
                channelIds = epgIds,
                allowCleartext = access.allowCleartext,
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
        val epgId = channel.tvgId?.trim()?.takeIf(String::isNotBlank)
        val globalPrograms = epgId
            ?.let { cache[sourceId]?.programsByEpgChannelId?.get(it) }
            .orEmpty()
        val programs = if (globalPrograms.isNotEmpty()) {
            globalPrograms
        } else {
            shortProgramsForChannel(
                sourceId = sourceId,
                channelId = channelId,
                providerStreamId = channel.providerStreamId,
            )
        }
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
        val sourceCache = cache[sourceId]
        val current = if (sourceCache == null) {
            mutableMapOf()
        } else {
            EpgCurrentProgramIndex.currentByChannel(
                channelIdsByEpgChannelId = sourceCache.channelIdsByEpgChannelId,
                programsByEpgChannelId = sourceCache.programsByEpgChannelId,
                nowEpochSeconds = nowEpochSeconds,
            ).toMutableMap()
        }

        shortCache.forEach { (key, entry) ->
            if (key.sourceId != sourceId) return@forEach
            if (
                !isEpgCacheFresh(
                    loadedAtEpochSeconds = entry.loadedAtEpochSeconds,
                    nowEpochSeconds = nowEpochSeconds,
                    ttlSeconds = SHORT_CACHE_TTL_SECONDS,
                )
            ) {
                shortCache.remove(key, entry)
                return@forEach
            }
            val fallbackCurrent = EpgCurrentProgramIndex.currentProgram(
                programs = entry.programs,
                nowEpochSeconds = nowEpochSeconds,
            ) ?: return@forEach
            current.putIfAbsent(key.channelId, fallbackCurrent)
        }
        return current
    }

    fun invalidateSource(sourceId: String) {
        cache.remove(sourceId)
        shortCache.entries.removeIf { entry -> entry.key.sourceId == sourceId }
    }

    private suspend fun shortProgramsForChannel(
        sourceId: String,
        channelId: String,
        providerStreamId: String?,
    ): List<EpgProgram> {
        val nowEpochSeconds = System.currentTimeMillis() / 1_000L
        val key = ShortCacheKey(sourceId = sourceId, channelId = channelId)
        shortCache[key]
            ?.takeIf { entry ->
                isEpgCacheFresh(
                    loadedAtEpochSeconds = entry.loadedAtEpochSeconds,
                    nowEpochSeconds = nowEpochSeconds,
                    ttlSeconds = SHORT_CACHE_TTL_SECONDS,
                )
            }
            ?.let { entry -> return entry.programs }

        val streamId = providerStreamId
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.toString()
            ?: return emptyList()
        val access = resolveAccess(sourceId) ?: return emptyList()
        val guide = when (
            val result = xtreamClient.getShortEpg(
                serverUrl = access.serverUrl,
                credentials = access.credentials,
                streamId = streamId,
                limit = SHORT_EPG_LIMIT,
                allowCleartext = access.allowCleartext,
            )
        ) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> return emptyList()
        }
        val programs = EpgTimelineProjector.normalize(guide.programs.map(::toProgram))
        shortCache[key] = ShortCacheEntry(
            programs = programs,
            loadedAtEpochSeconds = nowEpochSeconds,
        )
        return programs
    }

    private suspend fun resolveAccess(sourceId: String): XtreamAccess? {
        val source = database.playlistSourceDao().getById(sourceId) ?: return null
        if (source.sourceKind != SourceKinds.XTREAM) return null
        val locatorValue = try {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        val locator = XtreamSourceLocatorCodec.parse(locatorValue) ?: return null
        val credentialRef = source.credentialRef?.let(::CredentialRef) ?: return null
        val credentials = try {
            credentialStore.get(credentialRef)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        return XtreamAccess(
            serverUrl = locator.serverUrl,
            credentials = credentials,
            allowCleartext = locator.allowCleartext,
        )
    }

    private fun toProgram(program: XtreamXmlTvProgram): EpgProgram = EpgProgram(
        title = program.title,
        description = program.description,
        startEpochSeconds = program.startEpochSeconds,
        endEpochSeconds = program.endEpochSeconds,
        startLabel = program.startEpochSeconds?.let { epoch -> epgTimeLabel(epoch) },
        endLabel = program.endEpochSeconds?.let { epoch -> epgTimeLabel(epoch) },
    )

    private fun toProgram(program: XtreamEpgProgram): EpgProgram = EpgProgram(
        title = program.title,
        description = program.description,
        startEpochSeconds = program.startEpochSeconds,
        endEpochSeconds = program.endEpochSeconds,
        startLabel = program.startEpochSeconds?.let { epoch -> epgTimeLabel(epoch) } ?: program.startLabel,
        endLabel = program.endEpochSeconds?.let { epoch -> epgTimeLabel(epoch) } ?: program.endLabel,
    )

    private companion object {
        const val SHORT_EPG_LIMIT = 12
        const val SHORT_CACHE_TTL_SECONDS = 5L * 60L
    }
}
