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
import app.ownplay.player.persistence.download.MediaDownloadDao
import app.ownplay.player.persistence.download.MediaDownloadEntity
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
            val initialRow = dao.getById(downloadId) ?: return Result.success()
            if (initialRow.state == DownloadStates.PAUSED) {
                return Result.success()
            }
            if (
                initialRow.state == DownloadStates.COMPLETED &&
                OfflineDownloadStorage.locationExists(applicationContext, initialRow.localRelativePath)
            ) {
                return Result.success()
            }

            setForeground(createForegroundInfo(initialRow, initialRow.bytesDownloaded, initialRow.totalBytes))
            val resolver = XtreamDownloadLocatorResolver(
                database = database,
                sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext),
                credentialStore = AndroidKeystoreCredentialStore(applicationContext),
            )
            val locator = when (val resolved = resolver.resolve(initialRow)) {
                is DownloadLocatorResult.Success -> resolved.locator
                is DownloadLocatorResult.Failure -> {
                    markFailed(
                        dao = dao,
                        row = initialRow,
                        reason = resolved.reason,
                        localLocation = initialRow.localRelativePath,
                    )
                    return Result.failure()
                }
            }

            val partFile = OfflineDownloadStorage.partialFile(applicationContext, downloadId)
            val legacyPartialExists = partFile.isFile && partFile.length() > 0L
            val legacyPrivateLocation = initialRow.localRelativePath?.let { location ->
                !OfflineDownloadStorage.isPublicDownloadsLocation(location)
            } == true
            val usePublicDownloads =
                OfflineDownloadStorage.supportsPublicDownloads() &&
                    !legacyPartialExists &&
                    !legacyPrivateLocation
            val destinationLocation = if (usePublicDownloads) {
                initialRow.localRelativePath
                    ?.takeIf(OfflineDownloadStorage::isPublicDownloadsLocation)
                    ?.takeIf {
                        OfflineDownloadStorage.locationExists(applicationContext, it)
                    }
                    ?: OfflineDownloadStorage.createPublicDownloadsDestination(
                        applicationContext,
                        initialRow,
                    )
            } else {
                null
            }
            val existingBytes = if (usePublicDownloads) {
                OfflineDownloadStorage.locationSize(applicationContext, destinationLocation) ?: 0L
            } else {
                partFile.takeIf(File::isFile)?.length() ?: 0L
            }

            dao.updateTransfer(
                downloadId = downloadId,
                state = DownloadStates.DOWNLOADING,
                bytesDownloaded = existingBytes,
                totalBytes = null,
                localRelativePath = destinationLocation,
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
                    markFailed(
                        dao = dao,
                        row = initialRow,
                        reason = "Provider returned HTTP ${opened.code}",
                        bytesDownloaded = existingBytes,
                        localLocation = destinationLocation,
                    )
                    return Result.retry()
                }
                val body = opened.body
                val append = existingBytes > 0L && opened.code == 206
                val startBytes = if (append) existingBytes else 0L
                if (!usePublicDownloads && !append && partFile.exists()) {
                    partFile.delete()
                }
                val bodyLength = body.contentLength().takeIf { it >= 0L }
                val totalBytes = bodyLength?.plus(startBytes)
                if (bodyLength != null) {
                    val storageRoot = partFile.parentFile ?: applicationContext.filesDir
                    if (!hasEnoughOfflineDownloadSpace(storageRoot.usableSpace, bodyLength)) {
                        markFailed(
                            dao = dao,
                            row = initialRow,
                            reason = "Not enough free storage for this download. Free up space and retry.",
                            bytesDownloaded = startBytes,
                            totalBytes = totalBytes,
                            localLocation = destinationLocation,
                        )
                        return Result.failure()
                    }
                }
                var downloaded = startBytes
                var lastReportedBytes = downloaded
                var lastReportedAt = System.currentTimeMillis()

                val output = if (usePublicDownloads) {
                    OfflineDownloadStorage.openPublicOutput(
                        context = applicationContext,
                        location = requireNotNull(destinationLocation),
                        append = append,
                        startBytes = startBytes,
                    )
                } else {
                    BufferedOutputStream(FileOutputStream(partFile, append))
                }

                BufferedInputStream(body.byteStream()).use { input ->
                    output.use { openedOutput ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            openedOutput.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (
                                downloaded - lastReportedBytes >= PROGRESS_REPORT_BYTES ||
                                now - lastReportedAt >= PROGRESS_REPORT_MILLIS
                            ) {
                                openedOutput.flush()
                                if (dao.getById(downloadId)?.state == DownloadStates.PAUSED) {
                                    throw CancellationException("Download paused")
                                }
                                dao.updateTransfer(
                                    downloadId = downloadId,
                                    state = DownloadStates.DOWNLOADING,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    localRelativePath = destinationLocation,
                                    failureReason = null,
                                    updatedAtEpochMillis = now,
                                )
                                setForeground(createForegroundInfo(initialRow, downloaded, totalBytes))
                                lastReportedBytes = downloaded
                                lastReportedAt = now
                            }
                        }
                    }
                }

                if (dao.getById(downloadId)?.state == DownloadStates.PAUSED) {
                    throw CancellationException("Download paused before finalization")
                }
                if (totalBytes != null && downloaded < totalBytes) {
                    throw IOException("Download ended before the expected content length")
                }

                val finalLocation: String
                val finalBytes: Long
                if (usePublicDownloads) {
                    finalLocation = requireNotNull(destinationLocation)
                    OfflineDownloadStorage.publishPublicDownload(applicationContext, finalLocation)
                    finalBytes = OfflineDownloadStorage.locationSize(applicationContext, finalLocation)
                        ?: downloaded
                } else {
                    val finalFile = OfflineDownloadStorage.privateFinalFile(
                        applicationContext,
                        downloadId,
                        initialRow.containerExtension ?: "mp4",
                    )
                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    finalLocation = OfflineDownloadStorage.privateRelativePath(finalFile)
                    finalBytes = finalFile.length()
                }

                dao.updateTransfer(
                    downloadId = downloadId,
                    state = DownloadStates.COMPLETED,
                    bytesDownloaded = finalBytes,
                    totalBytes = totalBytes ?: finalBytes,
                    localRelativePath = finalLocation,
                    failureReason = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                return Result.success()
            }
        } catch (cancelled: CancellationException) {
            val row = dao.getById(downloadId)
            if (row != null) {
                val cancellationState = if (row.state == DownloadStates.PAUSED) {
                    DownloadStates.PAUSED
                } else {
                    DownloadStates.QUEUED
                }
                dao.updateTransfer(
                    downloadId = downloadId,
                    state = cancellationState,
                    bytesDownloaded = currentTransferBytes(row),
                    totalBytes = row.totalBytes,
                    localRelativePath = row.localRelativePath,
                    failureReason = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            throw cancelled
        } catch (_: Exception) {
            val row = dao.getById(downloadId)
            if (row != null) {
                markFailed(
                    dao = dao,
                    row = row,
                    reason = "Download interrupted",
                    bytesDownloaded = currentTransferBytes(row),
                    localLocation = row.localRelativePath,
                )
            }
            return Result.retry()
        } finally {
            database.close()
        }
    }

    private fun currentTransferBytes(row: MediaDownloadEntity): Long {
        if (OfflineDownloadStorage.isPublicDownloadsLocation(row.localRelativePath)) {
            return OfflineDownloadStorage.locationSize(applicationContext, row.localRelativePath)
                ?: row.bytesDownloaded
        }
        return OfflineDownloadStorage.partialFile(applicationContext, row.downloadId)
            .takeIf(File::isFile)
            ?.length()
            ?: row.bytesDownloaded
    }

    private suspend fun markFailed(
        dao: MediaDownloadDao,
        row: MediaDownloadEntity,
        reason: String,
        bytesDownloaded: Long = row.bytesDownloaded,
        totalBytes: Long? = row.totalBytes,
        localLocation: String? = row.localRelativePath,
    ) {
        dao.updateTransfer(
            downloadId = row.downloadId,
            state = DownloadStates.FAILED,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            localRelativePath = localLocation,
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
