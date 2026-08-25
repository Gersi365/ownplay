package app.ownplay.player.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.persistence.download.MediaDownloadEntity
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.ResolvedPlaybackLocator
import app.ownplay.player.playback.ResolvedPlaybackOrigin
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class OfflineDownloadSpec(
    val sourceId: String,
    val mediaKind: String,
    val contentId: String,
    val providerStreamId: Int,
    val title: String,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterUrl: String? = null,
    val containerExtension: String? = null,
)

data class OfflineDownload(
    val downloadId: String,
    val sourceId: String,
    val mediaKind: String,
    val contentId: String,
    val title: String,
    val seriesTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val posterUrl: String?,
    val state: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val failureReason: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val completed: Boolean
        get() = state == DownloadStates.COMPLETED

    val active: Boolean
        get() = state == DownloadStates.QUEUED || state == DownloadStates.DOWNLOADING

    val paused: Boolean
        get() = state == DownloadStates.PAUSED

    val progressFraction: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (bytesDownloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

class OfflineDownloadRepository(
    context: Context,
    private val database: OwnPlayDatabase,
) {
    private val applicationContext = context.applicationContext
    private val dao = database.mediaDownloadDao()
    private val workManager = WorkManager.getInstance(applicationContext)

    fun observeAll(): Flow<List<OfflineDownload>> =
        dao.observeAll().map { rows -> rows.map(::mapDownload) }

    suspend fun enqueue(spec: OfflineDownloadSpec): String {
        require(spec.sourceId.isNotBlank()) { "sourceId is required" }
        require(spec.contentId.isNotBlank()) { "contentId is required" }
        require(spec.providerStreamId > 0) { "providerStreamId must be positive" }
        require(spec.title.isNotBlank()) { "title is required" }
        require(
            spec.mediaKind == DownloadMediaKinds.MOVIE ||
                spec.mediaKind == DownloadMediaKinds.SERIES_EPISODE,
        ) { "Unsupported download media kind" }

        val existing = dao.getForContent(spec.sourceId, spec.mediaKind, spec.contentId)
        if (existing != null) {
            val completedFile = existing.localRelativePath?.let(::resolveRelativePath)
            if (existing.state == DownloadStates.COMPLETED && completedFile?.isFile == true) {
                return existing.downloadId
            }
            val partial = OfflineDownloadFiles.partialFile(applicationContext, existing.downloadId)
            dao.upsert(
                existing.copy(
                    providerStreamId = spec.providerStreamId,
                    title = spec.title,
                    seriesTitle = spec.seriesTitle,
                    seasonNumber = spec.seasonNumber,
                    episodeNumber = spec.episodeNumber,
                    posterUrl = spec.posterUrl,
                    containerExtension = spec.containerExtension,
                    state = DownloadStates.QUEUED,
                    bytesDownloaded = partial.takeIf(File::isFile)?.length() ?: 0L,
                    totalBytes = null,
                    localRelativePath = null,
                    failureReason = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            enqueueWork(existing.downloadId)
            return existing.downloadId
        }

        val now = System.currentTimeMillis()
        val downloadId = UUID.randomUUID().toString()
        dao.upsert(
            MediaDownloadEntity(
                downloadId = downloadId,
                sourceId = spec.sourceId,
                mediaKind = spec.mediaKind,
                contentId = spec.contentId,
                providerStreamId = spec.providerStreamId,
                title = spec.title.trim(),
                seriesTitle = spec.seriesTitle?.trim()?.takeIf(String::isNotBlank),
                seasonNumber = spec.seasonNumber,
                episodeNumber = spec.episodeNumber,
                posterUrl = spec.posterUrl,
                containerExtension = spec.containerExtension,
                state = DownloadStates.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = null,
                localRelativePath = null,
                failureReason = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        enqueueWork(downloadId)
        return downloadId
    }

    suspend fun pause(downloadId: String) {
        val existing = dao.getById(downloadId) ?: return
        if (existing.state != DownloadStates.QUEUED && existing.state != DownloadStates.DOWNLOADING) {
            return
        }
        val partial = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
        dao.updateTransfer(
            downloadId = downloadId,
            state = DownloadStates.PAUSED,
            bytesDownloaded = partial.takeIf(File::isFile)?.length() ?: existing.bytesDownloaded,
            totalBytes = existing.totalBytes,
            localRelativePath = null,
            failureReason = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        workManager.cancelUniqueWork(workName(downloadId))
    }

    suspend fun resume(downloadId: String) {
        val existing = dao.getById(downloadId) ?: return
        if (existing.state != DownloadStates.PAUSED) return
        val partial = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
        dao.updateTransfer(
            downloadId = downloadId,
            state = DownloadStates.QUEUED,
            bytesDownloaded = partial.takeIf(File::isFile)?.length() ?: existing.bytesDownloaded,
            totalBytes = existing.totalBytes,
            localRelativePath = null,
            failureReason = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        enqueueWork(downloadId)
    }

    suspend fun retry(downloadId: String) {
        val existing = dao.getById(downloadId) ?: return
        if (existing.state == DownloadStates.COMPLETED &&
            existing.localRelativePath?.let(::resolveRelativePath)?.isFile == true
        ) {
            return
        }
        val partial = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
        dao.upsert(
            existing.copy(
                state = DownloadStates.QUEUED,
                bytesDownloaded = partial.takeIf(File::isFile)?.length() ?: 0L,
                totalBytes = null,
                localRelativePath = null,
                failureReason = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        enqueueWork(downloadId)
    }

    suspend fun remove(downloadId: String) {
        workManager.cancelUniqueWork(workName(downloadId))
        dao.getById(downloadId)?.localRelativePath?.let(::resolveRelativePath)?.delete()
        OfflineDownloadFiles.partialFile(applicationContext, downloadId).delete()
        dao.delete(downloadId)
    }

    suspend fun localPlaybackLocator(request: PlaybackRequest): ResolvedPlaybackLocator? {
        val mediaKind = when (request.mediaKind) {
            PlaybackMediaKind.LIVE -> return null
            PlaybackMediaKind.MOVIE -> DownloadMediaKinds.MOVIE
            PlaybackMediaKind.SERIES_EPISODE -> DownloadMediaKinds.SERIES_EPISODE
        }
        val row = dao.getForContent(
            sourceId = request.sourceId,
            mediaKind = mediaKind,
            contentId = request.channelId,
        ) ?: return null
        if (row.state != DownloadStates.COMPLETED) return null
        val file = row.localRelativePath?.let(::resolveRelativePath) ?: return null
        if (!file.isFile) {
            dao.updateTransfer(
                downloadId = row.downloadId,
                state = DownloadStates.FAILED,
                bytesDownloaded = 0L,
                totalBytes = null,
                localRelativePath = null,
                failureReason = "Downloaded file is missing",
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            return null
        }
        return ResolvedPlaybackLocator(
            value = Uri.fromFile(file).toString(),
            origin = ResolvedPlaybackOrigin.LOCAL_DOWNLOAD,
        )
    }

    private fun enqueueWork(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(OfflineDownloadWorker.KEY_DOWNLOAD_ID to downloadId))
            .build()
        workManager.enqueueUniqueWork(
            workName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun resolveRelativePath(relativePath: String): File? =
        OfflineDownloadFiles.resolveRelativePath(applicationContext, relativePath)

    private fun mapDownload(row: MediaDownloadEntity): OfflineDownload = OfflineDownload(
        downloadId = row.downloadId,
        sourceId = row.sourceId,
        mediaKind = row.mediaKind,
        contentId = row.contentId,
        title = row.title,
        seriesTitle = row.seriesTitle,
        seasonNumber = row.seasonNumber,
        episodeNumber = row.episodeNumber,
        posterUrl = row.posterUrl,
        state = row.state,
        bytesDownloaded = row.bytesDownloaded,
        totalBytes = row.totalBytes,
        failureReason = row.failureReason,
        createdAtEpochMillis = row.createdAtEpochMillis,
        updatedAtEpochMillis = row.updatedAtEpochMillis,
    )

    companion object {
        fun workName(downloadId: String): String = "ownplay-offline-download-$downloadId"
    }
}

internal object OfflineDownloadFiles {
    private const val DIRECTORY = "offline"

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun partialFile(context: Context, downloadId: String): File =
        File(directory(context), "$downloadId.part")

    fun finalFile(context: Context, downloadId: String, extension: String): File =
        File(directory(context), "$downloadId.${normalizeExtension(extension)}")

    fun relativePath(file: File): String = "$DIRECTORY/${file.name}"

    fun resolveRelativePath(context: Context, relativePath: String): File? {
        if (!relativePath.startsWith("$DIRECTORY/")) return null
        val base = directory(context).canonicalFile
        val candidate = File(context.filesDir, relativePath).canonicalFile
        return candidate.takeIf { file ->
            file.path == base.path || file.path.startsWith(base.path + File.separator)
        }
    }

    fun normalizeExtension(extension: String?): String = extension
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "mp4"
}
