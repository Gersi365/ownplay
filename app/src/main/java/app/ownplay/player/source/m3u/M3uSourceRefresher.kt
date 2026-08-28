package app.ownplay.player.source.m3u

import app.ownplay.player.live.ingest.InitialLiveCatalogFactory
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestResult
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult

internal data class M3uRefreshRequest(
    val sourceId: String,
    val sourceKind: String,
    val storedLocator: String,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(storedLocator.isNotBlank()) { "storedLocator must not be blank" }
    }

    override fun toString(): String =
        "M3uRefreshRequest(sourceId=<opaque>, sourceKind=$sourceKind, storedLocator=<redacted>)"
}

internal class M3uSourceRefresher(
    private val loadRemote: suspend (url: String, allowCleartext: Boolean) -> SourceResult<M3uPlaylist>,
    private val loadLocal: suspend (documentUri: String) -> SourceResult<M3uPlaylist>,
    private val ingest: suspend (
        sourceId: String,
        generation: Long,
        playlist: M3uPlaylist,
    ) -> InitialLiveCatalogIngestResult,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun refresh(request: M3uRefreshRequest): SourceOnboardingResult {
        val locator = M3uSourceLocatorCodec.parse(request.storedLocator)
            ?: return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.CatalogImportFailure,
            )

        val loaded = when (request.sourceKind) {
            SourceKinds.REMOTE_M3U -> loadRemote(locator.value, locator.allowCleartext)
            SourceKinds.LOCAL_M3U -> loadLocal(locator.value)
            else -> return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.CatalogImportFailure,
            )
        }
        val playlist = when (loaded) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        val ingestResult = ingest(
            request.sourceId,
            nowEpochMillis(),
            playlist,
        )
        return when (ingestResult) {
            is InitialLiveCatalogIngestResult.Success -> SourceOnboardingResult.Success(
                sourceId = request.sourceId,
                channelCount = ingestResult.channelCount,
            )
            else -> SourceOnboardingResult.Failure(
                SourceOnboardingFailure.CatalogImportFailure,
            )
        }
    }

    companion object {
        fun catalogIngest(
            ingestCatalog: suspend (
                sourceId: String,
                generation: Long,
                catalog: app.ownplay.player.live.ingest.IncomingLiveCatalog,
            ) -> InitialLiveCatalogIngestResult,
        ): suspend (String, Long, M3uPlaylist) -> InitialLiveCatalogIngestResult =
            { sourceId, generation, playlist ->
                ingestCatalog(
                    sourceId,
                    generation,
                    InitialLiveCatalogFactory.fromM3u(playlist),
                )
            }
    }
}
