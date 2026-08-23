package app.ownplay.player.source.onboarding

import android.content.ContentResolver
import app.ownplay.player.live.ingest.IncomingLiveCatalog
import app.ownplay.player.live.ingest.InitialLiveCatalogFactory
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestResult
import app.ownplay.player.live.ingest.InitialLiveCatalogIngestor
import app.ownplay.player.live.ingest.RoomLiveCatalogPersistence
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.SourceValidator
import app.ownplay.player.source.UrlValidationResult
import app.ownplay.player.source.credential.CredentialStore
import app.ownplay.player.source.credential.XtreamCredentials
import app.ownplay.player.source.m3u.AndroidLocalM3uLoader
import app.ownplay.player.source.m3u.RemoteM3uLoader
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamSourceLocator
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface SourceOnboardingFailure {
    data object InvalidName : SourceOnboardingFailure
    data class SourceFailure(val error: SourceError) : SourceOnboardingFailure
    data object SecureStorageFailure : SourceOnboardingFailure
    data object PersistenceFailure : SourceOnboardingFailure
    data object CatalogImportFailure : SourceOnboardingFailure
}

sealed interface SourceOnboardingResult {
    data class Success(
        val sourceId: String,
        val channelCount: Int,
    ) : SourceOnboardingResult

    data class Failure(
        val reason: SourceOnboardingFailure,
    ) : SourceOnboardingResult
}

class SourceOnboardingService(
    private val database: OwnPlayDatabase,
    private val sensitiveValueStore: SensitiveValueStore,
    private val credentialStore: CredentialStore,
    contentResolver: ContentResolver,
    private val xtreamClient: XtreamClient = XtreamClient(),
    private val remoteM3uLoader: RemoteM3uLoader = RemoteM3uLoader(),
) {
    private val localM3uLoader = AndroidLocalM3uLoader(contentResolver)
    private val catalogIngestor = InitialLiveCatalogIngestor(
        persistence = RoomLiveCatalogPersistence(database),
        sensitiveValueStore = sensitiveValueStore,
    )

    suspend fun addRemoteM3u(
        name: String,
        playlistUrl: String,
    ): SourceOnboardingResult {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.InvalidName)
        }

        val normalizedUrl = playlistUrl.trim()
        val playlist = when (val loaded = remoteM3uLoader.load(normalizedUrl)) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        return persistSourceAndCatalog(
            name = normalizedName,
            sourceKind = SourceKinds.REMOTE_M3U,
            locatorValue = normalizedUrl,
            credentialRef = null,
            catalog = InitialLiveCatalogFactory.fromM3u(playlist),
        )
    }

    suspend fun addLocalM3u(
        name: String,
        documentUri: String,
    ): SourceOnboardingResult {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.InvalidName)
        }

        val normalizedDocumentUri = documentUri.trim()
        val playlist = when (val loaded = localM3uLoader.load(normalizedDocumentUri)) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        return persistSourceAndCatalog(
            name = normalizedName,
            sourceKind = SourceKinds.LOCAL_M3U,
            locatorValue = normalizedDocumentUri,
            credentialRef = null,
            catalog = InitialLiveCatalogFactory.fromM3u(playlist),
        )
    }

    suspend fun addXtream(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowCleartext: Boolean = false,
    ): SourceOnboardingResult {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.InvalidName)
        }

        val validatedServer = when (val validation = SourceValidator.validateXtreamServer(serverUrl)) {
            is UrlValidationResult.Invalid -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(validation.error),
                )
            }
            is UrlValidationResult.Valid -> validation
        }
        if (validatedServer.usesCleartext && !allowCleartext) {
            return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.SourceFailure(
                    SourceError.CleartextTransportRequiresOptIn,
                ),
            )
        }
        val normalizedServerUrl = validatedServer.normalizedUrl
        val credentials = XtreamCredentials(
            username = username.trim(),
            password = password,
        )

        when (
            val validation = xtreamClient.validateAccount(
                serverUrl = normalizedServerUrl,
                credentials = credentials,
                allowCleartext = allowCleartext,
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
                serverUrl = normalizedServerUrl,
                credentials = credentials,
                allowCleartext = allowCleartext,
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
                serverUrl = normalizedServerUrl,
                credentials = credentials,
                allowCleartext = allowCleartext,
            )
        ) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        val credentialRef = try {
            credentialStore.put(credentials)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.SecureStorageFailure,
            )
        }

        val sourceLocator = XtreamSourceLocatorCodec.encode(
            XtreamSourceLocator(
                serverUrl = normalizedServerUrl,
                allowCleartext = allowCleartext,
            ),
        )
        val result = try {
            persistSourceAndCatalog(
                name = normalizedName,
                sourceKind = SourceKinds.XTREAM,
                locatorValue = sourceLocator,
                credentialRef = credentialRef,
                catalog = InitialLiveCatalogFactory.fromXtream(categories, streams),
            )
        } catch (cancelled: CancellationException) {
            runCatching { credentialStore.delete(credentialRef) }
            throw cancelled
        }
        if (result is SourceOnboardingResult.Failure) {
            runCatching { credentialStore.delete(credentialRef) }
        }
        return result
    }

    private suspend fun persistSourceAndCatalog(
        name: String,
        sourceKind: String,
        locatorValue: String,
        credentialRef: CredentialRef?,
        catalog: IncomingLiveCatalog,
    ): SourceOnboardingResult {
        val sourceId = UUID.randomUUID().toString()
        val locatorRef = try {
            sensitiveValueStore.put(locatorValue)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.SecureStorageFailure,
            )
        }

        val now = System.currentTimeMillis()
        val pendingSource = PlaylistSourceEntity(
            sourceId = sourceId,
            name = name,
            sourceKind = sourceKind,
            locatorRef = locatorRef.value,
            credentialRef = credentialRef?.value,
            enabled = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        try {
            database.playlistSourceDao().upsert(pendingSource)
        } catch (error: Exception) {
            rollbackSource(sourceId, locatorRef)
            error.rethrowCancellation()
            return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.PersistenceFailure,
            )
        }

        val ingestResult = try {
            catalogIngestor.ingest(
                sourceId = sourceId,
                generation = now,
                catalog = catalog,
            )
        } catch (error: Exception) {
            rollbackSource(sourceId, locatorRef)
            error.rethrowCancellation()
            return SourceOnboardingResult.Failure(
                SourceOnboardingFailure.CatalogImportFailure,
            )
        }

        return when (ingestResult) {
            is InitialLiveCatalogIngestResult.Success -> {
                try {
                    database.playlistSourceDao().upsert(
                        pendingSource.copy(
                            enabled = true,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                } catch (error: Exception) {
                    rollbackSource(sourceId, locatorRef)
                    error.rethrowCancellation()
                    return SourceOnboardingResult.Failure(
                        SourceOnboardingFailure.PersistenceFailure,
                    )
                }
                SourceOnboardingResult.Success(
                    sourceId = sourceId,
                    channelCount = ingestResult.channelCount,
                )
            }
            else -> {
                rollbackSource(sourceId, locatorRef)
                SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.CatalogImportFailure,
                )
            }
        }
    }

    private suspend fun rollbackSource(
        sourceId: String,
        locatorRef: SensitiveValueRef,
    ) {
        withContext(NonCancellable) {
            runCatching { database.playlistSourceDao().deleteById(sourceId) }
            cleanupLocator(locatorRef)
        }
    }

    private fun cleanupLocator(locatorRef: SensitiveValueRef) {
        runCatching { sensitiveValueStore.delete(locatorRef) }
    }
}

private fun Exception.rethrowCancellation() {
    if (this is CancellationException) throw this
}
