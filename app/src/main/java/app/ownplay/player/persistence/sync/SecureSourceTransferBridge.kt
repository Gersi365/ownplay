package app.ownplay.player.persistence.sync

import androidx.room.withTransaction
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
import app.ownplay.player.source.m3u.RemoteM3uLoader
import app.ownplay.player.source.xtream.XtreamClient
import app.ownplay.player.source.xtream.XtreamSourceLocator
import app.ownplay.player.source.xtream.XtreamSourceLocatorCodec
import app.ownplay.player.sync.PORTABLE_SOURCE_KIND_REMOTE_M3U
import app.ownplay.player.sync.PORTABLE_SOURCE_KIND_XTREAM
import app.ownplay.player.sync.PortableEncryptedSourceSecret
import app.ownplay.player.sync.PortableSourceSecret
import app.ownplay.player.sync.PortableSourceSecretCrypto
import app.ownplay.player.sync.PortableSourceSecretKeyProvider
import java.security.GeneralSecurityException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Bridges local Android-keystore-backed source storage and portable encrypted source secrets.
 *
 * Export never exposes local keystore/sensitive-store references to transport. Import allocates new
 * local references on the receiving device and preserves the existing syncSourceId clocks rather
 * than recording a new local source mutation.
 */
internal class SecureSourceTransferBridge(
    private val database: OwnPlayDatabase,
    private val sensitiveValueStore: SensitiveValueStore,
    private val credentialStore: CredentialStore,
    private val keyProvider: PortableSourceSecretKeyProvider,
    private val catalogResolver: PortableSourceCatalogResolver = NetworkPortableSourceCatalogResolver(),
    sourceIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val persistence = RoomSecureSourceMaterializer(
        database = database,
        sensitiveValueStore = sensitiveValueStore,
        sourceIdFactory = sourceIdFactory,
    )

    suspend fun export(localSourceId: String): SecureSourceExportResult {
        val source = database.playlistSourceDao().getById(localSourceId)
            ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.NotFound)
        val syncSource = database.deviceSyncDao().sourceByLocalId(localSourceId)
            ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.SyncIdentityMissing)
        if (syncSource.deleted) {
            return SecureSourceExportResult.Failure(SecureSourceExportFailure.SourceDeleted)
        }
        if (syncSource.sourceKind != source.sourceKind) {
            return SecureSourceExportResult.Failure(SecureSourceExportFailure.SyncIdentityMismatch)
        }

        val locatorValue = try {
            sensitiveValueStore.get(SensitiveValueRef(source.locatorRef))
        } catch (error: Exception) {
            error.rethrowCancellation()
            null
        } ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.SecureStorageFailure)

        val secret = when (source.sourceKind) {
            SourceKinds.XTREAM -> {
                val locator = runCatching { XtreamSourceLocatorCodec.parse(locatorValue) }.getOrNull()
                    ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.InvalidStoredLocator)
                val credentialRef = source.credentialRef?.let(::CredentialRef)
                    ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.CredentialUnavailable)
                val credentials = try {
                    credentialStore.get(credentialRef)
                } catch (error: Exception) {
                    error.rethrowCancellation()
                    null
                } ?: return SecureSourceExportResult.Failure(SecureSourceExportFailure.CredentialUnavailable)

                PortableSourceSecret.Xtream(
                    serverUrl = locator.serverUrl,
                    username = credentials.username,
                    password = credentials.password,
                    allowCleartext = locator.allowCleartext,
                )
            }

            SourceKinds.REMOTE_M3U -> PortableSourceSecret.RemoteM3u(
                playlistUrl = locatorValue,
                epgUrl = null,
            )

            SourceKinds.LOCAL_M3U -> {
                return SecureSourceExportResult.Failure(SecureSourceExportFailure.DeviceLocalSource)
            }

            else -> return SecureSourceExportResult.Failure(SecureSourceExportFailure.UnsupportedSourceKind)
        }

        val encrypted = try {
            PortableSourceSecretCrypto.encrypt(
                syncSourceId = syncSource.syncSourceId,
                secret = secret,
                keyProvider = keyProvider,
            )
        } catch (error: Exception) {
            error.rethrowCancellation()
            return SecureSourceExportResult.Failure(SecureSourceExportFailure.EncryptionFailure)
        }

        return SecureSourceExportResult.Success(
            syncSourceId = syncSource.syncSourceId,
            envelope = encrypted,
        )
    }

    suspend fun import(envelope: PortableEncryptedSourceSecret): SecureSourceImportResult {
        val syncSource = database.deviceSyncDao().sourceBySyncId(envelope.syncSourceId)
            ?: return SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMissing)
        if (syncSource.deleted) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SourceDeleted)
        }
        if (syncSource.sourceKind != envelope.sourceKind) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMismatch)
        }
        syncSource.localSourceId?.let { localSourceId ->
            if (database.playlistSourceDao().getById(localSourceId) != null) {
                return SecureSourceImportResult.AlreadyMaterialized(localSourceId)
            }
        }

        val secret = try {
            PortableSourceSecretCrypto.decrypt(envelope, keyProvider)
        } catch (_: GeneralSecurityException) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.DecryptionFailure)
        } catch (_: IllegalArgumentException) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.DecryptionFailure)
        }
        if (secret.sourceKind != syncSource.sourceKind) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMismatch)
        }

        val resolved = when (val resolution = catalogResolver.resolve(secret)) {
            is PortableSourceCatalogResolution.Success -> resolution.value
            is PortableSourceCatalogResolution.SourceFailure -> {
                return SecureSourceImportResult.Failure(
                    SecureSourceImportFailure.SourceFailure(resolution.error),
                )
            }
            PortableSourceCatalogResolution.RemoteM3uEpgUnsupported -> {
                return SecureSourceImportResult.Failure(
                    SecureSourceImportFailure.RemoteM3uEpgUnsupported,
                )
            }
        }

        val latestBeforeStorage = database.deviceSyncDao().sourceBySyncId(envelope.syncSourceId)
            ?: return SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMissing)
        if (latestBeforeStorage.deleted) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SourceDeleted)
        }
        if (latestBeforeStorage.sourceKind != resolved.sourceKind) {
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMismatch)
        }
        latestBeforeStorage.localSourceId?.let { localSourceId ->
            if (database.playlistSourceDao().getById(localSourceId) != null) {
                return SecureSourceImportResult.AlreadyMaterialized(localSourceId)
            }
        }

        val locatorRef = try {
            sensitiveValueStore.put(resolved.locatorValue)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.SecureStorageFailure)
        }

        val credentialRef = if (resolved.credentials == null) {
            null
        } else {
            try {
                credentialStore.put(resolved.credentials)
            } catch (error: Exception) {
                runCatching { sensitiveValueStore.delete(locatorRef) }
                error.rethrowCancellation()
                return SecureSourceImportResult.Failure(SecureSourceImportFailure.SecureStorageFailure)
            }
        }

        val materialized = try {
            persistence.materialize(
                SecureSourceMaterializationRequest(
                    syncSourceId = envelope.syncSourceId,
                    sourceKind = resolved.sourceKind,
                    locatorRef = locatorRef,
                    credentialRef = credentialRef,
                    catalog = resolved.catalog,
                ),
            )
        } catch (cancelled: CancellationException) {
            cleanupAllocated(locatorRef, credentialRef)
            throw cancelled
        } catch (_: Exception) {
            cleanupAllocated(locatorRef, credentialRef)
            return SecureSourceImportResult.Failure(SecureSourceImportFailure.PersistenceFailure)
        }

        return when (materialized) {
            is SecureSourceMaterializationResult.Success -> SecureSourceImportResult.Success(
                sourceId = materialized.sourceId,
                channelCount = materialized.channelCount,
            )

            is SecureSourceMaterializationResult.AlreadyMaterialized -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.AlreadyMaterialized(materialized.sourceId)
            }

            SecureSourceMaterializationResult.SyncIdentityMissing -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMissing)
            }

            SecureSourceMaterializationResult.SourceDeleted -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.Failure(SecureSourceImportFailure.SourceDeleted)
            }

            SecureSourceMaterializationResult.SyncIdentityMismatch -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.Failure(SecureSourceImportFailure.SyncIdentityMismatch)
            }

            SecureSourceMaterializationResult.CatalogImportFailure -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.Failure(SecureSourceImportFailure.CatalogImportFailure)
            }

            SecureSourceMaterializationResult.PersistenceFailure -> {
                cleanupAllocated(locatorRef, credentialRef)
                SecureSourceImportResult.Failure(SecureSourceImportFailure.PersistenceFailure)
            }
        }
    }

    private suspend fun cleanupAllocated(
        locatorRef: SensitiveValueRef,
        credentialRef: CredentialRef?,
    ) = withContext(NonCancellable) {
        runCatching { sensitiveValueStore.delete(locatorRef) }
        credentialRef?.let { ref -> runCatching { credentialStore.delete(ref) } }
        Unit
    }
}

internal sealed interface SecureSourceExportResult {
    data class Success(
        val syncSourceId: String,
        val envelope: PortableEncryptedSourceSecret,
    ) : SecureSourceExportResult

    data class Failure(val reason: SecureSourceExportFailure) : SecureSourceExportResult
}

internal enum class SecureSourceExportFailure {
    NotFound,
    SyncIdentityMissing,
    SyncIdentityMismatch,
    SourceDeleted,
    DeviceLocalSource,
    UnsupportedSourceKind,
    InvalidStoredLocator,
    CredentialUnavailable,
    SecureStorageFailure,
    EncryptionFailure,
}

internal sealed interface SecureSourceImportResult {
    data class Success(
        val sourceId: String,
        val channelCount: Int,
    ) : SecureSourceImportResult

    data class AlreadyMaterialized(val sourceId: String) : SecureSourceImportResult

    data class Failure(val reason: SecureSourceImportFailure) : SecureSourceImportResult
}

internal sealed interface SecureSourceImportFailure {
    data object SyncIdentityMissing : SecureSourceImportFailure
    data object SyncIdentityMismatch : SecureSourceImportFailure
    data object SourceDeleted : SecureSourceImportFailure
    data object DecryptionFailure : SecureSourceImportFailure
    data object SecureStorageFailure : SecureSourceImportFailure
    data object RemoteM3uEpgUnsupported : SecureSourceImportFailure
    data object CatalogImportFailure : SecureSourceImportFailure
    data object PersistenceFailure : SecureSourceImportFailure
    data class SourceFailure(val error: SourceError) : SecureSourceImportFailure
}

internal data class ResolvedPortableSource(
    val sourceKind: String,
    val locatorValue: String,
    val credentials: XtreamCredentials?,
    val catalog: IncomingLiveCatalog,
) {
    override fun toString(): String =
        "ResolvedPortableSource(sourceKind=$sourceKind, locatorValue=<redacted>, " +
            "credentials=${if (credentials == null) "null" else "<redacted>"}, catalog=<redacted>)"
}

internal sealed interface PortableSourceCatalogResolution {
    data class Success(val value: ResolvedPortableSource) : PortableSourceCatalogResolution
    data class SourceFailure(val error: SourceError) : PortableSourceCatalogResolution
    data object RemoteM3uEpgUnsupported : PortableSourceCatalogResolution
}

internal fun interface PortableSourceCatalogResolver {
    suspend fun resolve(secret: PortableSourceSecret): PortableSourceCatalogResolution
}

internal class NetworkPortableSourceCatalogResolver(
    private val xtreamClient: XtreamClient = XtreamClient(),
    private val remoteM3uLoader: RemoteM3uLoader = RemoteM3uLoader(),
) : PortableSourceCatalogResolver {
    override suspend fun resolve(secret: PortableSourceSecret): PortableSourceCatalogResolution =
        when (secret) {
            is PortableSourceSecret.Xtream -> resolveXtream(secret)
            is PortableSourceSecret.RemoteM3u -> resolveRemoteM3u(secret)
        }

    private suspend fun resolveXtream(
        secret: PortableSourceSecret.Xtream,
    ): PortableSourceCatalogResolution {
        val validatedServer = when (val validation = SourceValidator.validateXtreamServer(secret.serverUrl)) {
            is UrlValidationResult.Invalid -> return PortableSourceCatalogResolution.SourceFailure(validation.error)
            is UrlValidationResult.Valid -> validation
        }
        if (validatedServer.usesCleartext && !secret.allowCleartext) {
            return PortableSourceCatalogResolution.SourceFailure(
                SourceError.CleartextTransportRequiresOptIn,
            )
        }
        val credentials = XtreamCredentials(
            username = secret.username.trim(),
            password = secret.password,
        )
        when (
            val validation = xtreamClient.validateAccount(
                serverUrl = validatedServer.normalizedUrl,
                credentials = credentials,
                allowCleartext = secret.allowCleartext,
            )
        ) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> return PortableSourceCatalogResolution.SourceFailure(validation.error)
        }
        val categories = when (
            val result = xtreamClient.getLiveCategories(
                serverUrl = validatedServer.normalizedUrl,
                credentials = credentials,
                allowCleartext = secret.allowCleartext,
            )
        ) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> return PortableSourceCatalogResolution.SourceFailure(result.error)
        }
        val streams = when (
            val result = xtreamClient.getLiveStreams(
                serverUrl = validatedServer.normalizedUrl,
                credentials = credentials,
                allowCleartext = secret.allowCleartext,
            )
        ) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> return PortableSourceCatalogResolution.SourceFailure(result.error)
        }
        return PortableSourceCatalogResolution.Success(
            ResolvedPortableSource(
                sourceKind = PORTABLE_SOURCE_KIND_XTREAM,
                locatorValue = XtreamSourceLocatorCodec.encode(
                    XtreamSourceLocator(
                        serverUrl = validatedServer.normalizedUrl,
                        allowCleartext = secret.allowCleartext,
                    ),
                ),
                credentials = credentials,
                catalog = InitialLiveCatalogFactory.fromXtream(categories, streams),
            ),
        )
    }

    private suspend fun resolveRemoteM3u(
        secret: PortableSourceSecret.RemoteM3u,
    ): PortableSourceCatalogResolution {
        if (secret.epgUrl != null) {
            return PortableSourceCatalogResolution.RemoteM3uEpgUnsupported
        }
        val validated = when (val validation = SourceValidator.validateRemotePlaylistUrl(secret.playlistUrl)) {
            is UrlValidationResult.Invalid -> return PortableSourceCatalogResolution.SourceFailure(validation.error)
            is UrlValidationResult.Valid -> validation
        }
        if (validated.usesCleartext) {
            return PortableSourceCatalogResolution.SourceFailure(
                SourceError.CleartextTransportRequiresOptIn,
            )
        }
        val playlist = when (val loaded = remoteM3uLoader.load(validated.normalizedUrl)) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> return PortableSourceCatalogResolution.SourceFailure(loaded.error)
        }
        return PortableSourceCatalogResolution.Success(
            ResolvedPortableSource(
                sourceKind = PORTABLE_SOURCE_KIND_REMOTE_M3U,
                locatorValue = validated.normalizedUrl,
                credentials = null,
                catalog = InitialLiveCatalogFactory.fromM3u(playlist),
            ),
        )
    }
}

internal data class SecureSourceMaterializationRequest(
    val syncSourceId: String,
    val sourceKind: String,
    val locatorRef: SensitiveValueRef,
    val credentialRef: CredentialRef?,
    val catalog: IncomingLiveCatalog,
) {
    override fun toString(): String =
        "SecureSourceMaterializationRequest(syncSourceId=$syncSourceId, sourceKind=$sourceKind, " +
            "locatorRef=<opaque>, credentialRef=${if (credentialRef == null) "null" else "<opaque>"}, " +
            "catalog=<redacted>)"
}

internal sealed interface SecureSourceMaterializationResult {
    data class Success(
        val sourceId: String,
        val channelCount: Int,
    ) : SecureSourceMaterializationResult

    data class AlreadyMaterialized(val sourceId: String) : SecureSourceMaterializationResult
    data object SyncIdentityMissing : SecureSourceMaterializationResult
    data object SyncIdentityMismatch : SecureSourceMaterializationResult
    data object SourceDeleted : SecureSourceMaterializationResult
    data object CatalogImportFailure : SecureSourceMaterializationResult
    data object PersistenceFailure : SecureSourceMaterializationResult
}

internal class RoomSecureSourceMaterializer(
    private val database: OwnPlayDatabase,
    private val sensitiveValueStore: SensitiveValueStore,
    private val sourceIdFactory: () -> String,
) {
    private val catalogIngestor = InitialLiveCatalogIngestor(
        persistence = RoomLiveCatalogPersistence(database),
        sensitiveValueStore = sensitiveValueStore,
    )

    suspend fun materialize(
        request: SecureSourceMaterializationRequest,
    ): SecureSourceMaterializationResult {
        val initialSync = database.deviceSyncDao().sourceBySyncId(request.syncSourceId)
            ?: return SecureSourceMaterializationResult.SyncIdentityMissing
        if (initialSync.deleted) return SecureSourceMaterializationResult.SourceDeleted
        if (initialSync.sourceKind != request.sourceKind) {
            return SecureSourceMaterializationResult.SyncIdentityMismatch
        }
        initialSync.localSourceId?.let { existingId ->
            if (database.playlistSourceDao().getById(existingId) != null) {
                return SecureSourceMaterializationResult.AlreadyMaterialized(existingId)
            }
        }

        val sourceId = sourceIdFactory().also { require(it.isNotBlank()) }
        if (database.playlistSourceDao().getById(sourceId) != null) {
            return SecureSourceMaterializationResult.PersistenceFailure
        }
        val now = System.currentTimeMillis().coerceAtLeast(0L)
        val pending = PlaylistSourceEntity(
            sourceId = sourceId,
            name = initialSync.displayName,
            sourceKind = request.sourceKind,
            locatorRef = request.locatorRef.value,
            credentialRef = request.credentialRef?.value,
            enabled = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        try {
            database.playlistSourceDao().upsert(pending)
        } catch (error: Exception) {
            rollbackCandidate(sourceId)
            error.rethrowCancellation()
            return SecureSourceMaterializationResult.PersistenceFailure
        }

        val ingest = try {
            catalogIngestor.ingest(
                sourceId = sourceId,
                generation = now,
                catalog = request.catalog,
            )
        } catch (error: Exception) {
            rollbackCandidate(sourceId)
            error.rethrowCancellation()
            return SecureSourceMaterializationResult.CatalogImportFailure
        }
        if (ingest !is InitialLiveCatalogIngestResult.Success) {
            rollbackCandidate(sourceId)
            return SecureSourceMaterializationResult.CatalogImportFailure
        }

        val finalResult = try {
            database.withTransaction {
                val current = database.deviceSyncDao().sourceBySyncId(request.syncSourceId)
                    ?: throw MaterializationStateChanged.SyncIdentityMissing
                if (current.deleted) throw MaterializationStateChanged.SourceDeleted
                if (current.sourceKind != request.sourceKind) {
                    throw MaterializationStateChanged.SyncIdentityMismatch
                }
                current.localSourceId?.let { existingId ->
                    if (existingId != sourceId && database.playlistSourceDao().getById(existingId) != null) {
                        throw MaterializationStateChanged.AlreadyMaterialized(existingId)
                    }
                }

                database.playlistSourceDao().upsert(
                    pending.copy(
                        name = current.displayName,
                        enabled = current.enabled,
                        updatedAtEpochMillis = maxOf(
                            now,
                            current.displayNameUpdatedAtEpochMillis,
                            current.enabledUpdatedAtEpochMillis,
                        ),
                    ),
                )
                database.deviceSyncDao().upsertSource(current.copy(localSourceId = sourceId))
                SecureSourceMaterializationResult.Success(
                    sourceId = sourceId,
                    channelCount = ingest.channelCount,
                )
            }
        } catch (cancelled: CancellationException) {
            rollbackCandidate(sourceId)
            throw cancelled
        } catch (changed: MaterializationStateChanged) {
            when (changed) {
                MaterializationStateChanged.SyncIdentityMissing -> SecureSourceMaterializationResult.SyncIdentityMissing
                MaterializationStateChanged.SyncIdentityMismatch -> SecureSourceMaterializationResult.SyncIdentityMismatch
                MaterializationStateChanged.SourceDeleted -> SecureSourceMaterializationResult.SourceDeleted
                is MaterializationStateChanged.AlreadyMaterialized -> {
                    SecureSourceMaterializationResult.AlreadyMaterialized(changed.sourceId)
                }
            }
        } catch (_: Exception) {
            SecureSourceMaterializationResult.PersistenceFailure
        }

        if (finalResult !is SecureSourceMaterializationResult.Success) {
            rollbackCandidate(sourceId)
        }
        return finalResult
    }

    private suspend fun rollbackCandidate(sourceId: String) = withContext(NonCancellable) {
        val channels = runCatching { database.providerCatalogDao().channelsForSource(sourceId) }
            .getOrDefault(emptyList())
        val customizations = runCatching { database.personalizationDao().customizationsForSource(sourceId) }
            .getOrDefault(emptyList())
        val catalogRefs = buildSet {
            channels.forEach { channel ->
                add(SensitiveValueRef(channel.streamLocatorRef))
                channel.logoRef?.let { add(SensitiveValueRef(it)) }
            }
            customizations.forEach { customization ->
                customization.logoOverrideRef?.let { add(SensitiveValueRef(it)) }
            }
        }
        runCatching { database.playlistSourceDao().deleteById(sourceId) }
        runCatching { sensitiveValueStore.deleteAll(catalogRefs) }
        Unit
    }
}

private sealed class MaterializationStateChanged : Exception() {
    data object SyncIdentityMissing : MaterializationStateChanged()
    data object SyncIdentityMismatch : MaterializationStateChanged()
    data object SourceDeleted : MaterializationStateChanged()
    data class AlreadyMaterialized(val sourceId: String) : MaterializationStateChanged()
}

private fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}
