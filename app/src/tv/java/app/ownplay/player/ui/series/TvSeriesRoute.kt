package app.ownplay.player.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.onDemandPresentationSession
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.playback.OnDemandContentKind
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesDetails
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.tv.LocalTvShellFocusBoundary
import app.ownplay.player.ui.vod.RemotePoster
import kotlinx.coroutines.launch

private const val SERIES_CONTINUE_KEY = "series:continue"
private const val SERIES_FAVORITES_KEY = "series:favorites"
private const val SERIES_CATALOG_KEY = "series:catalog"
private const val SERIES_CATEGORY_PREFIX = "series:category:"
private const val SERIES_GRID_COLUMNS = 5
private const val EPISODE_GRID_COLUMNS = 4

private data class TvSeriesSection(
    val key: String,
    val label: String,
)

/**
 * TV-only Series catalog and details presentation.
 *
 * Provider/catalog/progress/playback ownership stays in the established shared runtime. This route
 * owns only TV browse/detail geometry, seasons/episodes presentation and remote focus restoration.
 * Downloads/Offline management are intentionally absent from the OwnPlay TV product surface.
 */
@Composable
internal fun TvSeriesRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    requestedSeriesId: String? = null,
    onRequestedSeriesConsumed: () -> Unit = {},
    returnToLibraryOnDetailBack: Boolean = false,
    onReturnToLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val featureRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val onDemandPresentation by runtime.onDemandPresentationSession.state.collectAsState()
    val shellFocusBoundary = LocalTvShellFocusBoundary.current
    val onShellLeftBoundary: () -> Boolean = {
        if (shellFocusBoundary.railVisible) {
            shellFocusBoundary.requestRailFocus()
            true
        } else {
            false
        }
    }

    DisposableEffect(featureRuntime) {
        onDispose { featureRuntime.close() }
    }

    if (sourceId == null) {
        TvSeriesMessage(
            title = "No playlist configured",
            detail = "Add an Xtream playlist from Settings to load Series.",
            actionLabel = "Open Settings",
            contentEntryGeneration = shellFocusBoundary.contentEntryGeneration,
            onLeftBoundary = onShellLeftBoundary,
            onAction = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    if (sourceKind != SourceKinds.XTREAM) {
        TvSeriesMessage(
            title = "Series are not available for this playlist",
            detail = "The active playlist does not currently provide an Xtream Series catalog.",
            actionLabel = "Open Settings",
            contentEntryGeneration = shellFocusBoundary.contentEntryGeneration,
            onLeftBoundary = onShellLeftBoundary,
            onAction = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    val catalog by featureRuntime.observeCatalog(sourceId).collectAsState(initial = SeriesCatalog())
    var loading by remember(sourceId) { mutableStateOf(false) }
    var refreshError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var selectedSectionKey by remember(sourceId) { mutableStateOf<String?>(null) }
    var selectedSeries by remember(sourceId) { mutableStateOf<SeriesSummary?>(null) }
    var details by remember(sourceId) { mutableStateOf<SeriesDetails?>(null) }
    var detailsLoading by remember(sourceId) { mutableStateOf(false) }
    var detailsError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var selectedSeasonNumber by remember(sourceId) { mutableStateOf<Int?>(null) }
    var selectedEpisodeId by remember(sourceId) { mutableStateOf<String?>(null) }
    var seriesFocusTargetId by remember(sourceId) { mutableStateOf<String?>(null) }
    var seriesFocusRequestGeneration by remember(sourceId) { mutableIntStateOf(0) }
    val detailsBackOwner = remember(sourceId) { Any() }

    fun refreshCatalog() {
        scope.launch {
            loading = true
            refreshError = null
            when (val result = featureRuntime.refresh(sourceId)) {
                is SourceResult.Success -> Unit
                is SourceResult.Failure -> refreshError = result.error
            }
            loading = false
        }
    }

    fun openSeries(item: SeriesSummary) {
        selectedSeries = item
        selectedSeasonNumber = null
        selectedEpisodeId = null
        runtime.onDemandPresentationSession.showSeriesDetail(
            sourceId = sourceId,
            seriesId = item.seriesId,
            returnToLibraryOnDetailBack = returnToLibraryOnDetailBack,
        )
    }

    fun closeSeriesDetails() {
        val originSeriesId = selectedSeries?.seriesId
        if (returnToLibraryOnDetailBack) {
            runtime.onDemandPresentationSession.clear()
            onReturnToLibrary()
            return
        }
        selectedSeries = null
        selectedSeasonNumber = null
        selectedEpisodeId = null
        runtime.onDemandPresentationSession.showSeriesCatalog(sourceId)
        if (originSeriesId != null) {
            seriesFocusTargetId = originSeriesId
            seriesFocusRequestGeneration += 1
        }
    }

    fun startEpisode(
        episode: SeriesEpisode,
        fromBeginning: Boolean,
        returnToCatalog: Boolean,
    ) {
        val playbackEpisode = if (fromBeginning) {
            episode.copy(
                positionMs = null,
                progressCompleted = false,
            )
        } else {
            episode
        }
        runtime.playbackController.start(
            PlaybackRequest(
                sourceId = sourceId,
                channelId = episode.episodeId,
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
                providerStreamId = episode.providerEpisodeId,
                containerExtension = episode.containerExtension,
            ),
        )
        runtime.onDemandPresentationSession.showSeriesPlayback(
            sourceId = sourceId,
            episode = playbackEpisode,
            returnToLibraryOnDetailBack = returnToLibraryOnDetailBack,
            returnToCatalog = returnToCatalog,
            selectedSeasonNumber = episode.seasonNumber,
            selectedEpisodeId = episode.episodeId,
        )
    }

    fun setSeriesFavorite(item: SeriesSummary, favorite: Boolean) {
        scope.launch {
            if (!featureRuntime.setFavorite(sourceId, item.seriesId, favorite)) return@launch
            selectedSeries = selectedSeries?.let { current ->
                if (current.seriesId == item.seriesId) current.copy(isFavorite = favorite) else current
            }
            details = details?.let { current ->
                if (current.series.seriesId == item.seriesId) {
                    current.copy(series = current.series.copy(isFavorite = favorite))
                } else {
                    current
                }
            }
        }
    }

    DisposableEffect(selectedSeries?.seriesId, detailsBackOwner, returnToLibraryOnDetailBack) {
        if (selectedSeries != null) {
            PlaybackInteractionBridge.registerBackAction(detailsBackOwner, ::closeSeriesDetails)
        }
        onDispose {
            PlaybackInteractionBridge.clearBackAction(detailsBackOwner)
        }
    }

    LaunchedEffect(sourceId) {
        loading = true
        refreshError = null
        when (val result = featureRuntime.refresh(sourceId)) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> refreshError = result.error
        }
        loading = false
    }

    val sections = remember(catalog.categories, catalog.continueWatching, catalog.series) {
        buildList {
            if (catalog.continueWatching.isNotEmpty()) {
                add(TvSeriesSection(SERIES_CONTINUE_KEY, "Continue Watching"))
            }
            if (catalog.series.any(SeriesSummary::isFavorite)) {
                add(TvSeriesSection(SERIES_FAVORITES_KEY, "Favorites"))
            }
            catalog.categories.forEach { category ->
                add(
                    TvSeriesSection(
                        key = SERIES_CATEGORY_PREFIX + category.providerCategoryKey,
                        label = category.name,
                    ),
                )
            }
            if (catalog.categories.isEmpty() && catalog.series.isNotEmpty()) {
                add(TvSeriesSection(SERIES_CATALOG_KEY, "Series"))
            }
        }
    }

    LaunchedEffect(sections, selectedSectionKey) {
        if (selectedSectionKey == null || sections.none { it.key == selectedSectionKey }) {
            selectedSectionKey = sections.firstOrNull()?.key
        }
    }

    val visibleSeries = remember(catalog.series, selectedSectionKey) {
        when (selectedSectionKey) {
            SERIES_FAVORITES_KEY -> catalog.series.filter(SeriesSummary::isFavorite)
            SERIES_CATALOG_KEY -> catalog.series
            SERIES_CONTINUE_KEY, null -> emptyList()
            else -> {
                val categoryKey = selectedSectionKey.orEmpty().removePrefix(SERIES_CATEGORY_PREFIX)
                catalog.series.filter { item -> item.categoryKey == categoryKey }
            }
        }
    }
    val visibleContinueEpisodes = remember(catalog.continueWatching, selectedSectionKey) {
        if (selectedSectionKey == SERIES_CONTINUE_KEY) catalog.continueWatching else emptyList()
    }

    val sessionSeriesId = onDemandPresentation.itemId.takeIf {
        onDemandPresentation.kind == OnDemandContentKind.SERIES &&
            onDemandPresentation.sourceId == sourceId &&
            !onDemandPresentation.isSeriesPlayback
    }
    val requestedTargetId = requestedSeriesId ?: sessionSeriesId

    LaunchedEffect(requestedTargetId, catalog.series, catalog.categories) {
        val targetId = requestedTargetId ?: return@LaunchedEffect
        val target = catalog.series.firstOrNull { item -> item.seriesId == targetId }
            ?: return@LaunchedEffect
        if (selectedSeries?.seriesId != target.seriesId) {
            selectedSectionKey = target.categoryKey
                ?.let { SERIES_CATEGORY_PREFIX + it }
                ?.takeIf { key -> sections.any { section -> section.key == key } }
                ?: sections.firstOrNull { it.key != SERIES_CONTINUE_KEY }?.key
            selectedSeries = target
        }
        selectedSeasonNumber = onDemandPresentation.seriesSeasonNumber
        selectedEpisodeId = onDemandPresentation.seriesEpisodeId
        if (requestedSeriesId == targetId) {
            onRequestedSeriesConsumed()
        }
    }

    LaunchedEffect(catalog.series, selectedSeries?.seriesId) {
        val selectedId = selectedSeries?.seriesId ?: return@LaunchedEffect
        catalog.series.firstOrNull { item -> item.seriesId == selectedId }?.let { refreshed ->
            selectedSeries = refreshed
        }
    }

    LaunchedEffect(selectedSeries?.seriesId) {
        val selected = selectedSeries
        if (selected == null) {
            details = null
            detailsError = null
            detailsLoading = false
            return@LaunchedEffect
        }
        details = null
        detailsLoading = true
        detailsError = null
        val cached = featureRuntime.cachedDetails(sourceId, selected.seriesId)
        if (cached != null) {
            details = cached
            detailsLoading = false
        }
        when (val result = featureRuntime.details(sourceId, selected.seriesId)) {
            is SourceResult.Success -> details = result.value
            is SourceResult.Failure -> if (cached == null) {
                detailsError = result.error
                details = null
            }
        }
        detailsLoading = false
    }

    LaunchedEffect(details, selectedSeasonNumber, selectedEpisodeId) {
        val loaded = details ?: return@LaunchedEffect
        val currentSeason = selectedSeasonNumber
            ?.let { number -> loaded.seasons.firstOrNull { it.seasonNumber == number } }
            ?: loaded.seasons.minByOrNull { it.seasonNumber }
        if (currentSeason == null) {
            if (selectedSeasonNumber != null || selectedEpisodeId != null) {
                selectedSeasonNumber = null
                selectedEpisodeId = null
                runtime.onDemandPresentationSession.updateSeriesSelection(null, null)
            }
            return@LaunchedEffect
        }

        val normalizedSeasonNumber = currentSeason.seasonNumber
        val normalizedEpisodeId = selectedEpisodeId?.takeIf { episodeId ->
            currentSeason.episodes.any { episode -> episode.episodeId == episodeId }
        }
        if (
            selectedSeasonNumber != normalizedSeasonNumber ||
            selectedEpisodeId != normalizedEpisodeId
        ) {
            selectedSeasonNumber = normalizedSeasonNumber
            selectedEpisodeId = normalizedEpisodeId
            runtime.onDemandPresentationSession.updateSeriesSelection(
                normalizedSeasonNumber,
                normalizedEpisodeId,
            )
        }
    }

    val openedSeries = selectedSeries
    if (openedSeries != null) {
        TvSeriesDetailsScreen(
            series = openedSeries,
            details = details,
            loading = detailsLoading,
            failed = detailsError != null,
            selectedSeasonNumber = selectedSeasonNumber,
            selectedEpisodeId = selectedEpisodeId,
            onSeasonSelected = { seasonNumber ->
                selectedSeasonNumber = seasonNumber
                selectedEpisodeId = null
                runtime.onDemandPresentationSession.updateSeriesSelection(seasonNumber, null)
            },
            onEpisodeSelected = { episode ->
                selectedEpisodeId = episode.episodeId
                runtime.onDemandPresentationSession.updateSeriesSelection(
                    episode.seasonNumber,
                    episode.episodeId,
                )
                startEpisode(episode, fromBeginning = false, returnToCatalog = false)
            },
            onContinue = { episode ->
                selectedSeasonNumber = episode.seasonNumber
                selectedEpisodeId = episode.episodeId
                startEpisode(episode, fromBeginning = false, returnToCatalog = false)
            },
            onStartFromBeginning = { episode ->
                selectedSeasonNumber = episode.seasonNumber
                selectedEpisodeId = episode.episodeId
                startEpisode(episode, fromBeginning = true, returnToCatalog = false)
            },
            onFavoriteChanged = { favorite -> setSeriesFavorite(openedSeries, favorite) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    TvSeriesCatalogScreen(
        sections = sections,
        selectedSectionKey = selectedSectionKey,
        series = visibleSeries,
        continueEpisodes = visibleContinueEpisodes,
        totalSeriesCount = catalog.series.size,
        loading = loading,
        refreshFailed = refreshError != null,
        seriesFocusTargetId = seriesFocusTargetId,
        seriesFocusRequestGeneration = seriesFocusRequestGeneration,
        contentEntryGeneration = shellFocusBoundary.contentEntryGeneration,
        onLeftBoundary = onShellLeftBoundary,
        onSectionSelected = { selectedSectionKey = it },
        onSeriesFocusRequested = { seriesId ->
            seriesFocusTargetId = seriesId
            seriesFocusRequestGeneration += 1
        },
        onSeriesSelected = ::openSeries,
        onContinueEpisodeSelected = { episode ->
            startEpisode(episode, fromBeginning = false, returnToCatalog = true)
        },
        onRetry = ::refreshCatalog,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun TvSeriesCatalogScreen(
    sections: List<TvSeriesSection>,
    selectedSectionKey: String?,
    series: List<SeriesSummary>,
    continueEpisodes: List<SeriesEpisode>,
    totalSeriesCount: Int,
    loading: Boolean,
    refreshFailed: Boolean,
    seriesFocusTargetId: String?,
    seriesFocusRequestGeneration: Int,
    contentEntryGeneration: Int,
    onLeftBoundary: () -> Boolean,
    onSectionSelected: (String) -> Unit,
    onSeriesFocusRequested: (String) -> Unit,
    onSeriesSelected: (SeriesSummary) -> Unit,
    onContinueEpisodeSelected: (SeriesEpisode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionFocusRequester = remember { FocusRequester() }
    val seriesFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val selectedSectionLabel = sections.firstOrNull { it.key == selectedSectionKey }?.label ?: "Series"
    val contentHasItems = if (selectedSectionKey == SERIES_CONTINUE_KEY) {
        continueEpisodes.isNotEmpty()
    } else {
        series.isNotEmpty()
    }

    LaunchedEffect(seriesFocusRequestGeneration, seriesFocusTargetId, series) {
        if (seriesFocusRequestGeneration <= 0) return@LaunchedEffect
        val targetId = seriesFocusTargetId ?: return@LaunchedEffect
        val index = series.indexOfFirst { item -> item.seriesId == targetId }
        if (index < 0) return@LaunchedEffect
        gridState.scrollToItem(index)
        withFrameNanos { }
        withFrameNanos { }
        seriesFocusRequester.requestFocus()
    }

    LaunchedEffect(contentEntryGeneration, selectedSectionKey, sections, refreshFailed) {
        if (contentEntryGeneration <= 0) return@LaunchedEffect
        when {
            selectedSectionKey != null && sections.any { it.key == selectedSectionKey } -> {
                withFrameNanos { }
                sectionFocusRequester.requestFocus()
            }
            sections.isEmpty() && refreshFailed -> {
                withFrameNanos { }
                retryFocusRequester.requestFocus()
            }
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Series",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$totalSeriesCount titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sections, key = TvSeriesSection::key) { section ->
                        TvSeriesSectionRow(
                            section = section,
                            selected = section.key == selectedSectionKey,
                            focusRequester = sectionFocusRequester.takeIf {
                                section.key == selectedSectionKey
                            },
                            onClick = { onSectionSelected(section.key) },
                            onLeft = onLeftBoundary,
                            onRight = {
                                when {
                                    section.key == SERIES_CONTINUE_KEY -> false
                                    series.isNotEmpty() -> {
                                        onSeriesFocusRequested(series.first().seriesId)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedSectionLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when (selectedSectionKey) {
                            SERIES_CONTINUE_KEY -> "${continueEpisodes.size} episodes"
                            else -> "${series.size} available"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    refreshFailed -> TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .focusRequester(retryFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionLeft
                                ) {
                                    onLeftBoundary()
                                } else {
                                    false
                                }
                            },
                    ) { Text("Retry refresh") }
                }
            }

            when {
                loading && totalSeriesCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                !contentHasItems -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (refreshFailed && totalSeriesCount == 0) {
                            "Series could not be loaded"
                        } else {
                            "No Series in this section"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selectedSectionKey == SERIES_CONTINUE_KEY -> LazyVerticalGrid(
                    columns = GridCells.Fixed(EPISODE_GRID_COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = continueEpisodes,
                        key = { _, episode -> episode.episodeId },
                    ) { _, episode ->
                        TvContinueEpisodeCard(
                            episode = episode,
                            onClick = { onContinueEpisodeSelected(episode) },
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(SERIES_GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = series,
                        key = { _, item -> item.seriesId },
                    ) { index, item ->
                        TvSeriesCard(
                            series = item,
                            restoreFocus = item.seriesId == seriesFocusTargetId,
                            focusRequester = seriesFocusRequester,
                            onLeftBoundary = {
                                if (index % SERIES_GRID_COLUMNS == 0) {
                                    sectionFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                            onClick = { onSeriesSelected(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeriesSectionRow(
    section: TvSeriesSection,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
) {
    var focused by remember(section.key) { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    val background = when {
        focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
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
            .height(52.dp)
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> onLeft()
                        Key.DirectionRight -> onRight()
                        else -> false
                    }
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = background,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvSeriesCard(
    series: SeriesSummary,
    restoreFocus: Boolean,
    focusRequester: FocusRequester,
    onLeftBoundary: () -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(series.seriesId) { mutableStateOf(false) }
    val requesterModifier = if (restoreFocus) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft &&
                    onLeftBoundary()
                ) {
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RemotePoster(
                url = series.posterUrl,
                title = series.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = series.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (series.isFavorite) append("Favorite")
                    series.rating?.let { rating ->
                        if (isNotEmpty()) append("  ·  ")
                        append("★ %.1f".format(rating))
                    }
                    if (isEmpty()) append("Series")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvContinueEpisodeCard(
    episode: SeriesEpisode,
    onClick: () -> Unit,
) {
    var focused by remember(episode.episodeId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RemotePoster(
                url = episode.posterUrl,
                title = episode.seriesTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Text(
                text = episode.seriesTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "S${episode.seasonNumber} · E${episode.episodeNumber} · ${episode.title}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episodeProgressFraction(episode)?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSeriesDetailsScreen(
    series: SeriesSummary,
    details: SeriesDetails?,
    loading: Boolean,
    failed: Boolean,
    selectedSeasonNumber: Int?,
    selectedEpisodeId: String?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (SeriesEpisode) -> Unit,
    onContinue: (SeriesEpisode) -> Unit,
    onStartFromBeginning: (SeriesEpisode) -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryFocusRequester = remember(series.seriesId) { FocusRequester() }
    val seasons = details?.seasons.orEmpty().sortedBy { it.seasonNumber }
    val selectedSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
        ?: seasons.firstOrNull()
    val episodes = selectedSeason?.episodes.orEmpty().sortedBy { it.episodeNumber }
    val resumeEpisode = details?.seasons
        .orEmpty()
        .flatMap { it.episodes }
        .filter(SeriesEpisode::resumeAvailable)
        .maxByOrNull { it.progressUpdatedAtEpochMillis ?: Long.MIN_VALUE }
    val firstEpisode = seasons.firstOrNull()?.episodes?.minByOrNull { it.episodeNumber }
    val primaryEpisode = resumeEpisode ?: firstEpisode

    LaunchedEffect(series.seriesId, primaryEpisode?.episodeId) {
        withFrameNanos { }
        primaryFocusRequester.requestFocus()
    }

    Row(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        RemotePoster(
            url = details?.posterUrl ?: series.posterUrl,
            title = series.name,
            modifier = Modifier
                .width(250.dp)
                .aspectRatio(2f / 3f),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = details?.series?.name ?: series.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = seriesMetaLine(series, details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                primaryEpisode?.let { episode ->
                    TvSeriesActionRow(
                        label = if (episode.resumeAvailable) {
                            "Continue S${episode.seasonNumber} E${episode.episodeNumber}"
                        } else {
                            "Play S${episode.seasonNumber} E${episode.episodeNumber}"
                        },
                        focusRequester = primaryFocusRequester,
                        onClick = { onContinue(episode) },
                    )
                }
                firstEpisode?.let { episode ->
                    TvSeriesActionRow(
                        label = "Start from Episode 1",
                        onClick = { onStartFromBeginning(episode) },
                    )
                }
                TvSeriesActionRow(
                    label = if (series.isFavorite) "Remove favorite" else "Add favorite",
                    focusRequester = primaryFocusRequester.takeIf { primaryEpisode == null },
                    onClick = { onFavoriteChanged(!series.isFavorite) },
                )
            }

            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                failed -> Text(
                    text = "Detailed metadata is unavailable. Catalog browsing remains available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            details?.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (seasons.isNotEmpty()) {
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { season -> season.seasonId }) { season ->
                        TvSeasonChip(
                            label = season.name?.takeIf(String::isNotBlank)
                                ?: "Season ${season.seasonNumber}",
                            selected = season.seasonNumber == selectedSeason?.seasonNumber,
                            onClick = { onSeasonSelected(season.seasonNumber) },
                        )
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    episodes.forEach { episode ->
                        TvEpisodeRow(
                            episode = episode,
                            selected = episode.episodeId == selectedEpisodeId,
                            onClick = { onEpisodeSelected(episode) },
                        )
                    }
                }
            }

            details?.director?.takeIf(String::isNotBlank)?.let { director ->
                Text(
                    text = "Director · $director",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            details?.cast?.takeIf(String::isNotBlank)?.let { cast ->
                Text(
                    text = "Cast · $cast",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Back returns to the Series context.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvSeriesActionRow(
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun TvSeasonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
        },
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TvEpisodeRow(
    episode: SeriesEpisode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(episode.episodeId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "E${episode.episodeNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        episode.resumeAvailable -> "Resume"
                        episode.progressCompleted -> "Watched"
                        else -> "Play"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            episodeProgressFraction(episode)?.let { progress ->
                Text(
                    text = "${(progress * 100f).toInt().coerceIn(1, 99)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TvSeriesMessage(
    title: String,
    detail: String,
    actionLabel: String,
    contentEntryGeneration: Int,
    onLeftBoundary: () -> Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(contentEntryGeneration) {
        if (contentEntryGeneration <= 0) return@LaunchedEffect
        withFrameNanos { }
        actionFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier
                    .focusRequester(actionFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionLeft
                        ) {
                            onLeftBoundary()
                        } else {
                            false
                        }
                    },
            ) { Text(actionLabel) }
        }
    }
}

private fun seriesMetaLine(series: SeriesSummary, details: SeriesDetails?): String =
    listOfNotNull(
        details?.releaseDate,
        details?.genre,
        details?.country,
        (details?.rating ?: series.rating)?.let { rating -> "★ %.1f".format(rating) },
    ).joinToString("  ·  ").ifBlank { "Series" }

private fun episodeProgressFraction(episode: SeriesEpisode): Float? {
    val position = episode.positionMs ?: return null
    val duration = episode.durationMs ?: return null
    if (position <= 0L || duration <= 0L || episode.progressCompleted) return null
    return (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
}
