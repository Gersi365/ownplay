package app.ownplay.player.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal const val ENCRYPTED_SECRET_BLOB_VERSION = 1
private const val ENCRYPTED_SECRET_BLOB_MAGIC = 0x4F505342 // OPSB
private const val ENCRYPTED_SECRET_BLOB_REF_PREFIX = "ownplay-secret:v1:sha256:"
private const val MAX_BLOB_FIELD_BYTES = 512 * 1024

/**
 * Versioned content-addressed reference to an encrypted source-secret blob.
 *
 * The value is safe to synchronize: it contains only a SHA-256 digest of ciphertext bytes, never
 * a storage URL, local file path, Android keystore reference, locator, username, or password.
 */
internal data class EncryptedSecretBlobReference(val value: String) {
    init {
        require(value.startsWith(ENCRYPTED_SECRET_BLOB_REF_PREFIX))
        val encodedDigest = value.removePrefix(ENCRYPTED_SECRET_BLOB_REF_PREFIX)
        val digest = try {
            Base64.getUrlDecoder().decode(encodedDigest)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Encrypted secret blob reference is not Base64URL", error)
        }
        try {
            require(digest.size == 32) { "Encrypted secret blob reference must contain SHA-256" }
        } finally {
            digest.fill(0)
        }
    }

    fun verifies(payload: ByteArray): Boolean {
        val expected = Base64.getUrlDecoder().decode(value.removePrefix(ENCRYPTED_SECRET_BLOB_REF_PREFIX))
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        return try {
            MessageDigest.isEqual(expected, actual)
        } finally {
            expected.fill(0)
            actual.fill(0)
        }
    }

    companion object {
        fun fromPayload(payload: ByteArray): EncryptedSecretBlobReference {
            val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            return try {
                EncryptedSecretBlobReference(
                    ENCRYPTED_SECRET_BLOB_REF_PREFIX +
                        Base64.getUrlEncoder().withoutPadding().encodeToString(digest),
                )
            } finally {
                digest.fill(0)
            }
        }
    }
}

/** Deterministic binary representation stored behind [EncryptedSecretBlobReference]. */
internal object EncryptedSecretBlobCodec {
    fun encode(envelope: PortableEncryptedSourceSecret): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(ENCRYPTED_SECRET_BLOB_MAGIC)
                output.writeInt(ENCRYPTED_SECRET_BLOB_VERSION)
                output.writeInt(envelope.version)
                output.writeBlobString(envelope.algorithm)
                output.writeBlobString(envelope.keyId)
                output.writeBlobString(envelope.syncSourceId)
                output.writeBlobString(envelope.sourceKind)
                output.writeBlobString(envelope.nonceBase64Url)
                output.writeBlobString(envelope.ciphertextBase64Url)
            }
            bytes.toByteArray()
        }

    fun decode(payload: ByteArray): PortableEncryptedSourceSecret =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == ENCRYPTED_SECRET_BLOB_MAGIC) {
                "Encrypted secret blob magic is invalid"
            }
            require(input.readInt() == ENCRYPTED_SECRET_BLOB_VERSION) {
                "Unsupported encrypted secret blob version"
            }
            val envelope = PortableEncryptedSourceSecret(
                version = input.readInt(),
                algorithm = input.readBlobString(),
                keyId = input.readBlobString(),
                syncSourceId = input.readBlobString(),
                sourceKind = input.readBlobString(),
                nonceBase64Url = input.readBlobString(),
                ciphertextBase64Url = input.readBlobString(),
            )
            require(input.available() == 0) { "Encrypted secret blob has trailing data" }
            envelope
        }

    private fun DataInputStream.readBlobString(): String {
        val length = readInt()
        require(length in 0..MAX_BLOB_FIELD_BYTES) { "Encrypted secret blob field length is invalid" }
        val bytes = ByteArray(length).also(::readFully)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeBlobString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(bytes.size <= MAX_BLOB_FIELD_BYTES) { "Encrypted secret blob field is too large" }
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}

/** Ciphertext blob plus immutable content reference and creation time used for retention. */
internal class EncryptedSecretBlob private constructor(
    val reference: EncryptedSecretBlobReference,
    private val payload: ByteArray,
    val createdAtEpochMillis: Long,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(reference.verifies(payload)) { "Encrypted secret blob content does not match reference" }
    }

    val sizeBytes: Int
        get() = payload.size

    fun payloadCopy(): ByteArray = payload.copyOf()

    override fun toString(): String =
        "EncryptedSecretBlob(reference=${reference.value}, payload=<redacted>, " +
            "createdAtEpochMillis=$createdAtEpochMillis, sizeBytes=$sizeBytes)"

    companion object {
        fun create(payload: ByteArray, createdAtEpochMillis: Long): EncryptedSecretBlob =
            EncryptedSecretBlob(
                reference = EncryptedSecretBlobReference.fromPayload(payload),
                payload = payload.copyOf(),
                createdAtEpochMillis = createdAtEpochMillis,
            )

        fun restore(
            reference: EncryptedSecretBlobReference,
            payload: ByteArray,
            createdAtEpochMillis: Long,
        ): EncryptedSecretBlob = EncryptedSecretBlob(
            reference = reference,
            payload = payload.copyOf(),
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }
}

internal data class EncryptedSecretBlobMetadata(
    val reference: EncryptedSecretBlobReference,
    val createdAtEpochMillis: Long,
    val sizeBytes: Long,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(sizeBytes >= 0L)
    }
}

internal data class EncryptedSecretBlobPutResult(
    val metadata: EncryptedSecretBlobMetadata,
    /** true only when this call created a previously absent content-addressed object. */
    val created: Boolean,
)

/**
 * Backend-neutral encrypted blob store.
 *
 * Implementations may use cloud object storage, LAN relay, or another transport, but the caller
 * supplies and verifies the content address. Storage URLs and provider-specific object ids must not
 * leak into SyncSourceState.encryptedSecretRef.
 */
internal interface EncryptedSecretBlobStore {
    suspend fun put(blob: EncryptedSecretBlob): EncryptedSecretBlobPutResult

    suspend fun get(reference: EncryptedSecretBlobReference): EncryptedSecretBlob?

    suspend fun list(): List<EncryptedSecretBlobMetadata>

    suspend fun delete(reference: EncryptedSecretBlobReference): Boolean
}
