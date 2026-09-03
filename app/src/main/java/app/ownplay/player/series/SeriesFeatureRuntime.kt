package app.ownplay.player.series

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.persistence.series.SeriesMediaKinds
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.xtream.XtreamSeriesClient
import kotlinx.coroutines.flow.Flow

data class SeriesEpisodeProgressSnapshot(
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

class SeriesFeatureRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = sharedDatabase(applicationContext)
    private val repository = SeriesRepository(
        database = database,
        sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext),
        credentialStore = AndroidKeystoreCredentialStore(applicationContext),
        xtreamSeriesClient = XtreamSeriesClient(),
    )

    fun observeCatalog(sourceId: String): Flow<SeriesCatalog> = repository.observeCatalog(sourceId)

    suspend fun refresh(sourceId: String): SourceResult<Int> = repository.refresh(sourceId)

    suspend fun details(sourceId: String, seriesId: String): SourceResult<SeriesDetails> =
        repository.details(sourceId, seriesId)

    suspend fun setFavorite(sourceId: String, seriesId: String, favorite: Boolean): Boolean =
        repository.setFavorite(sourceId, seriesId, favorite)

    suspend fun saveEpisodeProgress(
        sourceId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long?,
    ): Boolean = repository.saveEpisodeProgress(sourceId, episodeId, positionMs, durationMs)

    suspend fun episodeProgress(
        sourceId: String,
        episodeId: String,
    ): SeriesEpisodeProgressSnapshot? = database.seriesCatalogDao()
        .progress(
            sourceId = sourceId,
            mediaKind = SeriesMediaKinds.EPISODE,
            contentId = episodeId,
        )
        ?.let { progress ->
            SeriesEpisodeProgressSnapshot(
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                completed = progress.completed,
                updatedAtEpochMillis = progress.updatedAtEpochMillis,
            )
        }

    suspend fun clearEpisodeProgress(sourceId: String, episodeId: String): Boolean =
        repository.clearEpisodeProgress(sourceId, episodeId)

    override fun close() {
        // Series routes are screen-scoped, but their Room database must outlive route disposal.
        // Animated section transitions and active WorkManager episode-download writes can overlap
        // the outgoing Series composition, so the shared instance intentionally lives for the process.
    }

    private companion object {
        @Volatile
        private var processDatabase: OwnPlayDatabase? = null

        fun sharedDatabase(context: Context): OwnPlayDatabase =
            processDatabase ?: synchronized(this) {
                processDatabase ?: OwnPlayDatabase.create(context).also { database ->
                    processDatabase = database
                }
            }
    }
}
