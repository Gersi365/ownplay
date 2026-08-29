package app.ownplay.player.persistence.sync

import app.ownplay.player.sync.EncryptedSecretBlob
import app.ownplay.player.sync.EncryptedSecretBlobMetadata
import app.ownplay.player.sync.EncryptedSecretBlobPutResult
import app.ownplay.player.sync.EncryptedSecretBlobReference
import app.ownplay.player.sync.EncryptedSecretBlobStore
import app.ownplay.player.sync.PORTABLE_SOURCE_KIND_XTREAM
import app.ownplay.player.sync.PortableEncryptedSourceSecret
import app.ownplay.player.sync.PortableSourceSecret
import app.ownplay.player.sync.PortableSourceSecretCrypto
import app.ownplay.player.sync.PortableSourceSecretKey
import app.ownplay.player.sync.PortableSourceSecretKeyProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSecretBlobLifecycleTest {
    @Test
    fun publishStoresCiphertextReferenceAndImportResolvesIt() = runBlocking {
        val keyProvider = MutableKeyProvider(key("old-key"))
        val store = InMemoryBlobStore()
        val registry = FakeReferenceRegistry(
            EncryptedSecretReferenceState(
                syncSourceId = "source-1",
                localSourceId = "local-phone",
                sourceKind = PORTABLE_SOURCE_KIND_XTREAM,
                deleted = false,
                encryptedSecretRef = null,
            ),
        )
        var importedEnvelope: PortableEncryptedSourceSecret? = null
        val lifecycle = EncryptedSecretBlobLifecycle(
            blobStore = store,
            referenceRegistry = registry,
            keyProvider = keyProvider,
            exportEnvelope = { localSourceId ->
                assertEquals("local-phone", localSourceId)
                SecureSourceExportResult.Success(
                    syncSourceId = "source-1",
                    envelope = encryptXtream(keyProvider),
                )
            },
            importEnvelope = { envelope ->
                importedEnvelope = envelope
                SecureSourceImportResult.Success(sourceId = "local-tv", channelCount = 12)
            },
            now = { 1_000L },
        )

        val published = lifecycle.publishLocalSource("local-phone")
        assertTrue(published is EncryptedSecretPublishResult.Published)
        val reference = (published as EncryptedSecretPublishResult.Published).reference
        assertEquals(reference.value, registry.state.encryptedSecretRef)
        assertEquals(1, store.size)

        val imported = lifecycle.importReferencedSource("source-1")
        assertEquals(EncryptedSecretImportResult.Imported("local-tv", 12), imported)
        assertEquals("source-1", importedEnvelope?.syncSourceId)
        assertEquals(PORTABLE_SOURCE_KIND_XTREAM, importedEnvelope?.sourceKind)
    }

    @Test
    fun keyRotationPublishesNewReferenceAndRetentionPrunesOldBlobAfterGracePeriod() = runBlocking {
        val oldKey = key("old-key")
        val newKey = key("new-key")
        val keyProvider = MutableKeyProvider(oldKey, listOf(newKey))
        val store = InMemoryBlobStore()
        val registry = FakeReferenceRegistry(
            EncryptedSecretReferenceState(
                syncSourceId = "source-1",
                localSourceId = "local-phone",
                sourceKind = PORTABLE_SOURCE_KIND_XTREAM,
                deleted = false,
                encryptedSecretRef = null,
            ),
        )
        var clock = 1_000L
        val lifecycle = EncryptedSecretBlobLifecycle(
            blobStore = store,
            referenceRegistry = registry,
            keyProvider = keyProvider,
            exportEnvelope = {
                SecureSourceExportResult.Success("source-1", encryptXtream(keyProvider))
            },
            importEnvelope = { SecureSourceImportResult.AlreadyMaterialized("local-phone") },
            now = { clock },
        )

        val first = lifecycle.publishLocalSource("local-phone") as EncryptedSecretPublishResult.Published
        val oldReference = first.reference
        assertEquals(1, store.size)

        keyProvider.currentKeyId = "new-key"
        clock = 2_000L
        val rotated = lifecycle.rotateReference("source-1") as EncryptedSecretRotationResult.Rotated
        assertEquals(oldReference, rotated.previousReference)
        assertNotEquals(oldReference, rotated.newReference)
        assertEquals(rotated.newReference.value, registry.state.encryptedSecretRef)
        assertEquals(2, store.size)

        clock = 2_500L
        val pruned = lifecycle.pruneUnreferenced(retentionMillis = 1_000L)
            as EncryptedSecretPruneResult.Success
        assertEquals(listOf(oldReference), pruned.removed)
        assertEquals(1, store.size)
        assertTrue(store.contains(rotated.newReference))
    }

    @Test
    fun referenceConflictDoesNotOverwriteNewerStateOrLeaveNewOrphanBlob() = runBlocking {
        val keyProvider = MutableKeyProvider(key("key-1"))
        val store = InMemoryBlobStore()
        val registry = FakeReferenceRegistry(
            EncryptedSecretReferenceState(
                syncSourceId = "source-1",
                localSourceId = "local-phone",
                sourceKind = PORTABLE_SOURCE_KIND_XTREAM,
                deleted = false,
                encryptedSecretRef = null,
            ),
        )
        registry.conflictOnNextUpdate = EncryptedSecretBlobReference.fromPayload(byteArrayOf(9, 9, 9)).value
        val lifecycle = EncryptedSecretBlobLifecycle(
            blobStore = store,
            referenceRegistry = registry,
            keyProvider = keyProvider,
            exportEnvelope = {
                SecureSourceExportResult.Success("source-1", encryptXtream(keyProvider))
            },
            importEnvelope = { error("Import is not expected") },
            now = { 1_000L },
        )

        val result = lifecycle.publishLocalSource("local-phone")

        assertEquals(EncryptedSecretPublishResult.ReferenceConflict, result)
        assertEquals(registry.conflictOnNextUpdateApplied, registry.state.encryptedSecretRef)
        assertEquals(0, store.size)
    }

    private fun encryptXtream(provider: PortableSourceSecretKeyProvider): PortableEncryptedSourceSecret =
        PortableSourceSecretCrypto.encrypt(
            syncSourceId = "source-1",
            secret = PortableSourceSecret.Xtream(
                serverUrl = "https://example.test",
                username = "user",
                password = "password",
            ),
            keyProvider = provider,
        )

    private class FakeReferenceRegistry(
        initial: EncryptedSecretReferenceState,
    ) : EncryptedSecretReferenceRegistry {
        var state = initial
        var conflictOnNextUpdate: String? = null
        var conflictOnNextUpdateApplied: String? = null

        override suspend fun read(syncSourceId: String): EncryptedSecretReferenceState? =
            state.takeIf { it.syncSourceId == syncSourceId }

        override suspend fun compareAndSet(
            syncSourceId: String,
            expectedRef: String?,
            newRef: String?,
        ): EncryptedSecretReferenceUpdateResult {
            if (state.syncSourceId != syncSourceId) {
                return EncryptedSecretReferenceUpdateResult.MissingSource
            }
            if (state.deleted) return EncryptedSecretReferenceUpdateResult.DeletedSource
            conflictOnNextUpdate?.let { forced ->
                conflictOnNextUpdate = null
                conflictOnNextUpdateApplied = forced
                state = state.copy(encryptedSecretRef = forced)
                return EncryptedSecretReferenceUpdateResult.Conflict(forced)
            }
            if (state.encryptedSecretRef == newRef) {
                return EncryptedSecretReferenceUpdateResult.AlreadyCurrent
            }
            if (state.encryptedSecretRef != expectedRef) {
                return EncryptedSecretReferenceUpdateResult.Conflict(state.encryptedSecretRef)
            }
            state = state.copy(encryptedSecretRef = newRef)
            return EncryptedSecretReferenceUpdateResult.Updated
        }

        override suspend fun liveReferences(): Set<EncryptedSecretBlobReference> =
            if (state.deleted || state.encryptedSecretRef == null) {
                emptySet()
            } else {
                setOf(EncryptedSecretBlobReference(requireNotNull(state.encryptedSecretRef)))
            }
    }

    private class InMemoryBlobStore : EncryptedSecretBlobStore {
        private val blobs = linkedMapOf<String, EncryptedSecretBlob>()

        val size: Int
            get() = blobs.size

        fun contains(reference: EncryptedSecretBlobReference): Boolean =
            blobs.containsKey(reference.value)

        override suspend fun put(blob: EncryptedSecretBlob): EncryptedSecretBlobPutResult {
            val existing = blobs[blob.reference.value]
            val stored = existing ?: blob.also { blobs[blob.reference.value] = it }
            return EncryptedSecretBlobPutResult(
                metadata = EncryptedSecretBlobMetadata(
                    reference = stored.reference,
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                    sizeBytes = stored.sizeBytes.toLong(),
                ),
                created = existing == null,
            )
        }

        override suspend fun get(reference: EncryptedSecretBlobReference): EncryptedSecretBlob? =
            blobs[reference.value]

        override suspend fun list(): List<EncryptedSecretBlobMetadata> =
            blobs.values.map { blob ->
                EncryptedSecretBlobMetadata(
                    reference = blob.reference,
                    createdAtEpochMillis = blob.createdAtEpochMillis,
                    sizeBytes = blob.sizeBytes.toLong(),
                )
            }

        override suspend fun delete(reference: EncryptedSecretBlobReference): Boolean =
            blobs.remove(reference.value) != null
    }

    private class MutableKeyProvider(
        current: PortableSourceSecretKey,
        additional: List<PortableSourceSecretKey> = emptyList(),
    ) : PortableSourceSecretKeyProvider {
        private val keys = (listOf(current) + additional).associateBy(PortableSourceSecretKey::keyId)
        var currentKeyId: String = current.keyId

        override fun currentKey(): PortableSourceSecretKey = requireNotNull(keys[currentKeyId])

        override fun keyForId(keyId: String): PortableSourceSecretKey? = keys[keyId]
    }

    private fun key(keyId: String): PortableSourceSecretKey = PortableSourceSecretKey(
        keyId = keyId,
        secretKey = generateAes256Key(),
    )

    private fun generateAes256Key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
