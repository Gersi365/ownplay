package app.ownplay.player.sync

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal const val SYNC_KEY_PROVISIONING_VERSION = 1
private const val SYNC_KEY_BYTES = 32
private const val PROVISIONING_NONCE_BYTES = 12
private const val PROVISIONING_GCM_TAG_BITS = 128
private const val PROVISIONING_CIPHER = "AES/GCM/NoPadding"
private const val SYNC_KEY_ID_PREFIX = "ownplay-sync-key:v1:"

internal data class SyncKeyProvisioningPackage(
    val version: Int = SYNC_KEY_PROVISIONING_VERSION,
    val providerDeviceId: String,
    val recipientDeviceId: String,
    val transcriptHashBase64Url: String,
    val keyId: String,
    val keyEpoch: Long,
    val nonceBase64Url: String,
    val ciphertextBase64Url: String,
) {
    init {
        require(version == SYNC_KEY_PROVISIONING_VERSION)
        require(providerDeviceId.isNotBlank())
        require(recipientDeviceId.isNotBlank())
        require(providerDeviceId != recipientDeviceId)
        require(transcriptHashBase64Url.isNotBlank())
        require(keyId.startsWith(SYNC_KEY_ID_PREFIX))
        require(keyEpoch > 0L)
        require(nonceBase64Url.isNotBlank())
        require(ciphertextBase64Url.isNotBlank())
    }

    override fun toString(): String =
        "SyncKeyProvisioningPackage(version=$version, providerDeviceId=$providerDeviceId, " +
            "recipientDeviceId=$recipientDeviceId, transcriptHash=<opaque>, keyId=<opaque>, " +
            "keyEpoch=$keyEpoch, nonce=<redacted>, ciphertext=<redacted>)"
}

internal data class PairingPeerTrustState(
    val deviceId: String,
    val identityFingerprint: String,
    val revoked: Boolean,
    val knownKeyIds: Set<String>,
)

internal sealed interface PairingPeerTrustResult {
    data object Trusted : PairingPeerTrustResult
    data object AlreadyTrusted : PairingPeerTrustResult
    data object Revoked : PairingPeerTrustResult
    data object IdentityConflict : PairingPeerTrustResult
}

internal data class SyncKeyRotationResult(
    val previousKeyId: String?,
    val newKeyId: String,
    val newEpoch: Long,
)

internal data class PairingPeerRevocationResult(
    val revoked: Boolean,
    val rotationRequired: Boolean,
)

internal sealed interface SyncKeyPackageCreationResult {
    data class Created(
        val value: SyncKeyProvisioningPackage,
        val peerTrust: PairingPeerTrustResult,
    ) : SyncKeyPackageCreationResult

    data object VerificationMismatch : SyncKeyPackageCreationResult
    data object LocalIdentityMismatch : SyncKeyPackageCreationResult
    data object PeerRevoked : SyncKeyPackageCreationResult
    data object PeerIdentityConflict : SyncKeyPackageCreationResult
}

internal sealed interface SyncKeyPackageAcceptanceResult {
    data class Accepted(
        val keyId: String,
        val keyEpoch: Long,
        val peerTrust: PairingPeerTrustResult,
    ) : SyncKeyPackageAcceptanceResult

    data object VerificationMismatch : SyncKeyPackageAcceptanceResult
    data object LocalIdentityMismatch : SyncKeyPackageAcceptanceResult
    data object PeerRevoked : SyncKeyPackageAcceptanceResult
    data object PeerIdentityConflict : SyncKeyPackageAcceptanceResult
    data object TranscriptMismatch : SyncKeyPackageAcceptanceResult
    data object InvalidPackage : SyncKeyPackageAcceptanceResult
    data object DecryptionFailure : SyncKeyPackageAcceptanceResult
    data object KeyIdMismatch : SyncKeyPackageAcceptanceResult
    data object StaleEpoch : SyncKeyPackageAcceptanceResult
}

/**
 * Local provisioning/trust ring used by pairing. It deliberately does not persist or transport keys.
 *
 * A pairing session establishes a pairwise ephemeral session key. That session key wraps a separate
 * 256-bit OwnPlay group sync key, allowing a new TV to receive the existing key instead of forcing
 * every already-paired device to adopt a different pairwise content-encryption key.
 *
 * Revocation is intentionally two-phase: the peer is blocked immediately, then callers rotate the
 * current group key and re-encrypt blobs before retiring the old key.
 */
internal class ProvisionedPortableSourceSecretKeyRing(
    val localDeviceId: String,
    private val secureRandom: SecureRandom = SecureRandom(),
) : PortableSourceSecretKeyProvider {
    private data class KeyRecord(
        val key: PortableSourceSecretKey,
        val epoch: Long,
    )

    private data class MutablePeer(
        val identityFingerprint: String,
        var revoked: Boolean = false,
        val knownKeyIds: MutableSet<String> = linkedSetOf(),
    )

    private val keys = linkedMapOf<String, KeyRecord>()
    private val peers = linkedMapOf<String, MutablePeer>()
    private var currentKeyId: String? = null

    init {
        require(localDeviceId.isNotBlank())
    }

    override fun currentKey(): PortableSourceSecretKey =
        keys[requireNotNull(currentKeyId) { "No portable sync key has been provisioned" }]!!.key

    override fun keyForId(keyId: String): PortableSourceSecretKey? = keys[keyId]?.key

    fun currentEpoch(): Long? = currentKeyId?.let { keys[it]?.epoch }

    fun trustedPeer(deviceId: String): PairingPeerTrustState? = peers[deviceId]?.let { peer ->
        PairingPeerTrustState(
            deviceId = deviceId,
            identityFingerprint = peer.identityFingerprint,
            revoked = peer.revoked,
            knownKeyIds = peer.knownKeyIds.toSet(),
        )
    }

    fun bootstrapIfNeeded(): PortableSourceSecretKey {
        currentKeyId?.let { return requireNotNull(keys[it]).key }
        val record = generateKey(epoch = 1L)
        keys[record.key.keyId] = record
        currentKeyId = record.key.keyId
        return record.key
    }

    fun rotateCurrentKey(): SyncKeyRotationResult {
        val previous = currentKeyId
        val nextEpoch = ((previous?.let { keys[it]?.epoch } ?: 0L) + 1L)
        val record = generateKey(nextEpoch)
        keys[record.key.keyId] = record
        currentKeyId = record.key.keyId
        return SyncKeyRotationResult(previous, record.key.keyId, nextEpoch)
    }

    fun createProvisioningPackage(
        candidate: DevicePairingCandidate,
        verifiedShortCode: String,
    ): SyncKeyPackageCreationResult {
        if (candidate.localDeviceId != localDeviceId) {
            return SyncKeyPackageCreationResult.LocalIdentityMismatch
        }
        if (candidate.verification.shortCode != verifiedShortCode) {
            return SyncKeyPackageCreationResult.VerificationMismatch
        }
        val trust = trust(candidate.peer)
        when (trust) {
            PairingPeerTrustResult.Revoked -> return SyncKeyPackageCreationResult.PeerRevoked
            PairingPeerTrustResult.IdentityConflict -> return SyncKeyPackageCreationResult.PeerIdentityConflict
            PairingPeerTrustResult.Trusted,
            PairingPeerTrustResult.AlreadyTrusted,
            -> Unit
        }

        val current = bootstrapIfNeeded()
        val epoch = requireNotNull(currentEpoch())
        val rawKey = requireNotNull(current.secretKey.encoded) {
            "Pairing provisioning requires exportable 256-bit sync key material"
        }
        val sessionKeyBytes = candidate.sessionKey.copyBytes()
        val nonce = ByteArray(PROVISIONING_NONCE_BYTES).also(secureRandom::nextBytes)
        val aad = provisioningAad(
            providerDeviceId = localDeviceId,
            recipientDeviceId = candidate.peer.deviceId,
            transcriptHash = candidate.transcriptHashBase64Url,
            keyId = current.keyId,
            keyEpoch = epoch,
        )
        val ciphertext = try {
            encrypt(sessionKeyBytes, nonce, aad, rawKey)
        } finally {
            rawKey.fill(0)
            sessionKeyBytes.fill(0)
            aad.fill(0)
        }
        val value = try {
            SyncKeyProvisioningPackage(
                providerDeviceId = localDeviceId,
                recipientDeviceId = candidate.peer.deviceId,
                transcriptHashBase64Url = candidate.transcriptHashBase64Url,
                keyId = current.keyId,
                keyEpoch = epoch,
                nonceBase64Url = encode(nonce),
                ciphertextBase64Url = encode(ciphertext),
            )
        } finally {
            nonce.fill(0)
            ciphertext.fill(0)
        }
        peers.getValue(candidate.peer.deviceId).knownKeyIds += current.keyId
        return SyncKeyPackageCreationResult.Created(value, trust)
    }

    fun acceptProvisioningPackage(
        candidate: DevicePairingCandidate,
        verifiedShortCode: String,
        provision: SyncKeyProvisioningPackage,
    ): SyncKeyPackageAcceptanceResult {
        if (candidate.localDeviceId != localDeviceId || provision.recipientDeviceId != localDeviceId) {
            return SyncKeyPackageAcceptanceResult.LocalIdentityMismatch
        }
        if (candidate.verification.shortCode != verifiedShortCode) {
            return SyncKeyPackageAcceptanceResult.VerificationMismatch
        }
        if (provision.providerDeviceId != candidate.peer.deviceId) {
            return SyncKeyPackageAcceptanceResult.LocalIdentityMismatch
        }
        if (provision.transcriptHashBase64Url != candidate.transcriptHashBase64Url) {
            return SyncKeyPackageAcceptanceResult.TranscriptMismatch
        }
        val trust = trust(candidate.peer)
        when (trust) {
            PairingPeerTrustResult.Revoked -> return SyncKeyPackageAcceptanceResult.PeerRevoked
            PairingPeerTrustResult.IdentityConflict -> return SyncKeyPackageAcceptanceResult.PeerIdentityConflict
            PairingPeerTrustResult.Trusted,
            PairingPeerTrustResult.AlreadyTrusted,
            -> Unit
        }

        val highestEpoch = keys.values.maxOfOrNull(KeyRecord::epoch) ?: 0L
        if (provision.keyEpoch < highestEpoch) {
            return SyncKeyPackageAcceptanceResult.StaleEpoch
        }

        val nonce = runCatching { decode(provision.nonceBase64Url) }.getOrNull()
            ?: return SyncKeyPackageAcceptanceResult.InvalidPackage
        if (nonce.size != PROVISIONING_NONCE_BYTES) {
            nonce.fill(0)
            return SyncKeyPackageAcceptanceResult.InvalidPackage
        }
        val ciphertext = runCatching { decode(provision.ciphertextBase64Url) }.getOrNull()
            ?: run {
                nonce.fill(0)
                return SyncKeyPackageAcceptanceResult.InvalidPackage
            }
        val sessionKeyBytes = candidate.sessionKey.copyBytes()
        val aad = provisioningAad(
            providerDeviceId = provision.providerDeviceId,
            recipientDeviceId = provision.recipientDeviceId,
            transcriptHash = provision.transcriptHashBase64Url,
            keyId = provision.keyId,
            keyEpoch = provision.keyEpoch,
        )
        val rawKey = try {
            decrypt(sessionKeyBytes, nonce, aad, ciphertext)
        } catch (_: Exception) {
            return SyncKeyPackageAcceptanceResult.DecryptionFailure
        } finally {
            sessionKeyBytes.fill(0)
            nonce.fill(0)
            aad.fill(0)
            ciphertext.fill(0)
        }
        if (rawKey.size != SYNC_KEY_BYTES) {
            rawKey.fill(0)
            return SyncKeyPackageAcceptanceResult.InvalidPackage
        }
        val calculatedKeyId = syncKeyId(rawKey)
        if (calculatedKeyId != provision.keyId) {
            rawKey.fill(0)
            return SyncKeyPackageAcceptanceResult.KeyIdMismatch
        }

        val portableKey = try {
            PortableSourceSecretKey(
                keyId = provision.keyId,
                secretKey = SecretKeySpec(rawKey, "AES"),
            )
        } finally {
            rawKey.fill(0)
        }
        keys[portableKey.keyId] = KeyRecord(portableKey, provision.keyEpoch)
        currentKeyId = portableKey.keyId
        peers.getValue(candidate.peer.deviceId).knownKeyIds += portableKey.keyId
        return SyncKeyPackageAcceptanceResult.Accepted(portableKey.keyId, provision.keyEpoch, trust)
    }

    fun revokePeer(deviceId: String): PairingPeerRevocationResult {
        val peer = peers[deviceId] ?: return PairingPeerRevocationResult(false, false)
        peer.revoked = true
        val current = currentKeyId
        return PairingPeerRevocationResult(
            revoked = true,
            rotationRequired = current != null && current in peer.knownKeyIds,
        )
    }

    fun retireKey(keyId: String): Boolean {
        require(keyId != currentKeyId) { "Current sync key cannot be retired" }
        val removed = keys.remove(keyId) != null
        if (removed) peers.values.forEach { it.knownKeyIds.remove(keyId) }
        return removed
    }

    private fun trust(identity: PairingDeviceIdentity): PairingPeerTrustResult {
        val fingerprint = identity.fingerprint
        val existing = peers[identity.deviceId]
        if (existing == null) {
            peers[identity.deviceId] = MutablePeer(identityFingerprint = fingerprint)
            return PairingPeerTrustResult.Trusted
        }
        if (existing.identityFingerprint != fingerprint) return PairingPeerTrustResult.IdentityConflict
        if (existing.revoked) return PairingPeerTrustResult.Revoked
        return PairingPeerTrustResult.AlreadyTrusted
    }

    private fun generateKey(epoch: Long): KeyRecord {
        require(epoch > 0L)
        val bytes = ByteArray(SYNC_KEY_BYTES).also(secureRandom::nextBytes)
        return try {
            val keyId = syncKeyId(bytes)
            KeyRecord(
                key = PortableSourceSecretKey(keyId, SecretKeySpec(bytes, "AES")),
                epoch = epoch,
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun provisioningAad(
        providerDeviceId: String,
        recipientDeviceId: String,
        transcriptHash: String,
        keyId: String,
        keyEpoch: Long,
    ): ByteArray = canonicalBytes(
        "OwnPlaySyncKeyProvisioning",
        SYNC_KEY_PROVISIONING_VERSION.toString(),
        providerDeviceId,
        recipientDeviceId,
        transcriptHash,
        keyId,
        keyEpoch.toString(),
    )

    private fun encrypt(
        sessionKey: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = Cipher.getInstance(PROVISIONING_CIPHER).run {
        init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(sessionKey, "AES"),
            GCMParameterSpec(PROVISIONING_GCM_TAG_BITS, nonce),
        )
        updateAAD(aad)
        doFinal(plaintext)
    }

    private fun decrypt(
        sessionKey: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = Cipher.getInstance(PROVISIONING_CIPHER).run {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(sessionKey, "AES"),
            GCMParameterSpec(PROVISIONING_GCM_TAG_BITS, nonce),
        )
        updateAAD(aad)
        doFinal(ciphertext)
    }

    private fun syncKeyId(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return try {
            SYNC_KEY_ID_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest.copyOfRange(0, 16))
        } finally {
            digest.fill(0)
        }
    }

    private fun canonicalBytes(vararg fields: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            for (field in fields) {
                val encoded = field.toByteArray(StandardCharsets.UTF_8)
                try {
                    output.writeInt(encoded.size)
                    output.write(encoded)
                } finally {
                    encoded.fill(0)
                }
            }
        }
        bytes.toByteArray()
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
