package app.ownplay.player.ui.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.runtime.LaunchedEffect
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
import app.ownplay.player.playback.PlaybackFailureCategory
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

    val activeUri = selectedUri
    if (activeUri != null) {
        LocalVideoPlaybackScreen(
            runtime = runtime,
            videoUri = activeUri,
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
    videoUri: String,
    title: String,
    onExit: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val backOwner = remember { Any() }
    var platformFallbackActive by remember(videoUri) { mutableStateOf(false) }

    DisposableEffect(backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, onExit)
        onDispose {
            PlaybackInteractionBridge.clearBackAction(backOwner)
            onFullscreenStateChanged(false)
        }
    }

    LaunchedEffect(playbackState, platformFallbackActive) {
        val failure = (playbackState as? PlaybackState.Failed)?.failure
        if (
            !platformFallbackActive &&
            failure != null &&
            LocalVideoPlayback.shouldUsePlatformFallback(failure)
        ) {
            platformFallbackActive = true
            runtime.playbackController.stop()
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
            if (platformFallbackActive) {
                PlatformLocalVideoFallback(videoUri = videoUri)
            } else {
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
            }

            if (!platformFallbackActive && playbackState is PlaybackState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp))
            }

            val failed = playbackState as? PlaybackState.Failed
            if (!platformFallbackActive && failed != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(localPlaybackFailureLabel(failed.failure.category))
                        if (failed.failure.retryable) {
                            Button(onClick = runtime.playbackController::retry) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformLocalVideoFallback(videoUri: String) {
    var platformReady by remember(videoUri) { mutableStateOf(false) }
    var platformError by remember(videoUri) { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlatformFallbackVideoView(viewContext).also { view ->
                    view.onPlaybackReady = {
                        platformReady = true
                        platformError = null
                    }
                    view.onPlaybackError = { what, extra ->
                        platformReady = true
                        platformError =
                            "Android platform playback also failed. (what=$what, extra=$extra)"
                    }
                    view.open(videoUri)
                }
            },
            update = { view -> view.open(videoUri) },
            onRelease = PlatformFallbackVideoView::releasePlayer,
        )

        if (!platformReady) {
            CircularProgressIndicator(modifier = Modifier.size(42.dp))
        }

        platformError?.let { message ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private class PlatformFallbackVideoView(
    context: Context,
) : VideoView(context) {
    var onPlaybackReady: (() -> Unit)? = null
    var onPlaybackError: ((what: Int, extra: Int) -> Unit)? = null

    private val controller = MediaController(context)
    private var openedUri: String? = null
    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                controller.show()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val deltaMs = if (event.x < width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS
                seekBy(deltaMs)
                controller.show()
                return true
            }
        },
    )

    init {
        controller.setAnchorView(this)
        setMediaController(controller)
        setOnPreparedListener {
            controller.setAnchorView(this)
            onPlaybackReady?.invoke()
            start()
            controller.show(CONTROLLER_INITIAL_SHOW_MS)
        }
        setOnErrorListener { _, what, extra ->
            onPlaybackError?.invoke(what, extra)
            true
        }
    }

    fun open(contentUri: String) {
        if (openedUri == contentUri) return
        openedUri = contentUri
        setVideoURI(Uri.parse(contentUri))
        requestFocus()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        controller.show()
        return true
    }

    fun releasePlayer() {
        runCatching { stopPlayback() }
        openedUri = null
        onPlaybackReady = null
        onPlaybackError = null
    }

    private fun seekBy(deltaMs: Int) {
        val current = runCatching { currentPosition }.getOrDefault(0)
        val total = runCatching { duration }.getOrDefault(0)
        val upperBound = total.takeIf { it > 0 } ?: Int.MAX_VALUE
        val target = (current.toLong() + deltaMs)
            .coerceIn(0L, upperBound.toLong())
            .toInt()
        seekTo(target)
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000
        const val CONTROLLER_INITIAL_SHOW_MS = 1_500
    }
}

private fun localPlaybackFailureLabel(category: PlaybackFailureCategory): String = when (category) {
    PlaybackFailureCategory.UNSUPPORTED_MEDIA ->
        "Unsupported video container or codec on this device."
    PlaybackFailureCategory.STREAM_UNAVAILABLE ->
        "The selected file could not be read or parsed."
    PlaybackFailureCategory.TIMEOUT ->
        "The selected file took too long to open."
    PlaybackFailureCategory.NETWORK_UNAVAILABLE ->
        "The selected document provider is temporarily unavailable."
    PlaybackFailureCategory.AUTHENTICATION_FAILURE,
    PlaybackFailureCategory.UNKNOWN,
    -> "Unable to play this local video."
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
