package app.ownplay.player

import android.content.Context
import app.ownplay.player.live.LiveCatalogRepository
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
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
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import app.ownplay.player.source.onboarding.SourceOnboardingService
import kotlinx.coroutines.flow.Flow

class OwnPlayAppRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
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

    fun observeSources(): Flow<List<PlaylistSourceEntity>> =
        database.playlistSourceDao().observeAll()

    fun observeLiveCatalog(sourceId: String) =
        liveCatalogRepository.observe(sourceId)

    suspend fun addXtreamSource(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ): SourceOnboardingResult = sourceOnboardingService.addXtream(
        name = name,
        serverUrl = serverUrl,
        username = username,
        password = password,
    )

    suspend fun addRemoteM3uSource(
        name: String,
        playlistUrl: String,
    ): SourceOnboardingResult = sourceOnboardingService.addRemoteM3u(
        name = name,
        playlistUrl = playlistUrl,
    )

    suspend fun addLocalM3uSource(
        name: String,
        documentUri: String,
    ): SourceOnboardingResult = sourceOnboardingService.addLocalM3u(
        name = name,
        documentUri = documentUri,
    )

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
        playbackController.close()
        playbackConnectivityMonitor.close()
        database.close()
    }
}
