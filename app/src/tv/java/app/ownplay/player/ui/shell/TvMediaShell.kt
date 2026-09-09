package app.ownplay.player.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

private val CollapsedRailWidth = 88.dp
private val ExpandedRailWidth = 232.dp
private val RailItemHeight = 58.dp

internal data class TvHomeShellFocusBoundary(
    val contentEntryGeneration: Int = 0,
    val requestRailFocus: () -> Unit = {},
)

internal val LocalTvHomeShellFocusBoundary = staticCompositionLocalOf {
    TvHomeShellFocusBoundary()
}

@Composable
internal fun TvMediaShell(
    activeDestination: TvDestination,
    railVisible: Boolean,
    onDestinationActivated: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val railFocusRequesters = remember {
        tvDestinations.associateWith { FocusRequester() }
    }

    var railExpanded by remember { mutableStateOf(true) }
    var focusedRailDestination by remember { mutableStateOf<TvDestination?>(null) }
    var pendingContentEntry by remember { mutableStateOf<TvDestination?>(null) }
    var pendingRailRestore by remember { mutableStateOf<TvDestination?>(null) }
    var homeContentEntryGeneration by remember { mutableIntStateOf(0) }
    var initialFocusApplied by remember { mutableStateOf(false) }

    LaunchedEffect(railVisible) {
        if (railVisible && !initialFocusApplied) {
            railExpanded = true
            railFocusRequesters.getValue(activeDestination).requestFocus()
            initialFocusApplied = true
        }
    }

    LaunchedEffect(activeDestination) {
        if (activeDestination != TvDestination.HOME) {
            homeContentEntryGeneration = 0
        }
    }

    LaunchedEffect(focusedRailDestination, railVisible) {
        if (railVisible && initialFocusApplied && focusedRailDestination == null) {
            withFrameNanos { }
            if (focusedRailDestination == null) {
                railExpanded = false
            }
        }
    }

    LaunchedEffect(pendingRailRestore, railVisible) {
        val destination = pendingRailRestore ?: return@LaunchedEffect
        if (railVisible) {
            railFocusRequesters.getValue(destination).requestFocus()
        }
        pendingRailRestore = null
    }

    LaunchedEffect(pendingContentEntry, activeDestination, railVisible) {
        val destination = pendingContentEntry ?: return@LaunchedEffect
        if (railVisible && destination == activeDestination) {
            if (destination == TvDestination.HOME) {
                homeContentEntryGeneration += 1
            } else {
                focusManager.moveFocus(FocusDirection.Right)
            }
        }
        pendingContentEntry = null
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (railVisible) CollapsedRailWidth else 0.dp),
        ) {
            CompositionLocalProvider(
                LocalTvHomeShellFocusBoundary provides TvHomeShellFocusBoundary(
                    contentEntryGeneration = homeContentEntryGeneration,
                    requestRailFocus = {
                        railExpanded = true
                        pendingRailRestore = activeDestination
                    },
                ),
            ) {
                content()
            }
        }

        if (railVisible) {
            TvNavigationRail(
                expanded = railExpanded,
                activeDestination = activeDestination,
                focusRequesters = railFocusRequesters,
                onItemFocused = { destination ->
                    val enteringCollapsedRail = !railExpanded
                    focusedRailDestination = destination
                    railExpanded = true
                    if (enteringCollapsedRail && destination != activeDestination) {
                        pendingRailRestore = activeDestination
                    }
                },
                onItemBlurred = { destination ->
                    if (focusedRailDestination == destination) {
                        focusedRailDestination = null
                    }
                },
                onActivate = onDestinationActivated,
                onEnterContent = { destination ->
                    onDestinationActivated(destination)
                    pendingContentEntry = destination
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

@Composable
private fun TvNavigationRail(
    expanded: Boolean,
    activeDestination: TvDestination,
    focusRequesters: Map<TvDestination, FocusRequester>,
    onItemFocused: (TvDestination) -> Unit,
    onItemBlurred: (TvDestination) -> Unit,
    onActivate: (TvDestination) -> Unit,
    onEnterContent: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(if (expanded) ExpandedRailWidth else CollapsedRailWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 22.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (expanded) "OwnPlay" else "OP",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tvDestinations.forEach { destination ->
                    TvNavigationRailItem(
                        destination = destination,
                        expanded = expanded,
                        selected = destination == activeDestination,
                        focusRequester = focusRequesters.getValue(destination),
                        onFocused = { onItemFocused(destination) },
                        onBlurred = { onItemBlurred(destination) },
                        onClick = { onActivate(destination) },
                        onRight = { onEnterContent(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvNavigationRailItem(
    destination: TvDestination,
    expanded: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onBlurred: () -> Unit,
    onClick: () -> Unit,
    onRight: () -> Unit,
) {
    var focused by remember(destination) { mutableStateOf(false) }

    val background = when {
        focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                } else {
                    onBlurred()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRight()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) {
                Arrangement.Start
            } else {
                Arrangement.Center
            },
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                modifier = Modifier.size(28.dp),
                tint = contentColor,
            )
            if (expanded) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
            }
        }
    }
}
