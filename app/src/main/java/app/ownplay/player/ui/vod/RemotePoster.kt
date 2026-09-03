package app.ownplay.player.ui.vod

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ownplay.player.source.network.SourceHttpClient
import app.ownplay.player.ui.CachedRemoteImage
import app.ownplay.player.ui.RemoteImageMemoryCache
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import okhttp3.Request

private const val MAX_POSTER_BYTES = 8 * 1024 * 1024
private const val MAX_POSTER_LONG_EDGE_PX = 768

internal enum class RemotePosterPresentationState {
    LOADING,
    IMAGE,
    UNAVAILABLE,
}

private data class RemotePosterLoadResult(
    val image: ImageBitmap? = null,
    val requestFinished: Boolean = false,
)

internal fun remotePosterPresentationState(
    url: String?,
    requestFinished: Boolean,
    hasImage: Boolean,
): RemotePosterPresentationState = when {
    hasImage -> RemotePosterPresentationState.IMAGE
    url.isNullOrBlank() -> RemotePosterPresentationState.UNAVAILABLE
    !requestFinished -> RemotePosterPresentationState.LOADING
    else -> RemotePosterPresentationState.UNAVAILABLE
}

@Composable
internal fun RemotePoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val loadResult by produceState(
        initialValue = RemotePosterLoadResult(requestFinished = url.isNullOrBlank()),
        key1 = url,
    ) {
        value = RemotePosterLoadResult(requestFinished = url.isNullOrBlank())
        val posterUrl = url?.takeIf(String::isNotBlank)
        if (posterUrl == null) return@produceState

        val decoded = RemoteImageMemoryCache.getOrLoad(
            cacheKey = "poster|$posterUrl",
        ) {
            try {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        value = RemotePosterLoadResult(
            image = decoded?.image,
            requestFinished = true,
        )
    }
    val presentationState = remotePosterPresentationState(
        url = url,
        requestFinished = loadResult.requestFinished,
        hasImage = loadResult.image != null,
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (presentationState) {
            RemotePosterPresentationState.IMAGE -> loadResult.image?.let { poster ->
                Image(
                    bitmap = poster,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            RemotePosterPresentationState.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
            RemotePosterPresentationState.UNAVAILABLE -> Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title.trim().firstOrNull()?.uppercase() ?: "•",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "No artwork",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun decodePoster(bytes: ByteArray): CachedRemoteImage? {
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
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return CachedRemoteImage(
        image = bitmap.asImageBitmap(),
        byteCount = bitmap.allocationByteCount.coerceAtLeast(1),
    )
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
