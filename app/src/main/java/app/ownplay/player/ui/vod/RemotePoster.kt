package app.ownplay.player.ui.vod

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.ownplay.player.source.network.SourceHttpClient
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val MAX_POSTER_BYTES = 8 * 1024 * 1024
private const val MAX_POSTER_LONG_EDGE_PX = 768
private const val POSTER_MEMORY_CACHE_KB = 8 * 1024

private val posterMemoryCache = object : LruCache<String, ImageBitmap>(POSTER_MEMORY_CACHE_KB) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        estimatedPosterMemoryKb(width = value.width, height = value.height)
}

@Composable
internal fun RemotePoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val normalizedUrl = url?.trim()?.takeIf(String::isNotBlank)
    val cachedPoster = normalizedUrl?.let(posterMemoryCache::get)
    val image by produceState<ImageBitmap?>(
        initialValue = cachedPoster,
        key1 = normalizedUrl,
    ) {
        if (value != null) return@produceState
        value = normalizedUrl?.let { posterUrl ->
            withContext(Dispatchers.IO) {
                runCatching {
                    SourceHttpClient.shared.newCall(
                        Request.Builder().url(posterUrl).get().build(),
                    ).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body
                        val contentLength = body.contentLength()
                        if (contentLength > MAX_POSTER_BYTES.toLong()) return@use null
                        val bytes = readPosterBytes(
                            input = body.byteStream(),
                            maxBytes = MAX_POSTER_BYTES,
                        ) ?: return@use null
                        decodePoster(bytes)
                    }
                }.getOrNull()?.also { poster ->
                    posterMemoryCache.put(posterUrl, poster)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { poster ->
            Image(
                bitmap = poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "•",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun decodePoster(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculatePosterInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxLongEdgePx = MAX_POSTER_LONG_EDGE_PX,
        )
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

internal fun estimatedPosterMemoryKb(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    val bytes = width.toLong() * height.toLong() * 4L
    return ((bytes + 1023L) / 1024L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
        .coerceAtLeast(1)
}

internal fun calculatePosterInSampleSize(
    width: Int,
    height: Int,
    maxLongEdgePx: Int = MAX_POSTER_LONG_EDGE_PX,
): Int {
    require(maxLongEdgePx > 0) { "maxLongEdgePx must be positive" }
    if (width <= 0 || height <= 0) return 1

    var sampleSize = 1
    while (
        width / sampleSize > maxLongEdgePx ||
        height / sampleSize > maxLongEdgePx
    ) {
        if (sampleSize > Int.MAX_VALUE / 2) break
        sampleSize *= 2
    }
    return sampleSize
}

internal fun readPosterBytes(input: InputStream, maxBytes: Int): ByteArray? {
    if (maxBytes <= 0) return null
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
