package app.ownplay.player.ui.vod

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.ownplay.player.source.network.SourceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
internal fun RemotePoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = url
            ?.takeIf(String::isNotBlank)
            ?.let { posterUrl ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        SourceHttpClient.shared.newCall(
                            Request.Builder().url(posterUrl).get().build(),
                        ).execute().use { response ->
                            if (!response.isSuccessful) return@use null
                            BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = title.trim().firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
