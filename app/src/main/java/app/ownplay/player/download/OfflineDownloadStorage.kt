package app.ownplay.player.download

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.ownplay.player.persistence.download.MediaDownloadEntity
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

internal object OfflineDownloadStorage {
    private const val PRIVATE_DIRECTORY = "offline"
    private const val CONTENT_URI_PREFIX = "content://"

    fun supportsPublicDownloads(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isPublicDownloadsLocation(location: String?): Boolean =
        location?.startsWith(CONTENT_URI_PREFIX) == true

    fun partialFile(context: Context, downloadId: String): File =
        File(privateDirectory(context), "$downloadId.part")

    fun privateFinalFile(context: Context, downloadId: String, extension: String): File =
        File(privateDirectory(context), "$downloadId.${normalizeExtension(extension)}")

    fun privateRelativePath(file: File): String = "$PRIVATE_DIRECTORY/${file.name}"

    fun resolvePrivateRelativePath(context: Context, relativePath: String): File? {
        if (!relativePath.startsWith("$PRIVATE_DIRECTORY/")) return null
        val base = privateDirectory(context).canonicalFile
        val candidate = File(context.filesDir, relativePath).canonicalFile
        return candidate.takeIf { file ->
            file.path == base.path || file.path.startsWith(base.path + File.separator)
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun createPublicDownloadsDestination(
        context: Context,
        row: MediaDownloadEntity,
    ): String {
        check(supportsPublicDownloads()) { "Public Downloads requires Android 10 or newer" }
        val extension = normalizeExtension(row.containerExtension)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, publicDisplayName(row, extension))
            put(MediaStore.Downloads.MIME_TYPE, mimeType(extension))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IOException("Could not create file in Downloads")
        return uri.toString()
    }

    fun openPublicOutput(
        context: Context,
        location: String,
        append: Boolean,
        startBytes: Long,
    ): BufferedOutputStream {
        val uri = Uri.parse(location)
        val descriptor = context.contentResolver.openFileDescriptor(
            uri,
            if (append) "rw" else "rwt",
        ) ?: throw IOException("Downloaded file is unavailable")
        val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        if (append && startBytes > 0L) {
            output.channel.position(startBytes)
        }
        return BufferedOutputStream(output)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun publishPublicDownload(context: Context, location: String) {
        if (!isPublicDownloadsLocation(location)) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        val updated = context.contentResolver.update(Uri.parse(location), values, null, null)
        if (updated <= 0) throw IOException("Could not publish file in Downloads")
    }

    fun locationExists(context: Context, location: String?): Boolean {
        if (location.isNullOrBlank()) return false
        return if (isPublicDownloadsLocation(location)) {
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(location), "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            resolvePrivateRelativePath(context, location)?.isFile == true
        }
    }

    fun locationSize(context: Context, location: String?): Long? {
        if (location.isNullOrBlank()) return null
        return if (isPublicDownloadsLocation(location)) {
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(location), "r")?.use { descriptor ->
                    descriptor.statSize.takeIf { it >= 0L }
                }
            } catch (_: Exception) {
                null
            }
        } else {
            resolvePrivateRelativePath(context, location)
                ?.takeIf(File::isFile)
                ?.length()
        }
    }

    fun deleteLocation(context: Context, location: String?) {
        if (location.isNullOrBlank()) return
        if (isPublicDownloadsLocation(location)) {
            try {
                context.contentResolver.delete(Uri.parse(location), null, null)
            } catch (_: Exception) {
                Unit
            }
        } else {
            resolvePrivateRelativePath(context, location)?.delete()
        }
    }

    fun playbackUri(context: Context, location: String?): String? {
        if (!locationExists(context, location)) return null
        val resolved = location ?: return null
        return if (isPublicDownloadsLocation(resolved)) {
            resolved
        } else {
            resolvePrivateRelativePath(context, resolved)?.let(Uri::fromFile)?.toString()
        }
    }

    fun normalizeExtension(extension: String?): String = extension
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "mp4"

    internal fun safeFileStem(value: String): String {
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.take(120).ifBlank { "OwnPlay" }
    }

    private fun privateDirectory(context: Context): File =
        File(context.filesDir, PRIVATE_DIRECTORY).apply { mkdirs() }

    private fun publicDisplayName(row: MediaDownloadEntity, extension: String): String {
        val stem = if (
            !row.seriesTitle.isNullOrBlank() &&
            row.seasonNumber != null &&
            row.episodeNumber != null
        ) {
            val season = row.seasonNumber.toString().padStart(2, '0')
            val episode = row.episodeNumber.toString().padStart(2, '0')
            safeFileStem("${row.seriesTitle} - S${season}E${episode} - ${row.title}")
        } else {
            safeFileStem(row.title)
        }
        return "$stem.$extension"
    }

    private fun mimeType(extension: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/*"
}
