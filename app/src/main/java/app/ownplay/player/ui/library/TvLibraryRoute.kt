package app.ownplay.player.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.ui.view.ContentViewMode
import app.ownplay.player.ui.view.ContentViewModeMenu
import app.ownplay.player.ui.view.ContentViewModeStore
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal enum class TvLibrarySection {
    OFFLINE,
    MOVIES,
    SERIES,
}

@Composable
internal fun TvLibraryRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    onOpenMovieDetails: (sourceId: String, movieId: String) -> Unit,
    onOpenSeriesDetails: (sourceId: String, seriesId: String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadsRuntime = remember(context) { OfflineDownloadFeatureRuntime(context.applicationContext) }
    val vodRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val seriesRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }
    val viewModeStore = remember(context) { ContentViewModeStore(context.applicationContext) }

    DisposableEffect(downloadsRuntime, vodRuntime, seriesRuntime) {
        onDispose {
            downloadsRuntime.close()
            vodRuntime.close()
            seriesRuntime.close()
        }
    }

    val downloads by downloadsRuntime.observeAll().collectAsState(initial = emptyList())
    val viewMode by viewModeStore.libraryMode.collectAsState(initial = ContentViewMode.CARDS)
    val vodFlow = remember(sourceId, vodRuntime) {
        sourceId?.let(vodRuntime::observeCatalog) ?: flowOf(VodCatalog())
    }
    val seriesFlow = remember(sourceId, seriesRuntime) {
        sourceId?.let(seriesRuntime::observeCatalog) ?: flowOf(SeriesCatalog())
    }
    val vodCatalog by vodFlow.collectAsState(initial = VodCatalog())
    val seriesCatalog by seriesFlow.collectAsState(initial = SeriesCatalog())

    var section by remember { mutableStateOf(TvLibrarySection.OFFLINE) }
    var movieCategoryKey by remember(sourceId) { mutableStateOf<String?>(null) }
    var seriesCategoryKey by remember(sourceId) { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var playbackSession by remember { mutableStateOf<LibraryPlaybackSession?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vodCatalog.categories, movieCategoryKey) {
        val categories = vodCatalog.categories
        movieCategoryKey = when {
            categories.isEmpty() -> null
            movieCategoryKey != null && categories.any { it.providerCategoryKey == movieCategoryKey } -> movieCategoryKey
            else -> categories.first().providerCategoryKey
        }
    }
    LaunchedEffect(seriesCatalog.categories, seriesCategoryKey) {
        val categories = seriesCatalog.categories
        seriesCategoryKey = when {
            categories.isEmpty() -> null
            seriesCategoryKey != null && categories.any { it.providerCategoryKey == seriesCategoryKey } -> seriesCategoryKey
            else -> categories.first().providerCategoryKey
        }
    }

    playbackSession?.let { session ->
        LibraryPlaybackScreen(
            runtime = runtime,
            session = session,
            onExit = {
                runtime.playbackController.stop()
                playbackSession = null
            },
            onProgress = { positionMs, durationMs ->
                scope.launch {
                    downloadsRuntime.savePlaybackProgress(
                        downloadId = session.download.downloadId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                    )
                }
            },
            onFullscreenStateChanged = onFullscreenStateChanged,
            contextLabel = "Library · Offline",
        )
        return
    }

    val normalizedQuery = query.trim().lowercase()
    val offlineItems = remember(downloads, normalizedQuery) {
        downloads.filter { download ->
            download.state == DownloadStates.COMPLETED &&
                (normalizedQuery.isBlank() ||
                    download.title.lowercase().contains(normalizedQuery) ||
                    download.seriesTitle.orEmpty().lowercase().contains(normalizedQuery))
        }
    }
    val movies = remember(vodCatalog.movies, movieCategoryKey, normalizedQuery) {
        vodCatalog.movies.filter { movie ->
            val categoryMatch = movieCategoryKey == null || movie.categoryKey == movieCategoryKey
            val queryMatch = normalizedQuery.isBlank() || movie.name.lowercase().contains(normalizedQuery)
            categoryMatch && queryMatch
        }
    }
    val series = remember(seriesCatalog.series, seriesCategoryKey, normalizedQuery) {
        seriesCatalog.series.filter { item ->
            val categoryMatch = seriesCategoryKey == null || item.categoryKey == seriesCategoryKey
            val queryMatch = normalizedQuery.isBlank() || item.name.lowercase().contains(normalizedQuery)
            categoryMatch && queryMatch
        }
    }

    fun playOffline(download: OfflineDownload) {
        scope.launch {
            val request = downloadsRuntime.playbackRequest(download.downloadId)
            if (request == null) {
                playbackError = "Offline file unavailable. Download it again."
                return@launch
            }
            val progress = downloadsRuntime.playbackProgress(download.downloadId)
            playbackError = null
            runtime.playbackController.start(request)
            playbackSession = LibraryPlaybackSession(
                download = download,
                initialPositionMs = progress?.takeIf { !it.completed }?.positionMs ?: 0L,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    when (section) {
                        TvLibrarySection.OFFLINE -> "Media available on this TV without internet"
                        TvLibrarySection.MOVIES -> "Movies"
                        TvLibrarySection.SERIES -> "Series"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ContentViewModeMenu(
                mode = viewMode,
                onModeSelected = { selected -> scope.launch { viewModeStore.setLibraryMode(selected) } },
                prefix = "View",
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = {
                Text(
                    when (section) {
                        TvLibrarySection.OFFLINE -> "Search Offline"
                        TvLibrarySection.MOVIES -> "Search Movies"
                        TvLibrarySection.SERIES -> "Search Series"
                    },
                )
            },
            shape = RoundedCornerShape(12.dp),
        )

        playbackError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvLibraryNavigationRail(
                section = section,
                movieCategories = vodCatalog.categories.map { it.providerCategoryKey to it.name },
                seriesCategories = seriesCatalog.categories.map { it.providerCategoryKey to it.name },
                movieCategoryKey = movieCategoryKey,
                seriesCategoryKey = seriesCategoryKey,
                onSectionSelected = {
                    section = it
                    query = ""
                },
                onMovieCategorySelected = { movieCategoryKey = it },
                onSeriesCategorySelected = { seriesCategoryKey = it },
                modifier = Modifier
                    .width(214.dp)
                    .fillMaxHeight(),
            )

            when (section) {
                TvLibrarySection.OFFLINE -> TvOfflineContent(
                    downloads = offlineItems,
                    viewMode = viewMode,
                    onOpen = ::playOffline,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                TvLibrarySection.MOVIES -> TvMovieContent(
                    movies = movies,
                    viewMode = viewMode,
                    onOpen = { movie -> sourceId?.let { onOpenMovieDetails(it, movie.movieId) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                TvLibrarySection.SERIES -> TvSeriesContent(
                    series = series,
                    viewMode = viewMode,
                    onOpen = { item -> sourceId?.let { onOpenSeriesDetails(it, item.seriesId) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TvLibraryNavigationRail(
    section: TvLibrarySection,
    movieCategories: List<Pair<String, String>>,
    seriesCategories: List<Pair<String, String>>,
    movieCategoryKey: String?,
    seriesCategoryKey: String?,
    onSectionSelected: (TvLibrarySection) -> Unit,
    onMovieCategorySelected: (String) -> Unit,
    onSeriesCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item { Text("Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                TvLibraryRailItem("Offline", Icons.Filled.DownloadDone, section == TvLibrarySection.OFFLINE) {
                    onSectionSelected(TvLibrarySection.OFFLINE)
                }
            }
            item {
                TvLibraryRailItem("Movies", Icons.Filled.Movie, section == TvLibrarySection.MOVIES) {
                    onSectionSelected(TvLibrarySection.MOVIES)
                }
            }
            item {
                TvLibraryRailItem("Series", Icons.Filled.Tv, section == TvLibrarySection.SERIES) {
                    onSectionSelected(TvLibrarySection.SERIES)
                }
            }

            val categories = when (section) {
                TvLibrarySection.OFFLINE -> emptyList()
                TvLibrarySection.MOVIES -> movieCategories
                TvLibrarySection.SERIES -> seriesCategories
            }
            if (categories.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(categories, key = { it.first }) { (key, name) ->
                    val selected = when (section) {
                        TvLibrarySection.MOVIES -> movieCategoryKey == key
                        TvLibrarySection.SERIES -> seriesCategoryKey == key
                        TvLibrarySection.OFFLINE -> false
                    }
                    TvCategoryRailItem(name = name, selected = selected) {
                        when (section) {
                            TvLibrarySection.MOVIES -> onMovieCategorySelected(key)
                            TvLibrarySection.SERIES -> onSeriesCategorySelected(key)
                            TvLibrarySection.OFFLINE -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryRailItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected || focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun TvCategoryRailItem(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected || focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvOfflineContent(
    downloads: List<OfflineDownload>,
    viewMode: ContentViewMode,
    onOpen: (OfflineDownload) -> Unit,
    modifier: Modifier,
) {
    if (downloads.isEmpty()) {
        TvLibraryEmpty("Nothing available offline", modifier)
        return
    }
    TvMediaCollection(
        items = downloads,
        viewMode = viewMode,
        key = { it.downloadId },
        title = { item ->
            if (item.mediaKind == DownloadMediaKinds.SERIES_EPISODE) {
                item.seriesTitle?.let { "$it · ${item.title}" } ?: item.title
            } else item.title
        },
        subtitle = { item ->
            if (item.savedToDownloads) "OFFLINE · Phone Downloads" else "OFFLINE · OwnPlay private storage"
        },
        poster = { it.posterUrl },
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
private fun TvMovieContent(
    movies: List<VodMovie>,
    viewMode: ContentViewMode,
    onOpen: (VodMovie) -> Unit,
    modifier: Modifier,
) {
    if (movies.isEmpty()) {
        TvLibraryEmpty("No movies in this category", modifier)
        return
    }
    TvMediaCollection(
        items = movies,
        viewMode = viewMode,
        key = { it.movieId },
        title = { it.name },
        subtitle = { movie -> movie.rating?.let { "★ $it" }.orEmpty() },
        poster = { it.posterUrl },
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
private fun TvSeriesContent(
    series: List<SeriesSummary>,
    viewMode: ContentViewMode,
    onOpen: (SeriesSummary) -> Unit,
    modifier: Modifier,
) {
    if (series.isEmpty()) {
        TvLibraryEmpty("No series in this category", modifier)
        return
    }
    TvMediaCollection(
        items = series,
        viewMode = viewMode,
        key = { it.seriesId },
        title = { it.name },
        subtitle = { item -> item.rating?.let { "★ $it" }.orEmpty() },
        poster = { it.posterUrl },
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
private fun <T> TvMediaCollection(
    items: List<T>,
    viewMode: ContentViewMode,
    key: (T) -> String,
    title: (T) -> String,
    subtitle: (T) -> String,
    poster: (T) -> String?,
    onOpen: (T) -> Unit,
    modifier: Modifier,
) {
    when (viewMode) {
        ContentViewMode.LIST -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            items(items, key = key) { item ->
                TvMediaRow(
                    title = title(item),
                    subtitle = subtitle(item),
                    posterUrl = poster(item),
                    onClick = { onOpen(item) },
                )
            }
        }
        ContentViewMode.COMPACT,
        ContentViewMode.CARDS,
        -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (viewMode == ContentViewMode.CARDS) 132.dp else 108.dp),
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            gridItems(items, key = key) { item ->
                TvMediaTile(
                    title = title(item),
                    subtitle = subtitle(item),
                    posterUrl = poster(item),
                    compact = viewMode == ContentViewMode.COMPACT,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun TvMediaTile(
    title: String,
    subtitle: String,
    posterUrl: String?,
    compact: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        shape = shape,
        tonalElevation = if (focused) 4.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 5.dp else 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RemotePoster(
                url = posterUrl,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                title,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvMediaRow(
    title: String,
    subtitle: String,
    posterUrl: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemotePoster(
                url = posterUrl,
                title = title,
                modifier = Modifier.size(width = 54.dp, height = 78.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TvLibraryEmpty(message: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
