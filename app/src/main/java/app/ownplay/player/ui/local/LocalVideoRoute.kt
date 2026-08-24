package app.ownplay.player.ui.local

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LocalVideoPlayback
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackState

@Composable
internal fun LocalVideoRoute(
    runtime: OwnPlayAppRuntime,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<String?>(null) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var pickerError by remember { mutableStateOf<String?>(null) }

    fun closePlayback() {
        runtime.playbackController.stop()
        selectedUri = null
        selectedTitle = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val uriText = uri.toString()
        val request = runCatching { LocalVideoPlayback.request(uriText) }.getOrNull()
        if (request == null) {
            pickerError = "The selected item could not be opened as a local video."
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        pickerError = null
        selectedUri = uriText
        selectedTitle = localVideoDisplayName(context.contentResolver, uri)
        runtime.playbackController.start(request)
    }

    if (selectedUri != null) {
        LocalVideoPlaybackScreen(
            runtime = runtime,
            title = selectedTitle ?: "Local video",
            onExit = ::closePlayback,
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Local Video",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Open a video stored on this device with Android's system picker. " +
                        "OwnPlay reads only the item you choose; no broad storage scan is used.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pickerError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { picker.launch(arrayOf("video/*")) }) {
                    Text("Open Video")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LocalVideoPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    title: String,
    onExit: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val backOwner = remember { Any() }

    DisposableEffect(backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, onExit)
        onDispose {
            PlaybackInteractionBridge.clearBackAction(backOwner)
            onFullscreenStateChanged(false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onExit) { Text("Back") }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).also { view ->
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlaybackInteractionBridge.bind(
                            output = runtime.playbackVideoOutput,
                            view = view,
                            showNativeController = true,
                        )
                    }
                },
                update = { view ->
                    PlaybackInteractionBridge.bind(
                        output = runtime.playbackVideoOutput,
                        view = view,
                        showNativeController = true,
                    )
                },
                onRelease = { view ->
                    PlaybackInteractionBridge.unbind(runtime.playbackVideoOutput, view)
                },
            )
            if (playbackState is PlaybackState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp))
            }
            if (playbackState is PlaybackState.Failed) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Unable to play this video.")
                        Button(onClick = runtime.playbackController::retry) { Text("Retry") }
                    }
                }
            }
        }
    }
}

private fun localVideoDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String? = runCatching {
    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)?.trim()?.takeIf(String::isNotBlank)
    }
}.getOrNull()
