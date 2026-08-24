package app.ownplay.player.vod

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.xtream.XtreamClient
import kotlinx.coroutines.flow.Flow

class VodFeatureRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
    private val repository = VodRepository(
        database = database,
        sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext),
        credentialStore = AndroidKeystoreCredentialStore(applicationContext),
        xtreamClient = XtreamClient(),
    )

    fun observeCatalog(sourceId: String): Flow<VodCatalog> = repository.observeCatalog(sourceId)

    suspend fun refresh(sourceId: String): SourceResult<Int> = repository.refresh(sourceId)

    suspend fun details(sourceId: String, movieId: String): SourceResult<VodMovieDetails> =
        repository.details(sourceId, movieId)

    suspend fun setFavorite(sourceId: String, movieId: String, favorite: Boolean): Boolean =
        repository.setFavorite(sourceId, movieId, favorite)

    suspend fun saveProgress(
        sourceId: String,
        movieId: String,
        positionMs: Long,
        durationMs: Long?,
    ): Boolean = repository.saveProgress(sourceId, movieId, positionMs, durationMs)

    suspend fun clearProgress(sourceId: String, movieId: String): Boolean =
        repository.clearProgress(sourceId, movieId)

    override fun close() {
        database.close()
    }
}
