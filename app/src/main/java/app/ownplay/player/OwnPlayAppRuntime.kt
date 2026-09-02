package app.ownplay.player

import android.content.Context
import androidx.room.withTransaction
import app.ownplay.player.download.OfflineDownloadRepository
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.epg.XtreamEpgRepository
import app.ownplay.player.live.LiveCatalogRepository
import app.ownplay.player.live.ingest.InitialLiveCatalogFactory
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestResult
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestor
import app.ownplay.player.live.ingest.RoomLiveCatalogPersistence
import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistRefreshStateEntity
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.persistence.RefreshStates
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.recent.RecentChannelHistory
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.personalization.CategoryOrderMutationResult
import app.ownplay.player.personalization.CategoryOrderStore
import app.ownplay.player.personalization.CategoryVisibilityMutationResult
import app.ownplay.player.personalization.CategoryVisibilityMutator
import app.ownplay.player.personalization.CategoryVisibilityStore
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelBulkActionExecutionResult
import app.ownplay.player.personalization.ChannelBulkActionExecutor
import app.ownplay.player.personalization.ChannelCustomizationMutationResult
import app.ownplay.player.personalization.ChannelCustomizationMutator
import app.ownplay.player.personalization.ChannelVisibilityMutator
import app.ownplay.player.personalization.CustomGroupMutationResult
import app.ownplay.player.personalization.CustomGroupMutator
import app.ownplay.player.personalization.FavoriteChannelMutator
import app.ownplay.player.personalization.FavoriteMutationResult
import app.ownplay.player.personalization.ManualChannelOrderMutator
import app.ownplay.player.personalization.ManualOrderMutationResult
import app.ownplay.player.personalization.ManualOrderPlacement
import app.ownplay.player.playback.AndroidPlaybackConnectivityMonitor
import app.ownplay.player.playback.LivePlaybackResolver
import app.ownplay.player.playback.Media3PlaybackEngine
import app.ownplay.player.playback.PlaybackController
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackTrackController
import app.ownplay.player.playback.RoomPlaybackResolutionLookup
import app.ownplay.player.series.SeriesRepository
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.SourceSyncFailure
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.management.SourceEditSnapshot
import app.ownplay.player.source.management.SourceManagementService
import app.ownplay.player.source.management.SourceMutationResult
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import app.ownplay.player.source.onboarding.SourceOnboardingService
import app.ownplay.player.source.onboarding.SourceStagingService
import app.ownplay.player.source.onboarding.toSourceSyncFailure
import app.ownplay.player.source.selection.ActivePlaylistSelection
import app.ownplay.player.source.selection.ActivePlaylistStore
import app.ownplay.player.source.shouldRefreshSource
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamSeriesClient
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import app.ownplay.player.vod.VodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OwnPlayAppRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
    private val offlineDownloadRepository = OfflineDownloadRepository(
        context = applicationContext,
        database = database,
    )
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val categoryVisibilityStore = CategoryVisibilityStore(
        context = applicationContext,
        scope = runtimeScope,
    )
    private val categoryOrderStore = CategoryOrderStore(
        context = applicationContext,
        scope = runtimeScope,
    )
    private val liveCatalogRepository = LiveCatalogRepository(
        dao = database.liveBrowseDao(),
        observeHiddenCategoryKeys = categoryVisibilityStore::observeHiddenCategoryKeys,
        observeCategoryOrder = categoryOrderStore::observeOrder,
    )
    private val recentChannelHistory = RecentChannelHistory(database)
    private val playbackConnectivityMonitor =
        AndroidPlaybackConnectivityMonitor(applicationContext)
    private val sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext)
    private val credentialStore = AndroidKeystoreCredentialStore(applicationContext)
    private val sourceOnboardingService = SourceOnboardingService(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
        contentResolver = applicationContext.contentResolver,
    )
    private val sourceStagingService = SourceStagingService(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )
    private val sourceManagementService = SourceManagementService(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )
    private val xtreamClient = XtreamClient()
    private val xtreamSeriesClient = XtreamSeriesClient()
    private val liveCatalogIngestor = InitialLiveCatalogIngestor(
        persistence = RoomLiveCatalogPersistence(database),
        sensitiveValueStore = sensitiveValueStore,
    )
    private val epgRepository = XtreamEpgRepository(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )
    private val vodRepository = VodRepository(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
        xtreamClient = xtreamClient,
    )
    private val seriesRepository = SeriesRepository(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
        xtreamSeriesClient = xtreamSeriesClient,
    )

    private val _sourceSyncState = MutableStateFlow(SourceSyncState())
    val sourceSyncState: StateFlow<SourceSyncState> = _sourceSyncState.asStateFlow()
    private val _sourceSyncStates = MutableStateFlow<Map<String, SourceSyncState>>(emptyMap())
    val sourceSyncStates: StateFlow<Map<String, SourceSyncState>> = _sourceSyncStates.asStateFlow()

    private fun rememberSourceState(state: SourceSyncState) {
        val sourceId = state.sourceId ?: return
        _sourceSyncStates.update { current -> current + (sourceId to state) }
    }

    private val channelVisibilityMutator = ChannelVisibilityMutator(database)
    private val categoryVisibilityMutator = CategoryVisibilityMutator(
        store = categoryVisibilityStore,
        categoryExists = database.providerCatalogDao()::categoryExistsInSource,
    )
    private val favoriteChannelMutator = FavoriteChannelMutator(database)
    private val manualChannelOrderMutator = ManualChannelOrderMutator(database)
    private val customGroupMutator = CustomGroupMutator(database)
    private val channelCustomizationMutator = ChannelCustomizationMutator(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
    )
    private val channelBulkActionExecutor = ChannelBulkActionExecutor(
        visibilityMutator = channelVisibilityMutator,
        favoriteMutator = favoriteChannelMutator,
        manualOrderMutator = manualChannelOrderMutator,
        customGroupMutator = customGroupMutator,
    )

    private val playbackResolver = LivePlaybackResolver(
        lookup = RoomPlaybackResolutionLookup(
            sourceDao = database.playlistSourceDao(),
            catalogDao = database.providerCatalogDao(),
        ),
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )
    private val playbackEngine = Media3PlaybackEngine(applicationContext)

    val playbackController = PlaybackController(
        resolveLocator = playbackResolver::resolve,
        engine = playbackEngine,
        resolveOfflineLocator = offlineDownloadRepository::localPlaybackLocator,
        networkState = playbackConnectivityMonitor.state,
    )
    val playbackVideoOutput = playbackEngine
    val playbackTrackController: PlaybackTrackController = playbackEngine

    init {
        runtimeScope.launch {
            playbackController.state.collect { state ->
                val request = (state as? PlaybackState.Playing)?.request ?: return@collect
                if (request.mediaKind != PlaybackMediaKind.LIVE) return@collect
                try {
                    recentChannelHistory.recordWatch(
                        channelId = request.channelId,
                        watchedAtEpochMillis = System.currentTimeMillis(),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Recent history is best-effort and must never interrupt playback.
                }
            }
        }
        runtimeScope.launch {
            _sourceSyncState.collect(::rememberSourceState)
        }
        runtimeScope.launch {
            val sources = try {
                database.playlistSourceDao().allForBackup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }

            // Resume only imports that were in-flight when the previous runtime stopped.
            // Failed imports remain visible and wait for an explicit Retry from Settings.
            sources.filter { source -> !source.enabled }.forEach { source ->
                val persistedRefresh = try {
                    database.refreshStateDao().get(source.sourceId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                when (persistedRefresh?.state) {
                    RefreshStates.FAILED -> rememberSourceState(
                        SourceSyncState(
                            sourceId = source.sourceId,
                            sourceName = source.name,
                            stage = SourceSyncStage.ChannelsFailed,
                            failure = SourceSyncFailure.Unexpected,
                        ),
                    )
                    else -> completePendingSource(source.sourceId)
                }
            }

            // Cached catalogs are immediately usable. Only the persisted active source is eligible
            // for automatic startup refresh, and even it is skipped while still fresh.
            val readySources = sources.filter(PlaylistSourceEntity::enabled)
            val persistedActiveSourceId = try {
                (ActivePlaylistStore(applicationContext).observe().first() as? ActivePlaylistSelection.Ready)
                    ?.sourceId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val startupSource = readySources.firstOrNull { source ->
                source.sourceId == persistedActiveSourceId
            } ?: readySources.firstOrNull()
            startupSource?.let { source -> refreshSourceIfStale(source.sourceId) }
        }
    }

    fun observeSources(): Flow<List<PlaylistSourceEntity>> =
        database.playlistSourceDao().observeAll()

    fun observeSourceSummaries(): Flow<List<PlaylistSourceSummary>> =
        database.playlistSourceDao().observeSummaries()

    fun observeLiveCatalog(sourceId: String) =
        liveCatalogRepository.observe(sourceId)

    suspend fun addXtreamSource(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowCleartext: Boolean = false,
    ): SourceOnboardingResult = withContext(Dispatchers.IO) {
        val result = sourceStagingService.stageXtream(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowCleartext = allowCleartext,
        )
        handleStagedSourceResult(result, name.trim().ifBlank { "Xtream" })
        result
    }

    suspend fun addRemoteM3uSource(
        name: String,
        playlistUrl: String,
        allowCleartext: Boolean = false,
    ): SourceOnboardingResult = withContext(Dispatchers.IO) {
        val result = sourceStagingService.stageRemoteM3u(
            name = name,
            playlistUrl = playlistUrl,
            allowCleartext = allowCleartext,
        )
        handleStagedSourceResult(result, name.trim().ifBlank { "M3U" })
        result
    }

    suspend fun addLocalM3uSource(
        name: String,
        documentUri: String,
        allowCleartext: Boolean = false,
    ): SourceOnboardingResult = withContext(Dispatchers.IO) {
        val result = sourceStagingService.stageLocalM3u(
            name = name,
            documentUri = documentUri,
            allowCleartext = allowCleartext,
        )
        handleStagedSourceResult(result, name.trim().ifBlank { "Local M3U" })
        result
    }

    private fun handleStagedSourceResult(
        result: SourceOnboardingResult,
        fallbackName: String,
    ) {
        when (result) {
            is SourceOnboardingResult.Success -> {
                val state = SourceSyncState(
                    sourceId = result.sourceId,
                    sourceName = fallbackName,
                    stage = SourceSyncStage.LoadingChannels,
                )
                _sourceSyncState.value = state
                rememberSourceState(state)
                runtimeScope.launch { completePendingSource(result.sourceId) }
            }
            is SourceOnboardingResult.Failure -> {
                _sourceSyncState.value = SourceSyncState(
                    sourceName = fallbackName,
                    stage = SourceSyncStage.ChannelsFailed,
                    failure = result.reason.toSourceSyncFailure(),
                )
            }
        }
    }

    suspend fun retryPendingSource(sourceId: String) {
        completePendingSource(sourceId)
    }

    fun onActiveSourceSelected(sourceId: String) {
        runtimeScope.launch { refreshSourceIfStale(sourceId) }
    }

    private suspend fun refreshSourceIfStale(sourceId: String) {
        val refreshState = try {
            database.refreshStateDao().get(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (
            shouldRefreshSource(
                lastSuccessAtEpochMillis = refreshState?.lastSuccessAtEpochMillis,
                nowEpochMillis = System.currentTimeMillis(),
            )
        ) {
            refreshSource(sourceId)
        }
    }

    private suspend fun completePendingSource(sourceId: String) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val source = database.playlistSourceDao().getById(sourceId) ?: return@withLock
            if (source.enabled) return@withLock

            val existingCount = try {
                database.providerCatalogDao().channelsForSource(sourceId)
                    .count { channel -> channel.availability != ChannelAvailability.REMOVED }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0
            }
            val loadingState = SourceSyncState(
                sourceId = sourceId,
                sourceName = source.name,
                stage = SourceSyncStage.LoadingChannels,
                channelCount = existingCount,
            )
            _sourceSyncState.value = loadingState
            rememberSourceState(loadingState)
            markRefreshRunning(sourceId)

            val channelResult = refreshLiveCatalogInternal(
                sourceId = sourceId,
                sourceKind = source.sourceKind,
            )
            if (channelResult is SourceOnboardingResult.Failure) {
                val failedState = SourceSyncState(
                    sourceId = sourceId,
                    sourceName = source.name,
                    stage = SourceSyncStage.ChannelsFailed,
                    channelCount = existingCount,
                    failure = channelResult.reason.toSourceSyncFailure(),
                )
                _sourceSyncState.value = failedState
                rememberSourceState(failedState)
                markRefreshFailed(sourceId, failedState.failure.toString())
                return@withLock
            }
            channelResult as SourceOnboardingResult.Success

            val now = System.currentTimeMillis()
            try {
                database.withTransaction {
                    database.playlistSourceDao().upsert(
                        source.copy(
                            enabled = true,
                            updatedAtEpochMillis = now,
                        ),
                    )
                    val previous = database.refreshStateDao().get(sourceId)
                    database.refreshStateDao().upsert(
                        PlaylistRefreshStateEntity(
                            sourceId = sourceId,
                            generation = previous?.generation ?: now,
                            state = RefreshStates.SUCCEEDED,
                            lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                            lastSuccessAtEpochMillis = now,
                            lastErrorCode = null,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failedState = SourceSyncState(
                    sourceId = sourceId,
                    sourceName = source.name,
                    stage = SourceSyncStage.ChannelsFailed,
                    channelCount = channelResult.channelCount,
                    failure = SourceSyncFailure.Persistence,
                )
                _sourceSyncState.value = failedState
                rememberSourceState(failedState)
                markRefreshFailed(sourceId, "persistence")
                return@withLock
            }

            loadEpgAfterChannels(
                sourceId = sourceId,
                sourceName = source.name,
                channelCount = channelResult.channelCount,
            )
            if (source.sourceKind == SourceKinds.XTREAM) {
                runtimeScope.launch { refreshXtreamMediaCatalogs(sourceId) }
            }
        }
    }

    private suspend fun markRefreshRunning(sourceId: String) {
        val now = System.currentTimeMillis()
        val previous = database.refreshStateDao().get(sourceId)
        database.refreshStateDao().upsert(
            PlaylistRefreshStateEntity(
                sourceId = sourceId,
                generation = now,
                state = RefreshStates.RUNNING,
                lastAttemptAtEpochMillis = now,
                lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                lastErrorCode = null,
            ),
        )
    }

    private suspend fun markRefreshSucceeded(sourceId: String) {
        val now = System.currentTimeMillis()
        val previous = database.refreshStateDao().get(sourceId)
        database.refreshStateDao().upsert(
            PlaylistRefreshStateEntity(
                sourceId = sourceId,
                generation = previous?.generation ?: now,
                state = RefreshStates.SUCCEEDED,
                lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                lastSuccessAtEpochMillis = now,
                lastErrorCode = null,
            ),
        )
    }

    private suspend fun markRefreshFailed(sourceId: String, errorCode: String?) {
        val now = System.currentTimeMillis()
        val previous = database.refreshStateDao().get(sourceId)
        database.refreshStateDao().upsert(
            PlaylistRefreshStateEntity(
                sourceId = sourceId,
                generation = previous?.generation ?: now,
                state = RefreshStates.FAILED,
                lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                lastErrorCode = errorCode,
            ),
        )
    }

    suspend fun refreshSource(sourceId: String) {
        if (!sourceExists(sourceId)) return
        try {
            markRefreshRunning(sourceId)
            refreshSourcePipeline(sourceId)
            val state = _sourceSyncState.value
            if (
                state.sourceId == sourceId &&
                state.stage == SourceSyncStage.ChannelsFailed
            ) {
                markRefreshFailed(sourceId, state.failure.toString())
            } else {
                markRefreshSucceeded(sourceId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            refreshMutex.withLock {
                if (sourceExists(sourceId)) {
                    publishUnexpectedRefreshFailure(sourceId)
                    markRefreshFailed(sourceId, "unexpected")
                }
            }
        }
    }

    suspend fun refreshAllSources() {
        val sources = try {
            observeSources().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val current = _sourceSyncState.value
            _sourceSyncState.value = current.copy(
                stage = SourceSyncStage.ChannelsFailed,
                failure = SourceSyncFailure.Unexpected,
            )
            return
        }
        sources.forEach { source -> refreshSource(source.sourceId) }
    }

    private suspend fun refreshSourcePipeline(sourceId: String) = refreshMutex.withLock {
        val source = database.playlistSourceDao().getById(sourceId) ?: return@withLock
        val existingCount = try {
            database.providerCatalogDao().channelsForSource(sourceId)
                .count { channel -> channel.availability != ChannelAvailability.REMOVED }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            0
        }
        _sourceSyncState.value = SourceSyncState(
            sourceId = sourceId,
            sourceName = source.name,
            stage = SourceSyncStage.LoadingChannels,
            channelCount = existingCount,
        )

        val channelResult = refreshLiveCatalogInternal(
            sourceId = sourceId,
            sourceKind = source.sourceKind,
        )
        if (channelResult is SourceOnboardingResult.Failure) {
            _sourceSyncState.value = SourceSyncState(
                sourceId = sourceId,
                sourceName = source.name,
                stage = SourceSyncStage.ChannelsFailed,
                channelCount = existingCount,
                failure = channelResult.reason.toSourceSyncFailure(),
            )
            if (source.sourceKind == SourceKinds.XTREAM) {
                refreshXtreamMediaCatalogs(sourceId)
            }
            return@withLock
        }
        channelResult as SourceOnboardingResult.Success

        loadEpgAfterChannels(
            sourceId = sourceId,
            sourceName = source.name,
            channelCount = channelResult.channelCount,
        )
        if (source.sourceKind == SourceKinds.XTREAM) {
            refreshXtreamMediaCatalogs(sourceId)
        }
    }

    private suspend fun refreshXtreamMediaCatalogs(sourceId: String) {
        try {
            vodRepository.refresh(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the persisted VOD snapshot and continue with Series.
        }
        try {
            seriesRepository.refresh(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the persisted Series snapshot. Startup refresh is best-effort and non-destructive.
        }
    }

    private suspend fun sourceExists(sourceId: String): Boolean = try {
        database.playlistSourceDao().getById(sourceId) != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun publishUnexpectedRefreshFailure(sourceId: String) {
        val current = _sourceSyncState.value
        val sourceName = if (current.sourceId == sourceId) {
            current.sourceName
        } else {
            try {
                database.playlistSourceDao().getById(sourceId)?.name
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        val channelCount = if (current.sourceId == sourceId) {
            current.channelCount
        } else {
            try {
                database.providerCatalogDao().channelsForSource(sourceId)
                    .count { channel -> channel.availability != ChannelAvailability.REMOVED }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0
            }
        }
        _sourceSyncState.value = SourceSyncState(
            sourceId = sourceId,
            sourceName = sourceName,
            stage = SourceSyncStage.ChannelsFailed,
            channelCount = channelCount,
            failure = SourceSyncFailure.Unexpected,
        )
    }

    private suspend fun loadEpgAfterChannels(
        sourceId: String,
        sourceName: String,
        channelCount: Int,
    ) {
        _sourceSyncState.value = SourceSyncState(
            sourceId = sourceId,
            sourceName = sourceName,
            stage = SourceSyncStage.LoadingEpg,
            channelCount = channelCount,
        )
        val epg = try {
            epgRepository.refreshSource(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!sourceExists(sourceId)) {
            if (_sourceSyncState.value.sourceId == sourceId) {
                _sourceSyncState.value = SourceSyncState()
            }
            return
        }
        _sourceSyncState.value = if (epg == null) {
            SourceSyncState(
                sourceId = sourceId,
                sourceName = sourceName,
                stage = SourceSyncStage.EpgFailed,
                channelCount = channelCount,
            )
        } else {
            SourceSyncState(
                sourceId = sourceId,
                sourceName = sourceName,
                stage = SourceSyncStage.Ready,
                channelCount = channelCount,
                epgChannelCount = epg.matchedChannelCount,
            )
        }
    }

    suspend fun epgSnapshot(
        sourceId: String,
        channelId: String,
    ): EpgSnapshot? = epgRepository.snapshot(sourceId, channelId)

    fun currentEpgPrograms(sourceId: String): Map<String, EpgProgram> =
        epgRepository.currentPrograms(sourceId)

    suspend fun loadSourceEditSnapshot(sourceId: String): SourceEditSnapshot? =
        sourceManagementService.load(sourceId)

    suspend fun renameSource(
        sourceId: String,
        name: String,
    ): SourceMutationResult = refreshMutex.withLock {
        sourceManagementService.rename(sourceId, name)
    }

    suspend fun updateXtreamSource(
        sourceId: String,
        name: String,
        serverUrl: String,
        replacementUsername: String,
        replacementPassword: String,
        allowCleartext: Boolean,
    ): SourceMutationResult {
        val result = refreshMutex.withLock {
            sourceManagementService.updateXtream(
                sourceId = sourceId,
                name = name,
                serverUrl = serverUrl,
                replacementUsername = replacementUsername,
                replacementPassword = replacementPassword,
                allowCleartext = allowCleartext,
            )
        }
        if (result is SourceMutationResult.Success) {
            refreshSource(sourceId)
        }
        return result
    }

    suspend fun deleteSource(sourceId: String): SourceMutationResult = refreshMutex.withLock {
        val result = sourceManagementService.delete(sourceId)
        if (result is SourceMutationResult.Success) {
            epgRepository.invalidateSource(sourceId)
            try {
                categoryVisibilityStore.clearSource(sourceId)
                categoryOrderStore.clearSource(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The source is already deleted. Stale source-scoped preferences are inert.
            }
            _sourceSyncStates.update { current -> current - sourceId }
            if (_sourceSyncState.value.sourceId == sourceId) {
                _sourceSyncState.value = SourceSyncState()
            }
        }
        result
    }

    suspend fun ensureLiveCatalog(sourceId: String): SourceOnboardingResult =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val source = try {
                    database.playlistSourceDao().getById(sourceId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return@withLock SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.PersistenceFailure,
                )
                val existingChannels = try {
                    database.providerCatalogDao().channelsForSource(sourceId)
                        .count { channel -> channel.availability != ChannelAvailability.REMOVED }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withLock SourceOnboardingResult.Failure(
                        SourceOnboardingFailure.PersistenceFailure,
                    )
                }
                if (existingChannels > 0) {
                    return@withLock SourceOnboardingResult.Success(
                        sourceId = sourceId,
                        channelCount = existingChannels,
                    )
                }
                refreshLiveCatalogInternal(
                    sourceId = sourceId,
                    sourceKind = source.sourceKind,
                )
            }
        }

    suspend fun refreshLiveCatalog(sourceId: String): SourceOnboardingResult =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val source = try {
                    database.playlistSourceDao().getById(sourceId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return@withLock SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.PersistenceFailure,
                )
                refreshLiveCatalogInternal(
                    sourceId = sourceId,
                    sourceKind = source.sourceKind,
                )
            }
        }

    private suspend fun refreshLiveCatalogInternal(
        sourceId: String,
        sourceKind: String,
    ): SourceOnboardingResult = when (sourceKind) {
        SourceKinds.XTREAM -> refreshXtreamLiveCatalogInternal(sourceId)
        SourceKinds.REMOTE_M3U,
        SourceKinds.LOCAL_M3U,
        -> sourceOnboardingService.refreshM3u(sourceId)
        else -> SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure)
    }

    private suspend fun refreshXtreamLiveCatalogInternal(
        sourceId: String,
    ): SourceOnboardingResult {
        val source = try {
            database.playlistSourceDao().getById(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.PersistenceFailure)
        } ?: return SourceOnboardingResult.Failure(SourceOnboardingFailure.PersistenceFailure)

        if (source.sourceKind != SourceKinds.XTREAM) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure)
        }

        val locatorValue = try {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return SourceOnboardingResult.Failure(SourceOnboardingFailure.SecureStorageFailure)

        val locator = XtreamSourceLocatorCodec.parse(locatorValue)
            ?: return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.SourceFailure(SourceError.InvalidUrl),
            )

        val credentialRefValue = source.credentialRef
            ?: return SourceOnboardingResult.Failure(SourceOnboardingFailure.SecureStorageFailure)
        val credentials = try {
            credentialStore.get(CredentialRef(credentialRefValue))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return SourceOnboardingResult.Failure(SourceOnboardingFailure.SecureStorageFailure)

        when (
            val validation = xtreamClient.validateAccount(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(validation.error),
                )
            }
        }

        val categories = when (
            val loaded = xtreamClient.getLiveCategories(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }
        val streams = when (
            val loaded = xtreamClient.getLiveStreams(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        val ingestResult = try {
            liveCatalogIngestor.ingest(
                sourceId = sourceId,
                generation = System.currentTimeMillis(),
                catalog = InitialLiveCatalogFactory.fromXtream(categories, streams),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure)
        }

        return when (ingestResult) {
            is InitialLiveCatalogIngestResult.Success -> {
                SourceOnboardingResult.Success(
                    sourceId = sourceId,
                    channelCount = ingestResult.channelCount,
                )
            }
            else -> SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure)
        }
    }

    suspend fun executeChannelBulkAction(
        sourceId: String,
        selectedChannelIds: Set<String>,
        action: ChannelBulkAction,
    ): ChannelBulkActionExecutionResult = channelBulkActionExecutor.execute(
        sourceId = sourceId,
        selectedChannelIds = selectedChannelIds,
        action = action,
        eventAtEpochMillis = System.currentTimeMillis(),
    )

    suspend fun hideCategory(
        sourceId: String,
        providerCategoryKey: String,
    ): CategoryVisibilityMutationResult = categoryVisibilityMutator.hide(
        sourceId = sourceId,
        providerCategoryKey = providerCategoryKey,
    )

    suspend fun unhideCategory(
        sourceId: String,
        providerCategoryKey: String,
    ): CategoryVisibilityMutationResult = categoryVisibilityMutator.unhide(
        sourceId = sourceId,
        providerCategoryKey = providerCategoryKey,
    )

    suspend fun setCategoryOrder(
        sourceId: String,
        orderedCategoryKeys: List<String>,
    ): CategoryOrderMutationResult = categoryOrderStore.setOrder(
        sourceId = sourceId,
        orderedCategoryKeys = orderedCategoryKeys,
    )

    suspend fun createCustomGroup(name: String): CustomGroupMutationResult =
        customGroupMutator.createGroup(
            name = name,
            createdAtEpochMillis = System.currentTimeMillis(),
        )

    suspend fun renameCustomGroup(
        groupId: String,
        name: String,
    ): CustomGroupMutationResult = customGroupMutator.renameGroup(groupId, name)

    suspend fun deleteCustomGroup(groupId: String): CustomGroupMutationResult =
        customGroupMutator.deleteGroup(groupId)

    suspend fun setLocalDisplayName(
        sourceId: String,
        channelId: String,
        name: String,
    ): ChannelCustomizationMutationResult = channelCustomizationMutator.setLocalDisplayName(
        sourceId = sourceId,
        channelId = channelId,
        localDisplayName = name,
    )

    suspend fun clearLocalDisplayName(
        sourceId: String,
        channelId: String,
    ): ChannelCustomizationMutationResult = channelCustomizationMutator.clearLocalDisplayName(
        sourceId = sourceId,
        channelId = channelId,
    )

    suspend fun setLogoOverride(
        sourceId: String,
        channelId: String,
        logoValue: String,
    ): ChannelCustomizationMutationResult = channelCustomizationMutator.setLogoOverride(
        sourceId = sourceId,
        channelId = channelId,
        logoValue = logoValue,
    )

    suspend fun clearLogoOverride(
        sourceId: String,
        channelId: String,
    ): ChannelCustomizationMutationResult = channelCustomizationMutator.clearLogoOverride(
        sourceId = sourceId,
        channelId = channelId,
    )

    suspend fun moveChannelRelative(
        sourceId: String,
        channelId: String,
        anchorChannelId: String,
        placement: ManualOrderPlacement,
    ): ManualOrderMutationResult = manualChannelOrderMutator.moveRelative(
        sourceId = sourceId,
        channelId = channelId,
        anchorChannelId = anchorChannelId,
        placement = placement,
    )

    suspend fun moveFavoriteRelative(
        sourceId: String,
        channelId: String,
        anchorChannelId: String,
        placement: ManualOrderPlacement,
    ): FavoriteMutationResult = favoriteChannelMutator.moveFavoriteRelative(
        sourceId = sourceId,
        channelId = channelId,
        anchorChannelId = anchorChannelId,
        placement = placement,
    )

    override fun close() {
        runtimeScope.cancel()
        playbackController.close()
        playbackConnectivityMonitor.close()
        database.close()
    }
}
