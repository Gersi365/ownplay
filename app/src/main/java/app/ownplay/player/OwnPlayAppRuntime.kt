package app.ownplay.player

import android.content.Context
import app.ownplay.player.live.LiveCatalogRepository
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.secure.AndroidKeystoreSensitiveValueStore
import app.ownplay.player.playback.LivePlaybackResolver
import app.ownplay.player.playback.Media3PlaybackControllerFactory
import app.ownplay.player.playback.PlaybackController
import app.ownplay.player.playback.RoomPlaybackResolutionLookup
import app.ownplay.player.source.credential.AndroidKeystoreCredentialStore
import kotlinx.coroutines.flow.Flow

class OwnPlayAppRuntime(
    context: Context,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val database = OwnPlayDatabase.create(applicationContext)
    private val liveCatalogRepository = LiveCatalogRepository(database.liveBrowseDao())

    val playbackController: PlaybackController = Media3PlaybackControllerFactory.create(
        context = applicationContext,
        resolver = LivePlaybackResolver(
            lookup = RoomPlaybackResolutionLookup(
                sourceDao = database.playlistSourceDao(),
                catalogDao = database.providerCatalogDao(),
            ),
            sensitiveValueStore = AndroidKeystoreSensitiveValueStore(applicationContext),
            credentialStore = AndroidKeystoreCredentialStore(applicationContext),
        ),
    )

    fun observeSources(): Flow<List<PlaylistSourceEntity>> =
        database.playlistSourceDao().observeAll()

    fun observeLiveCatalog(sourceId: String) =
        liveCatalogRepository.observe(sourceId)

    override fun close() {
        playbackController.close()
        database.close()
    }
}
