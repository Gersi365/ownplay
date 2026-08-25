package app.ownplay.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.persistence.download.MediaDownloadEntity
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

class OfflineDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val database = OwnPlayDatabase.create(applicationContext)
        val dao = database.mediaDownloadDao()
        try {
            val row = dao.getById(downloadId) ?: return Result.success()
            if (row.state == DownloadStates.PAUSED) {
                return Result.success()
            }
            if (row.state == DownloadStates.COMPLETED &&
                row.localRelativePath
                    ?.let { OfflineDownloadFiles.resolveRelativePath(applicationContext, it) }
                    ?.isFile == true
            ) {
                return Result.success()
            }

            setForeground(createForegroundInfo(row, row.bytesDownloaded, row.totalBytes))
            val resolver = XtreamDownloadLocatorResolver(
                database = database,
                sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext),
                credentialStore = AndroidKeystoreCredentialStore(applicationContext),
            )
            val locator = when (val resolved = resolver.resolve(row)) {
                is DownloadLocatorResult.Success -> resolved.locator
                is DownloadLocatorResult.Failure -> {
                    markFailed(dao, row, resolved.reason)
                    return Result.failure()
                }
            }

            val partFile = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
            val existingBytes = partFile.takeIf { it.isFile }?.length() ?: 0L
            dao.updateTransfer(
                downloadId = downloadId,
                state = DownloadStates.DOWNLOADING,
                bytesDownloaded = existingBytes,
                totalBytes = null,
                localRelativePath = null,
                failureReason = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )

            val requestBuilder = Request.Builder().url(locator.value)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use { opened ->
                if (!opened.isSuccessful) {
                    markFailed(dao, row, "Provider returned HTTP ${opened.code}", existingBytes)
                    return Result.retry()
                }
                val body = opened.body
                val append = existingBytes > 0L && opened.code == 206
                val startBytes = if (append) existingBytes else 0L
                if (!append && partFile.exists()) {
                    partFile.delete()
                }
                val bodyLength = body.contentLength().takeIf { it >= 0L }
                val totalBytes = bodyLength?.plus(startBytes)
                var downloaded = startBytes
                var lastReportedBytes = downloaded
                var lastReportedAt = System.currentTimeMillis()

                BufferedInputStream(body.byteStream()).use { input ->
                    BufferedOutputStream(FileOutputStream(partFile, append)).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (
                                downloaded - lastReportedBytes >= PROGRESS_REPORT_BYTES ||
                                now - lastReportedAt >= PROGRESS_REPORT_MILLIS
                            ) {
                                output.flush()
                                if (dao.getById(downloadId)?.state == DownloadStates.PAUSED) {
                                    throw CancellationException("Download paused")
                                }
                                dao.updateTransfer(
                                    downloadId = downloadId,
                                    state = DownloadStates.DOWNLOADING,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    localRelativePath = null,
                                    failureReason = null,
                                    updatedAtEpochMillis = now,
                                )
                                setForeground(createForegroundInfo(row, downloaded, totalBytes))
                                lastReportedBytes = downloaded
                                lastReportedAt = now
                            }
                        }
                    }
                }

                val finalFile = OfflineDownloadFiles.finalFile(
                    applicationContext,
                    downloadId,
                    row.containerExtension ?: "mp4",
                )
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                dao.updateTransfer(
                    downloadId = downloadId,
                    state = DownloadStates.COMPLETED,
                    bytesDownloaded = finalFile.length(),
                    totalBytes = totalBytes ?: finalFile.length(),
                    localRelativePath = OfflineDownloadFiles.relativePath(finalFile),
                    failureReason = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                return Result.success()
            }
        } catch (cancelled: CancellationException) {
            val row = dao.getById(downloadId)
            if (row != null) {
                val partial = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
                val cancellationState = if (row.state == DownloadStates.PAUSED) {
                    DownloadStates.PAUSED
                } else {
                    DownloadStates.QUEUED
                }
                dao.updateTransfer(
                    downloadId = downloadId,
                    state = cancellationState,
                    bytesDownloaded = partial.takeIf { it.isFile }?.length() ?: row.bytesDownloaded,
                    totalBytes = row.totalBytes,
                    localRelativePath = null,
                    failureReason = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            throw cancelled
        } catch (_: Exception) {
            val row = dao.getById(downloadId)
            if (row != null) {
                val partial = OfflineDownloadFiles.partialFile(applicationContext, downloadId)
                markFailed(
                    dao = dao,
                    row = row,
                    reason = "Download interrupted",
                    bytesDownloaded = partial.takeIf { it.isFile }?.length() ?: row.bytesDownloaded,
                )
            }
            return Result.retry()
        } finally {
            database.close()
        }
    }

    private suspend fun markFailed(
        dao: app.ownplay.player.persistence.download.MediaDownloadDao,
        row: MediaDownloadEntity,
        reason: String,
        bytesDownloaded: Long = row.bytesDownloaded,
    ) {
        dao.updateTransfer(
            downloadId = row.downloadId,
            state = DownloadStates.FAILED,
            bytesDownloaded = bytesDownloaded,
            totalBytes = row.totalBytes,
            localRelativePath = null,
            failureReason = reason,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun createForegroundInfo(
        row: MediaDownloadEntity,
        bytesDownloaded: Long,
        totalBytes: Long?,
    ): ForegroundInfo {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${row.title}")
            .setContentText(progressLabel(bytesDownloaded, totalBytes))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (totalBytes != null && totalBytes > 0L) {
            val progress = ((bytesDownloaded.toDouble() / totalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return ForegroundInfo(
            NOTIFICATION_ID_BASE + (row.downloadId.hashCode() and 0x3fffffff),
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Offline downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress for movies and series episodes saved for offline playback"
                },
            )
        }
    }

    private fun progressLabel(bytesDownloaded: Long, totalBytes: Long?): String {
        val downloaded = humanBytes(bytesDownloaded)
        val knownTotal = totalBytes?.takeIf { it > 0L }
        val total = knownTotal?.let(::humanBytes)
        if (knownTotal == null || total == null) return downloaded
        val percent = ((bytesDownloaded.coerceAtLeast(0L).toDouble() / knownTotal.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
        return "$downloaded / $total · $percent%"
    }

    private fun humanBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1_073_741_824L -> "%.1f GB".format(safe / 1_073_741_824.0)
            safe >= 1_048_576L -> "%.1f MB".format(safe / 1_048_576.0)
            safe >= 1_024L -> "%.1f KB".format(safe / 1_024.0)
            else -> "$safe B"
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val BUFFER_SIZE_BYTES = 64 * 1024
        private const val PROGRESS_REPORT_BYTES = 1024 * 1024L
        private const val PROGRESS_REPORT_MILLIS = 750L
        private const val NOTIFICATION_CHANNEL_ID = "ownplay_offline_downloads"
        private const val NOTIFICATION_ID_BASE = 4100

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
