package app.ownplay.player

import android.content.Context
import app.ownplay.player.live.LiveCatalogRepository
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
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

    override fun close() {
        playbackController.close()
        playbackConnectivityMonitor.close()
        database.close()
    }
}
