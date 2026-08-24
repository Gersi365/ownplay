package app.ownplay.player.series

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.xtream.XtreamSeriesClient
import kotlinx.coroutines.flow.Flow

class SeriesFeatureRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
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

    suspend fun clearEpisodeProgress(sourceId: String, episodeId: String): Boolean =
        repository.clearEpisodeProgress(sourceId, episodeId)

    override fun close() {
        database.close()
    }
}
