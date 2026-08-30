package app.ownplay.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgTimelineProjector
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackAudioSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackSubtitleSelection
import app.ownplay.player.playback.PlaybackTrackState
import app.ownplay.player.playback.PlaybackVideoOutput
import kotlinx.coroutines.delay

private const val LIVE_EPG_AUTO_HIDE_MILLIS = 4_500L
private const val LIVE_EPG_PROGRAM_LIMIT = 6

/**
 * Live fullscreen presentation.
 *
 * Live intentionally has no playback menu. The video owns the screen and EPG is the only transient
 * interaction layer. TV remote behavior is OK -> reveal EPG, Down -> enter EPG, Left/Right -> move
 * through available programmes, Up -> leave the EPG timeline. The final timeline card opens the
 * full guide by returning to Preview first. Mobile uses a tap on the video to reveal the same EPG.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackScreen(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    trackState: PlaybackTrackState,
    videoOutput: PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onAudioSelection: (PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (PlaybackSubtitleSelection) -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onReturnToChannels: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val rootFocusRequester = remember { FocusRequester() }
    val touchInteractionSource = remember { MutableInteractionSource() }
    val epgSnapshot by LiveEpgPresentationBridge.snapshot.collectAsState()
    val timeline = remember(epgSnapshot, selection.request.channelId) {
        EpgTimelineProjector.project(
            programs = epgSnapshot?.programs.orEmpty(),
            nowEpochSeconds = System.currentTimeMillis() / 1_000L,
        )
    }
    val overlayPrograms = remember(timeline) {
        buildList {
            timeline.current?.let(::add)
            timeline.future
                .asSequence()
                .filter { program -> program != timeline.current }
                .take(LIVE_EPG_PROGRAM_LIMIT - size)
                .forEach(::add)
        }.distinct()
    }
    val fullGuideIndex = overlayPrograms.size
    val currentProgramIndex = overlayPrograms.indexOf(timeline.current).takeIf { it >= 0 } ?: 0

    var epgVisible by remember(selection.request.channelId) { mutableStateOf(true) }
    var epgFocused by remember(selection.request.channelId) { mutableStateOf(false) }
    var selectedProgramIndex by remember(selection.request.channelId) {
        mutableIntStateOf(currentProgramIndex)
    }
    var interactionGeneration by remember(selection.request.channelId) { mutableIntStateOf(0) }

    fun revealEpg() {
        epgVisible = true
        interactionGeneration += 1
    }

    fun openFullGuide() {
        LiveEpgPresentationBridge.requestFullGuide()
        onReturnToChannels()
    }

    FullscreenSystemBarsEffect(enabled = true)

    LaunchedEffect(Unit) {
        onFullscreenStateChanged(true)
    }
    DisposableEffect(onFullscreenStateChanged) {
        onDispose { onFullscreenStateChanged(false) }
    }

    LaunchedEffect(selection.request.channelId) {
        epgVisible = true
        epgFocused = false
        selectedProgramIndex = currentProgramIndex
        interactionGeneration += 1
        withFrameNanos { }
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(overlayPrograms, currentProgramIndex) {
        if (!epgFocused) selectedProgramIndex = currentProgramIndex
    }

    LaunchedEffect(epgVisible, epgFocused, interactionGeneration, state) {
        if (epgVisible && !epgFocused && state is PlaybackState.Playing) {
            delay(LIVE_EPG_AUTO_HIDE_MILLIS)
            epgVisible = false
        }
    }

    BackHandler(onBack = onReturnToChannels)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_BUTTON_SELECT,
                        -> {
                            when {
                                !epgVisible -> revealEpg()
                                epgFocused && selectedProgramIndex == fullGuideIndex -> openFullGuide()
                                else -> revealEpg()
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!epgVisible) {
                                false
                            } else {
                                epgFocused = true
                                selectedProgramIndex = selectedProgramIndex.coerceIn(0, fullGuideIndex)
                                interactionGeneration += 1
                                true
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (epgFocused) {
                                epgFocused = false
                                interactionGeneration += 1
                                true
                            } else {
                                false
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (epgFocused) {
                                selectedProgramIndex = (selectedProgramIndex - 1).coerceAtLeast(0)
                                interactionGeneration += 1
                                true
                            } else {
                                false
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (epgFocused) {
                                selectedProgramIndex =
                                    (selectedProgramIndex + 1).coerceAtMost(fullGuideIndex)
                                interactionGeneration += 1
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                }
                .focusable(),
        ) {
            LiveFullscreenVideoSurface(
                videoOutput = videoOutput,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selection.request.channelId) {
                        detectTapGestures(onTap = { revealEpg() })
                    }
                    .clickable(
                        interactionSource = touchInteractionSource,
                        indication = null,
                        onClick = ::revealEpg,
                    ),
            )

            val controls = PlaybackPresentationPolicy.controlsFor(state)
            if (controls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (state is PlaybackState.Failed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = playbackStatusLabel(state),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            AnimatedVisibility(
                visible = epgVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LiveFullscreenEpgOverlay(
                    channelName = selection.displayName,
                    programs = overlayPrograms,
                    selectedIndex = selectedProgramIndex,
                    focused = epgFocused,
                    fullGuideIndex = fullGuideIndex,
                    onProgramSelected = { index ->
                        selectedProgramIndex = index
                        epgFocused = true
                        revealEpg()
                    },
                    onFullGuide = ::openFullGuide,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveFullscreenVideoSurface(
    videoOutput: PlaybackVideoOutput,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AndroidColor.BLACK)
                videoOutput.bind(this)
            }
        },
        modifier = modifier,
        update = { view ->
            view.useController = false
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view -> videoOutput.unbind(view) },
    )
}

@Composable
private fun LiveFullscreenEpgOverlay(
    channelName: String,
    programs: List<EpgProgram>,
    selectedIndex: Int,
    focused: Boolean,
    fullGuideIndex: Int,
    onProgramSelected: (Int) -> Unit,
    onFullGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, focused) {
        if (focused) {
            listState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
        }
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (focused) "EPG · ← → browse · ↑ return to video" else "EPG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                }
            }

            if (programs.isEmpty()) {
                Text(
                    text = "EPG unavailable for this channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                )
            } else {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(programs) { index, program ->
                        LiveFullscreenProgramCard(
                            program = program,
                            selected = focused && selectedIndex == index,
                            current = index == 0,
                            onClick = { onProgramSelected(index) },
                        )
                    }
                    item(key = "full-epg") {
                        LiveFullscreenFullGuideCard(
                            selected = focused && selectedIndex == fullGuideIndex,
                            onClick = onFullGuide,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFullscreenProgramCard(
    program: EpgProgram,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(230.dp)
            .heightIn(min = 78.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(13.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
            current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (current) "NOW · ${timeRange(program)}" else timeRange(program),
                style = MaterialTheme.typography.labelSmall,
                color = if (current || selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveFullscreenFullGuideCard(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(150.dp)
            .heightIn(min = 78.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "FULL EPG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Open guide  →",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Returns to Preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun timeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "—"
}

@Composable
private fun FullscreenSystemBarsEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        val controller = activity?.let { host ->
            WindowCompat.getInsetsController(host.window, host.window.decorView)
        }

        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
