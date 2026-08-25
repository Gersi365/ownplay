package app.ownplay.player.download

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import kotlinx.coroutines.flow.Flow

class OfflineDownloadFeatureRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
    private val repository = OfflineDownloadRepository(
        context = applicationContext,
        database = database,
    )

    fun observeAll(): Flow<List<OfflineDownload>> = repository.observeAll()

    suspend fun enqueue(spec: OfflineDownloadSpec): String = repository.enqueue(spec)

    suspend fun pause(downloadId: String) = repository.pause(downloadId)

    suspend fun resume(downloadId: String) = repository.resume(downloadId)

    suspend fun retry(downloadId: String) = repository.retry(downloadId)

    suspend fun remove(downloadId: String) = repository.remove(downloadId)

    override fun close() {
        database.close()
    }
}
