package app.ownplay.player

import android.content.Context
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.epg.XtreamEpgRepository
import app.ownplay.player.live.LiveCatalogRepository
import app.ownplay.player.live.ingest.InitialLiveCatalogFactory
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestResult
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestor
import app.ownplay.player.live.ingest.RoomLiveCatalogPersistence
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.persistence.secure.SensitiveValueRef
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
import app.ownplay.player.playback.PlaybackTrackController
import app.ownplay.player.playback.RoomPlaybackResolutionLookup
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.management.SourceEditSnapshot
import app.ownplay.player.source.management.SourceManagementService
import app.ownplay.player.source.management.SourceMutationResult
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import app.ownplay.player.source.onboarding.SourceOnboardingService
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OwnPlayAppRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val liveCatalogRepository = LiveCatalogRepository(database.liveBrowseDao())
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
    private val sourceManagementService = SourceManagementService(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )
    private val xtreamClient = XtreamClient()
    private val liveCatalogIngestor = InitialLiveCatalogIngestor(
        persistence = RoomLiveCatalogPersistence(database),
        sensitiveValueStore = sensitiveValueStore,
    )
    private val epgRepository = XtreamEpgRepository(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        credentialStore = credentialStore,
    )

    private val _sourceSyncState = MutableStateFlow(SourceSyncState())
    val sourceSyncState: StateFlow<SourceSyncState> = _sourceSyncState.asStateFlow()

    private val channelVisibilityMutator = ChannelVisibilityMutator(database)
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
        networkState = playbackConnectivityMonitor.state,
    )
    val playbackVideoOutput = playbackEngine
    val playbackTrackController: PlaybackTrackController = playbackEngine

    init {
        runtimeScope.launch {
            val sources = observeSources().first()
            sources.forEach { source ->
                refreshSourcePipeline(source.sourceId)
            }
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
        _sourceSyncState.value = SourceSyncState(
            sourceName = name.trim().ifBlank { "Xtream" },
            stage = SourceSyncStage.LoadingChannels,
        )
        val result = sourceOnboardingService.addXtream(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowCleartext = allowCleartext,
        )
        when (result) {
            is SourceOnboardingResult.Success -> {
                loadEpgAfterChannels(
                    sourceId = result.sourceId,
                    sourceName = name.trim(),
                    channelCount = result.channelCount,
                )
            }
            is SourceOnboardingResult.Failure -> {
                _sourceSyncState.value = SourceSyncState(
                    sourceName = name.trim().ifBlank { "Xtream" },
                    stage = SourceSyncStage.ChannelsFailed,
                )
            }
        }
        result
    }

    suspend fun addRemoteM3uSource(
        name: String,
        playlistUrl: String,
    ): SourceOnboardingResult = withContext(Dispatchers.IO) {
        _sourceSyncState.value = SourceSyncState(
            sourceName = name.trim().ifBlank { "M3U" },
            stage = SourceSyncStage.LoadingChannels,
        )
        val result = sourceOnboardingService.addRemoteM3u(
            name = name,
            playlistUrl = playlistUrl,
        )
        _sourceSyncState.value = when (result) {
            is SourceOnboardingResult.Success -> SourceSyncState(
                sourceId = result.sourceId,
                sourceName = name.trim(),
                stage = SourceSyncStage.Ready,
                channelCount = result.channelCount,
            )
            is SourceOnboardingResult.Failure -> SourceSyncState(
                sourceName = name.trim().ifBlank { "M3U" },
                stage = SourceSyncStage.ChannelsFailed,
            )
        }
        result
    }

    suspend fun addLocalM3uSource(
        name: String,
        documentUri: String,
    ): SourceOnboardingResult = withContext(Dispatchers.IO) {
        _sourceSyncState.value = SourceSyncState(
            sourceName = name.trim().ifBlank { "Local M3U" },
            stage = SourceSyncStage.LoadingChannels,
        )
        val result = sourceOnboardingService.addLocalM3u(
            name = name,
            documentUri = documentUri,
        )
        _sourceSyncState.value = when (result) {
            is SourceOnboardingResult.Success -> SourceSyncState(
                sourceId = result.sourceId,
                sourceName = name.trim(),
                stage = SourceSyncStage.Ready,
                channelCount = result.channelCount,
            )
            is SourceOnboardingResult.Failure -> SourceSyncState(
                sourceName = name.trim().ifBlank { "Local M3U" },
                stage = SourceSyncStage.ChannelsFailed,
            )
        }
        result
    }

    suspend fun refreshSource(sourceId: String) {
        refreshSourcePipeline(sourceId)
    }

    suspend fun refreshAllSources() {
        observeSources().first().forEach { source ->
            refreshSourcePipeline(source.sourceId)
        }
    }

    private suspend fun refreshSourcePipeline(sourceId: String) = refreshMutex.withLock {
        val source = database.playlistSourceDao().getById(sourceId) ?: return@withLock
        val existingCount = runCatching {
            database.providerCatalogDao().channelsForSource(sourceId).size
        }.getOrDefault(0)
        _sourceSyncState.value = SourceSyncState(
            sourceId = sourceId,
            sourceName = source.name,
            stage = SourceSyncStage.LoadingChannels,
            channelCount = existingCount,
        )

        val channelResult = when (source.sourceKind) {
            SourceKinds.XTREAM -> refreshXtreamLiveCatalogInternal(sourceId)
            else -> SourceOnboardingResult.Success(sourceId, existingCount)
        }
        if (channelResult is SourceOnboardingResult.Failure) {
            _sourceSyncState.value = SourceSyncState(
                sourceId = sourceId,
                sourceName = source.name,
                stage = SourceSyncStage.ChannelsFailed,
                channelCount = existingCount,
            )
            return@withLock
        }
        channelResult as SourceOnboardingResult.Success

        if (source.sourceKind == SourceKinds.XTREAM) {
            loadEpgAfterChannels(
                sourceId = sourceId,
                sourceName = source.name,
                channelCount = channelResult.channelCount,
            )
        } else {
            _sourceSyncState.value = SourceSyncState(
                sourceId = sourceId,
                sourceName = source.name,
                stage = SourceSyncStage.Ready,
                channelCount = channelResult.channelCount,
            )
        }
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
        val epg = epgRepository.refreshSource(sourceId)
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

    suspend fun loadSourceEditSnapshot(sourceId: String): SourceEditSnapshot? =
        sourceManagementService.load(sourceId)

    suspend fun renameSource(
        sourceId: String,
        name: String,
    ): SourceMutationResult = sourceManagementService.rename(sourceId, name)

    suspend fun updateXtreamSource(
        sourceId: String,
        name: String,
        serverUrl: String,
        replacementUsername: String,
        replacementPassword: String,
        allowCleartext: Boolean,
    ): SourceMutationResult {
        val result = sourceManagementService.updateXtream(
            sourceId = sourceId,
            name = name,
            serverUrl = serverUrl,
            replacementUsername = replacementUsername,
            replacementPassword = replacementPassword,
            allowCleartext = allowCleartext,
        )
        if (result is SourceMutationResult.Success) {
            refreshSourcePipeline(sourceId)
        }
        return result
    }

    suspend fun deleteSource(sourceId: String): SourceMutationResult {
        val result = sourceManagementService.delete(sourceId)
        if (result is SourceMutationResult.Success) {
            epgRepository.invalidateSource(sourceId)
            if (_sourceSyncState.value.sourceId == sourceId) {
                _sourceSyncState.value = SourceSyncState()
            }
        }
        return result
    }

    suspend fun ensureLiveCatalog(sourceId: String): SourceOnboardingResult =
        withContext(Dispatchers.IO) {
            val existingChannels = try {
                database.providerCatalogDao().channelsForSource(sourceId)
            } catch (_: Exception) {
                return@withContext SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.PersistenceFailure,
                )
            }
            if (existingChannels.isNotEmpty()) {
                return@withContext SourceOnboardingResult.Success(
                    sourceId = sourceId,
                    channelCount = existingChannels.size,
                )
            }
            refreshXtreamLiveCatalogInternal(sourceId)
        }

    suspend fun refreshLiveCatalog(sourceId: String): SourceOnboardingResult =
        withContext(Dispatchers.IO) {
            refreshXtreamLiveCatalogInternal(sourceId)
        }

    private suspend fun refreshXtreamLiveCatalogInternal(
        sourceId: String,
    ): SourceOnboardingResult {
        val source = try {
            database.playlistSourceDao().getById(sourceId)
        } catch (_: Exception) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.PersistenceFailure)
        } ?: return SourceOnboardingResult.Failure(SourceOnboardingFailure.PersistenceFailure)

        if (source.sourceKind != SourceKinds.XTREAM) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure)
        }

        val locatorValue = try {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
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
