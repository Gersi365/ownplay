package app.ownplay.player.epg

import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.CredentialStore
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamEpgProgram
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
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

class XtreamEpgRepository(
    private val database: OwnPlayDatabase,
    private val sensitiveValueStore: SensitiveValueStore,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient = XtreamClient(),
    private val cacheTtlMillis: Long = 120_000L,
) {
    private data class CacheEntry(
        val loadedAtEpochMillis: Long,
        val snapshot: EpgSnapshot,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun load(
        sourceId: String,
        channelId: String,
        force: Boolean = false,
    ): EpgSnapshot? = withContext(Dispatchers.IO) {
        val cacheKey = "$sourceId:$channelId"
        val nowMillis = System.currentTimeMillis()
        if (!force) {
            cache[cacheKey]
                ?.takeIf { nowMillis - it.loadedAtEpochMillis <= cacheTtlMillis }
                ?.let { return@withContext it.snapshot }
        }

        val source = database.playlistSourceDao().getById(sourceId) ?: return@withContext null
        if (source.sourceKind != SourceKinds.XTREAM) return@withContext null
        val channel = database.providerCatalogDao().channelById(channelId) ?: return@withContext null
        if (channel.sourceId != sourceId) return@withContext null
        val streamId = channel.providerStreamId?.takeIf(String::isNotBlank) ?: return@withContext null

        val locatorValue = runCatching {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
        }.getOrNull() ?: return@withContext null
        val locator = XtreamSourceLocatorCodec.parse(locatorValue) ?: return@withContext null
        val credentialRef = source.credentialRef?.let(::CredentialRef) ?: return@withContext null
        val credentials = runCatching { credentialStore.get(credentialRef) }.getOrNull()
            ?: return@withContext null

        val guide = when (
            val result = xtreamClient.getShortEpg(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                streamId = streamId,
                limit = 16,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> return@withContext null
        }

        val programs = guide.programs.map(::toProgram)
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val currentIndex = guide.programs.indexOfFirst { program ->
            val start = program.startEpochSeconds
            val end = program.endEpochSeconds
            start != null && end != null && nowSeconds >= start && nowSeconds < end
        }

        val current = when {
            currentIndex >= 0 -> programs[currentIndex]
            programs.isNotEmpty() && guide.programs.all { it.startEpochSeconds == null } -> programs.first()
            else -> null
        }
        val next = when {
            currentIndex >= 0 -> programs.getOrNull(currentIndex + 1)
            else -> guide.programs.indexOfFirst { program ->
                program.startEpochSeconds?.let { it >= nowSeconds } == true
            }.takeIf { it >= 0 }?.let(programs::getOrNull)
        }

        EpgSnapshot(
            current = current,
            next = next,
            programs = programs,
        ).also { snapshot ->
            cache[cacheKey] = CacheEntry(
                loadedAtEpochMillis = nowMillis,
                snapshot = snapshot,
            )
        }
    }

    fun invalidateSource(sourceId: String) {
        val prefix = "$sourceId:"
        cache.keys.removeIf { key -> key.startsWith(prefix) }
    }

    private fun toProgram(program: XtreamEpgProgram): EpgProgram = EpgProgram(
        title = program.title,
        description = program.description,
        startEpochSeconds = program.startEpochSeconds,
        endEpochSeconds = program.endEpochSeconds,
        startLabel = program.startLabel,
        endLabel = program.endLabel,
    )
}
