package app.ownplay.player.persistence.sync

import androidx.room.withTransaction
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.sync.EncryptedSecretBlob
import app.ownplay.player.sync.EncryptedSecretBlobCodec
import app.ownplay.player.sync.EncryptedSecretBlobReference
import app.ownplay.player.sync.EncryptedSecretBlobStore
import app.ownplay.player.sync.PortableEncryptedSourceSecret
import app.ownplay.player.sync.PortableSourceSecretCrypto
import app.ownplay.player.sync.PortableSourceSecretKeyProvider
import kotlinx.coroutines.CancellationException

internal data class EncryptedSecretReferenceState(
    val syncSourceId: String,
    val localSourceId: String?,
    val sourceKind: String,
    val deleted: Boolean,
    val encryptedSecretRef: String?,
)

internal sealed interface EncryptedSecretReferenceUpdateResult {
    data object Updated : EncryptedSecretReferenceUpdateResult
    data object AlreadyCurrent : EncryptedSecretReferenceUpdateResult
    data object MissingSource : EncryptedSecretReferenceUpdateResult
    data object DeletedSource : EncryptedSecretReferenceUpdateResult
    data class Conflict(val currentRef: String?) : EncryptedSecretReferenceUpdateResult
}

/** Atomic local reference registry used independently of whichever blob backend is selected later. */
internal interface EncryptedSecretReferenceRegistry {
    suspend fun read(syncSourceId: String): EncryptedSecretReferenceState?

    suspend fun compareAndSet(
        syncSourceId: String,
        expectedRef: String?,
        newRef: String?,
    ): EncryptedSecretReferenceUpdateResult

    /** Only non-deleted source references are live for retention purposes. */
    suspend fun liveReferences(): Set<EncryptedSecretBlobReference>
}

internal class RoomEncryptedSecretReferenceRegistry(
    private val database: OwnPlayDatabase,
    private val writer: DeviceSyncLocalMutationWriter = DeviceSyncLocalMutationWriter(database),
) : EncryptedSecretReferenceRegistry {
    override suspend fun read(syncSourceId: String): EncryptedSecretReferenceState? =
        database.deviceSyncDao().sourceBySyncId(syncSourceId)?.toReferenceState()

    override suspend fun compareAndSet(
        syncSourceId: String,
        expectedRef: String?,
        newRef: String?,
    ): EncryptedSecretReferenceUpdateResult = database.withTransaction {
        val current = database.deviceSyncDao().sourceBySyncId(syncSourceId)
            ?: return@withTransaction EncryptedSecretReferenceUpdateResult.MissingSource
        if (current.deleted) {
            return@withTransaction EncryptedSecretReferenceUpdateResult.DeletedSource
        }
        if (current.encryptedSecretRef == newRef) {
            return@withTransaction EncryptedSecretReferenceUpdateResult.AlreadyCurrent
        }
        if (current.encryptedSecretRef != expectedRef) {
            return@withTransaction EncryptedSecretReferenceUpdateResult.Conflict(
                currentRef = current.encryptedSecretRef,
            )
        }
        writer.recordEncryptedSecretRef(syncSourceId, newRef)
        EncryptedSecretReferenceUpdateResult.Updated
    }

    override suspend fun liveReferences(): Set<EncryptedSecretBlobReference> =
        database.deviceSyncDao().allSources()
            .asSequence()
            .filterNot { it.deleted }
            .mapNotNull { it.encryptedSecretRef }
            .map(::EncryptedSecretBlobReference)
            .toSet()

    private fun DeviceSyncSourceEntity.toReferenceState(): EncryptedSecretReferenceState =
        EncryptedSecretReferenceState(
            syncSourceId = syncSourceId,
            localSourceId = localSourceId,
            sourceKind = sourceKind,
            deleted = deleted,
            encryptedSecretRef = encryptedSecretRef,
        )
}

/**
 * Owns the lifecycle of encrypted portable source-secret blobs without knowing the future backend.
 *
 * The sync envelope stores only a versioned content reference. Publishing, importing, key rotation,
 * and retention all go through this coordinator so provider-specific object ids never become part
 * of the synchronization contract.
 */
internal class EncryptedSecretBlobLifecycle(
    private val blobStore: EncryptedSecretBlobStore,
    private val referenceRegistry: EncryptedSecretReferenceRegistry,
    private val keyProvider: PortableSourceSecretKeyProvider,
    private val exportEnvelope: suspend (String) -> SecureSourceExportResult,
    private val importEnvelope: suspend (PortableEncryptedSourceSecret) -> SecureSourceImportResult,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(
        database: OwnPlayDatabase,
        blobStore: EncryptedSecretBlobStore,
        keyProvider: PortableSourceSecretKeyProvider,
        transferBridge: SecureSourceTransferBridge,
        now: () -> Long = System::currentTimeMillis,
    ) : this(
        blobStore = blobStore,
        referenceRegistry = RoomEncryptedSecretReferenceRegistry(database),
        keyProvider = keyProvider,
        exportEnvelope = transferBridge::export,
        importEnvelope = transferBridge::import,
        now = now,
    )

    suspend fun publishLocalSource(localSourceId: String): EncryptedSecretPublishResult {
        val exported = when (val result = exportEnvelope(localSourceId)) {
            is SecureSourceExportResult.Success -> result
            is SecureSourceExportResult.Failure -> {
                return EncryptedSecretPublishResult.ExportFailure(result.reason)
            }
        }
        val state = referenceRegistry.read(exported.syncSourceId)
            ?: return EncryptedSecretPublishResult.MissingSource
        if (state.deleted) return EncryptedSecretPublishResult.DeletedSource
        if (state.localSourceId != localSourceId) {
            return EncryptedSecretPublishResult.IdentityMismatch
        }
        if (state.sourceKind != exported.envelope.sourceKind) {
            return EncryptedSecretPublishResult.IdentityMismatch
        }

        return publishEnvelope(
            envelope = exported.envelope,
            expectedRef = state.encryptedSecretRef,
        )
    }

    suspend fun importReferencedSource(syncSourceId: String): EncryptedSecretImportResult {
        val state = referenceRegistry.read(syncSourceId)
            ?: return EncryptedSecretImportResult.MissingSource
        if (state.deleted) return EncryptedSecretImportResult.DeletedSource
        val refValue = state.encryptedSecretRef
            ?: return EncryptedSecretImportResult.ReferenceMissing
        val reference = try {
            EncryptedSecretBlobReference(refValue)
        } catch (_: IllegalArgumentException) {
            return EncryptedSecretImportResult.InvalidReference
        }
        val envelope = when (val loaded = loadEnvelope(reference)) {
            is BlobEnvelopeLoadResult.Success -> loaded.envelope
            BlobEnvelopeLoadResult.Missing -> return EncryptedSecretImportResult.BlobMissing
            BlobEnvelopeLoadResult.Invalid -> return EncryptedSecretImportResult.BlobIntegrityFailure
            BlobEnvelopeLoadResult.StoreFailure -> return EncryptedSecretImportResult.StoreFailure
        }
        if (envelope.syncSourceId != syncSourceId || envelope.sourceKind != state.sourceKind) {
            return EncryptedSecretImportResult.IdentityMismatch
        }

        return when (val imported = importEnvelope(envelope)) {
            is SecureSourceImportResult.Success -> EncryptedSecretImportResult.Imported(
                sourceId = imported.sourceId,
                channelCount = imported.channelCount,
            )
            is SecureSourceImportResult.AlreadyMaterialized -> {
                EncryptedSecretImportResult.AlreadyMaterialized(imported.sourceId)
            }
            is SecureSourceImportResult.Failure -> EncryptedSecretImportResult.ImportFailure(imported.reason)
        }
    }

    suspend fun rotateReference(syncSourceId: String): EncryptedSecretRotationResult {
        val state = referenceRegistry.read(syncSourceId)
            ?: return EncryptedSecretRotationResult.MissingSource
        if (state.deleted) return EncryptedSecretRotationResult.DeletedSource
        val oldRefValue = state.encryptedSecretRef
            ?: return EncryptedSecretRotationResult.ReferenceMissing
        val oldReference = try {
            EncryptedSecretBlobReference(oldRefValue)
        } catch (_: IllegalArgumentException) {
            return EncryptedSecretRotationResult.InvalidReference
        }
        val oldEnvelope = when (val loaded = loadEnvelope(oldReference)) {
            is BlobEnvelopeLoadResult.Success -> loaded.envelope
            BlobEnvelopeLoadResult.Missing -> return EncryptedSecretRotationResult.BlobMissing
            BlobEnvelopeLoadResult.Invalid -> return EncryptedSecretRotationResult.BlobIntegrityFailure
            BlobEnvelopeLoadResult.StoreFailure -> return EncryptedSecretRotationResult.StoreFailure
        }
        if (oldEnvelope.syncSourceId != syncSourceId || oldEnvelope.sourceKind != state.sourceKind) {
            return EncryptedSecretRotationResult.IdentityMismatch
        }
        val currentKey = try {
            keyProvider.currentKey()
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretRotationResult.KeyFailure
        }
        if (oldEnvelope.keyId == currentKey.keyId) {
            return EncryptedSecretRotationResult.AlreadyCurrent(oldReference)
        }

        val secret = try {
            PortableSourceSecretCrypto.decrypt(oldEnvelope, keyProvider)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretRotationResult.DecryptionFailure
        }
        val rotated = try {
            PortableSourceSecretCrypto.encrypt(syncSourceId, secret, keyProvider)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretRotationResult.EncryptionFailure
        }
        return when (
            val published = publishEnvelope(
                envelope = rotated,
                expectedRef = oldRefValue,
            )
        ) {
            is EncryptedSecretPublishResult.Published -> EncryptedSecretRotationResult.Rotated(
                previousReference = oldReference,
                newReference = published.reference,
                blobCreated = published.blobCreated,
            )
            EncryptedSecretPublishResult.MissingSource -> EncryptedSecretRotationResult.MissingSource
            EncryptedSecretPublishResult.DeletedSource -> EncryptedSecretRotationResult.DeletedSource
            EncryptedSecretPublishResult.IdentityMismatch -> EncryptedSecretRotationResult.IdentityMismatch
            EncryptedSecretPublishResult.ReferenceConflict -> EncryptedSecretRotationResult.ReferenceConflict
            EncryptedSecretPublishResult.StoreFailure -> EncryptedSecretRotationResult.StoreFailure
            EncryptedSecretPublishResult.MetadataFailure -> EncryptedSecretRotationResult.MetadataFailure
            is EncryptedSecretPublishResult.ExportFailure -> error("Rotation does not export a source")
        }
    }

    suspend fun pruneUnreferenced(
        retentionMillis: Long,
        additionalLiveReferences: Set<EncryptedSecretBlobReference> = emptySet(),
    ): EncryptedSecretPruneResult {
        require(retentionMillis >= 0L)
        val live = try {
            referenceRegistry.liveReferences() + additionalLiveReferences
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretPruneResult.RegistryFailure
        }
        val objects = try {
            blobStore.list()
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretPruneResult.StoreFailure
        }
        val cutoff = (now().coerceAtLeast(0L) - retentionMillis).coerceAtLeast(0L)
        val removed = mutableListOf<EncryptedSecretBlobReference>()
        for (metadata in objects.sortedBy { it.reference.value }) {
            if (metadata.reference in live || metadata.createdAtEpochMillis > cutoff) continue
            val deleted = try {
                blobStore.delete(metadata.reference)
            } catch (error: Exception) {
                error.rethrowCancellation()
                return EncryptedSecretPruneResult.PartialFailure(removed.toList())
            }
            if (deleted) removed += metadata.reference
        }
        return EncryptedSecretPruneResult.Success(removed)
    }

    private suspend fun publishEnvelope(
        envelope: PortableEncryptedSourceSecret,
        expectedRef: String?,
    ): EncryptedSecretPublishResult {
        val payload = try {
            EncryptedSecretBlobCodec.encode(envelope)
        } catch (_: IllegalArgumentException) {
            return EncryptedSecretPublishResult.MetadataFailure
        }
        val blob = try {
            EncryptedSecretBlob.create(payload, now().coerceAtLeast(0L))
        } finally {
            payload.fill(0)
        }
        val put = try {
            blobStore.put(blob)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return EncryptedSecretPublishResult.StoreFailure
        }
        if (
            put.metadata.reference != blob.reference ||
            put.metadata.sizeBytes != blob.sizeBytes.toLong()
        ) {
            if (put.created) runCatching { blobStore.delete(put.metadata.reference) }
            return EncryptedSecretPublishResult.StoreFailure
        }

        val updated = try {
            referenceRegistry.compareAndSet(
                syncSourceId = envelope.syncSourceId,
                expectedRef = expectedRef,
                newRef = blob.reference.value,
            )
        } catch (error: Exception) {
            error.rethrowCancellation()
            if (put.created) runCatching { blobStore.delete(blob.reference) }
            return EncryptedSecretPublishResult.MetadataFailure
        }
        return when (updated) {
            EncryptedSecretReferenceUpdateResult.Updated,
            EncryptedSecretReferenceUpdateResult.AlreadyCurrent,
            -> EncryptedSecretPublishResult.Published(
                reference = blob.reference,
                blobCreated = put.created,
            )

            EncryptedSecretReferenceUpdateResult.MissingSource -> {
                if (put.created) runCatching { blobStore.delete(blob.reference) }
                EncryptedSecretPublishResult.MissingSource
            }
            EncryptedSecretReferenceUpdateResult.DeletedSource -> {
                if (put.created) runCatching { blobStore.delete(blob.reference) }
                EncryptedSecretPublishResult.DeletedSource
            }
            is EncryptedSecretReferenceUpdateResult.Conflict -> {
                if (updated.currentRef != blob.reference.value && put.created) {
                    runCatching { blobStore.delete(blob.reference) }
                }
                EncryptedSecretPublishResult.ReferenceConflict
            }
        }
    }

    private suspend fun loadEnvelope(reference: EncryptedSecretBlobReference): BlobEnvelopeLoadResult {
        val blob = try {
            blobStore.get(reference)
        } catch (error: Exception) {
            error.rethrowCancellation()
            return BlobEnvelopeLoadResult.StoreFailure
        } ?: return BlobEnvelopeLoadResult.Missing
        if (blob.reference != reference) return BlobEnvelopeLoadResult.Invalid
        val payload = blob.payloadCopy()
        return try {
            if (!reference.verifies(payload)) {
                BlobEnvelopeLoadResult.Invalid
            } else {
                val envelope = try {
                    EncryptedSecretBlobCodec.decode(payload)
                } catch (_: Exception) {
                    return BlobEnvelopeLoadResult.Invalid
                }
                BlobEnvelopeLoadResult.Success(envelope)
            }
        } finally {
            payload.fill(0)
        }
    }
}

internal sealed interface EncryptedSecretPublishResult {
    data class Published(
        val reference: EncryptedSecretBlobReference,
        val blobCreated: Boolean,
    ) : EncryptedSecretPublishResult

    data class ExportFailure(val reason: SecureSourceExportFailure) : EncryptedSecretPublishResult
    data object MissingSource : EncryptedSecretPublishResult
    data object DeletedSource : EncryptedSecretPublishResult
    data object IdentityMismatch : EncryptedSecretPublishResult
    data object ReferenceConflict : EncryptedSecretPublishResult
    data object StoreFailure : EncryptedSecretPublishResult
    data object MetadataFailure : EncryptedSecretPublishResult
}

internal sealed interface EncryptedSecretImportResult {
    data class Imported(val sourceId: String, val channelCount: Int) : EncryptedSecretImportResult
    data class AlreadyMaterialized(val sourceId: String) : EncryptedSecretImportResult
    data class ImportFailure(val reason: SecureSourceImportFailure) : EncryptedSecretImportResult
    data object MissingSource : EncryptedSecretImportResult
    data object DeletedSource : EncryptedSecretImportResult
    data object ReferenceMissing : EncryptedSecretImportResult
    data object InvalidReference : EncryptedSecretImportResult
    data object BlobMissing : EncryptedSecretImportResult
    data object BlobIntegrityFailure : EncryptedSecretImportResult
    data object IdentityMismatch : EncryptedSecretImportResult
    data object StoreFailure : EncryptedSecretImportResult
}

internal sealed interface EncryptedSecretRotationResult {
    data class Rotated(
        val previousReference: EncryptedSecretBlobReference,
        val newReference: EncryptedSecretBlobReference,
        val blobCreated: Boolean,
    ) : EncryptedSecretRotationResult

    data class AlreadyCurrent(val reference: EncryptedSecretBlobReference) : EncryptedSecretRotationResult
    data object MissingSource : EncryptedSecretRotationResult
    data object DeletedSource : EncryptedSecretRotationResult
    data object ReferenceMissing : EncryptedSecretRotationResult
    data object InvalidReference : EncryptedSecretRotationResult
    data object BlobMissing : EncryptedSecretRotationResult
    data object BlobIntegrityFailure : EncryptedSecretRotationResult
    data object IdentityMismatch : EncryptedSecretRotationResult
    data object ReferenceConflict : EncryptedSecretRotationResult
    data object KeyFailure : EncryptedSecretRotationResult
    data object DecryptionFailure : EncryptedSecretRotationResult
    data object EncryptionFailure : EncryptedSecretRotationResult
    data object StoreFailure : EncryptedSecretRotationResult
    data object MetadataFailure : EncryptedSecretRotationResult
}

internal sealed interface EncryptedSecretPruneResult {
    data class Success(val removed: List<EncryptedSecretBlobReference>) : EncryptedSecretPruneResult
    data class PartialFailure(val removedBeforeFailure: List<EncryptedSecretBlobReference>) : EncryptedSecretPruneResult
    data object RegistryFailure : EncryptedSecretPruneResult
    data object StoreFailure : EncryptedSecretPruneResult
}

private sealed interface BlobEnvelopeLoadResult {
    data class Success(val envelope: PortableEncryptedSourceSecret) : BlobEnvelopeLoadResult
    data object Missing : BlobEnvelopeLoadResult
    data object Invalid : BlobEnvelopeLoadResult
    data object StoreFailure : BlobEnvelopeLoadResult
}

private fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}
