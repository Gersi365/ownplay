package app.ownplay.player.download

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.persistence.download.MediaDownloadEntity
import app.ownplay.player.persistence.vod.MediaKinds
import app.ownplay.player.persistence.vod.PlaybackProgressEntity
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow

data class OfflinePlaybackProgress(
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
)

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

    suspend fun playbackRequest(downloadId: String): PlaybackRequest? {
        val row = database.mediaDownloadDao().getById(downloadId) ?: return null
        if (row.state != DownloadStates.COMPLETED) return null

        val request = when (row.mediaKind) {
            DownloadMediaKinds.MOVIE -> PlaybackRequest(
                sourceId = row.sourceId,
                channelId = row.contentId,
                mediaKind = PlaybackMediaKind.MOVIE,
            )

            DownloadMediaKinds.SERIES_EPISODE -> PlaybackRequest(
                sourceId = row.sourceId,
                channelId = row.contentId,
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
                providerStreamId = row.providerStreamId,
                containerExtension = row.containerExtension,
            )

            else -> return null
        }

        return if (repository.localPlaybackLocator(request) != null) request else null
    }

    suspend fun playbackProgress(downloadId: String): OfflinePlaybackProgress? {
        val row = database.mediaDownloadDao().getById(downloadId) ?: return null
        val mediaKind = row.progressMediaKind() ?: return null
        return database.vodCatalogDao()
            .progress(row.sourceId, mediaKind, row.contentId)
            ?.let { progress ->
                OfflinePlaybackProgress(
                    positionMs = progress.positionMs,
                    durationMs = progress.durationMs,
                    completed = progress.completed,
                )
            }
    }

    suspend fun savePlaybackProgress(
        downloadId: String,
        positionMs: Long,
        durationMs: Long?,
    ): Boolean {
        return try {
            val row = database.mediaDownloadDao().getById(downloadId) ?: return false
            val mediaKind = row.progressMediaKind() ?: return false
            val existing = database.vodCatalogDao().progress(
                row.sourceId,
                mediaKind,
                row.contentId,
            )
            val normalizedPosition = positionMs.coerceAtLeast(0L)
            val normalizedDuration = durationMs?.takeIf { it > 0L }
                ?: existing?.durationMs?.takeIf { it > 0L }
            val completed = normalizedDuration?.let { duration ->
                normalizedPosition >= (duration * 0.95).toLong()
            } ?: false
            database.vodCatalogDao().upsertProgress(
                PlaybackProgressEntity(
                    sourceId = row.sourceId,
                    mediaKind = mediaKind,
                    contentId = row.contentId,
                    positionMs = normalizedPosition,
                    durationMs = normalizedDuration,
                    completed = completed,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        database.close()
    }

    private fun MediaDownloadEntity.progressMediaKind(): String? =
        when (mediaKind) {
            DownloadMediaKinds.MOVIE -> MediaKinds.MOVIE
            DownloadMediaKinds.SERIES_EPISODE -> MediaKinds.EPISODE
            else -> null
        }
}
