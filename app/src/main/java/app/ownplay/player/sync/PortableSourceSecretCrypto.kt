package app.ownplay.player.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal object PortableSourceSecretCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val EXPECTED_NONCE_BYTES = 12
    private const val MAX_CIPHERTEXT_BYTES = 256 * 1024
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encrypt(
        syncSourceId: String,
        secret: PortableSourceSecret,
        keyProvider: PortableSourceSecretKeyProvider,
    ): PortableEncryptedSourceSecret {
        require(syncSourceId.isNotBlank())
        val key = keyProvider.currentKey()
        val plaintext = PortableSourceSecretCodec.encode(secret)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key.secretKey)
            cipher.updateAAD(aad(syncSourceId, secret.sourceKind, key.keyId))
            val ciphertext = cipher.doFinal(plaintext)
            try {
                require(cipher.iv.size == EXPECTED_NONCE_BYTES) {
                    "Portable source secret requires a 96-bit GCM nonce"
                }
                PortableEncryptedSourceSecret(
                    keyId = key.keyId,
                    syncSourceId = syncSourceId,
                    sourceKind = secret.sourceKind,
                    nonceBase64Url = encoder.encodeToString(cipher.iv),
                    ciphertextBase64Url = encoder.encodeToString(ciphertext),
                )
            } finally {
                ciphertext.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(
        envelope: PortableEncryptedSourceSecret,
        keyProvider: PortableSourceSecretKeyProvider,
    ): PortableSourceSecret {
        val key = keyProvider.keyForId(envelope.keyId)
            ?: throw PortableSourceSecretKeyUnavailableException(envelope.keyId)
        val nonce = decodeBounded(envelope.nonceBase64Url, EXPECTED_NONCE_BYTES, EXPECTED_NONCE_BYTES)
        val ciphertext = decodeBounded(envelope.ciphertextBase64Url, 16, MAX_CIPHERTEXT_BYTES)
        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key.secretKey,
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(aad(envelope.syncSourceId, envelope.sourceKind, envelope.keyId))
            try {
                cipher.doFinal(ciphertext)
            } catch (error: AEADBadTagException) {
                throw PortableSourceSecretAuthenticationException(error)
            }
        } finally {
            nonce.fill(0)
            ciphertext.fill(0)
        }
        return try {
            PortableSourceSecretCodec.decode(plaintext).also { secret ->
                require(secret.sourceKind == envelope.sourceKind) {
                    "Portable source secret kind does not match encrypted envelope"
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun aad(syncSourceId: String, sourceKind: String, keyId: String): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PORTABLE_SOURCE_SECRET_VERSION)
                output.writeBoundedString("OwnPlay:PortableSourceSecret")
                output.writeBoundedString(syncSourceId)
                output.writeBoundedString(sourceKind)
                output.writeBoundedString(keyId)
            }
            bytes.toByteArray()
        }

    private fun decodeBounded(value: String, minBytes: Int, maxBytes: Int): ByteArray {
        val decoded = try {
            decoder.decode(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid portable source secret Base64URL payload", error)
        }
        require(decoded.size in minBytes..maxBytes) { "Portable source secret payload size is invalid" }
        return decoded
    }
}

internal object PortableSourceSecretCodec {
    private const val TYPE_XTREAM = 1
    private const val TYPE_REMOTE_M3U = 2
    private const val MAX_FIELD_BYTES = 64 * 1024

    fun encode(secret: PortableSourceSecret): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PORTABLE_SOURCE_SECRET_VERSION)
                when (secret) {
                    is PortableSourceSecret.Xtream -> {
                        output.writeInt(TYPE_XTREAM)
                        output.writeBoundedString(secret.serverUrl)
                        output.writeBoundedString(secret.username)
                        output.writeBoundedString(secret.password)
                        output.writeBoolean(secret.allowCleartext)
                    }

                    is PortableSourceSecret.RemoteM3u -> {
                        output.writeInt(TYPE_REMOTE_M3U)
                        output.writeBoundedString(secret.playlistUrl)
                        output.writeNullableBoundedString(secret.epgUrl)
                    }
                }
            }
            bytes.toByteArray()
        }

    fun decode(payload: ByteArray): PortableSourceSecret =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == PORTABLE_SOURCE_SECRET_VERSION) {
                "Unsupported portable source secret payload version"
            }
            val secret = when (input.readInt()) {
                TYPE_XTREAM -> PortableSourceSecret.Xtream(
                    serverUrl = input.readBoundedString(),
                    username = input.readBoundedString(),
                    password = input.readBoundedString(),
                    allowCleartext = input.readBoolean(),
                )

                TYPE_REMOTE_M3U -> PortableSourceSecret.RemoteM3u(
                    playlistUrl = input.readBoundedString(),
                    epgUrl = input.readNullableBoundedString(),
                )

                else -> error("Unsupported portable source secret type")
            }
            require(input.available() == 0) { "Portable source secret payload has trailing data" }
            secret
        }

    private fun DataInputStream.readBoundedString(): String {
        val length = readInt()
        require(length in 0..MAX_FIELD_BYTES) { "Portable source secret field length is invalid" }
        val bytes = ByteArray(length).also(::readFully)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readNullableBoundedString(): String? =
        when (val marker = readByte().toInt()) {
            0 -> null
            1 -> readBoundedString()
            else -> error("Portable source secret nullable field marker is invalid: $marker")
        }

    internal fun DataOutputStream.writeBoundedString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(bytes.size <= MAX_FIELD_BYTES) { "Portable source secret field is too large" }
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeNullableBoundedString(value: String?) {
        if (value == null) {
            writeByte(0)
        } else {
            writeByte(1)
            writeBoundedString(value)
        }
    }
}

private fun DataOutputStream.writeBoundedString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    try {
        require(bytes.size <= 64 * 1024) { "Portable source secret AAD field is too large" }
        writeInt(bytes.size)
        write(bytes)
    } finally {
        bytes.fill(0)
    }
}

internal class PortableSourceSecretKeyUnavailableException(
    keyId: String,
) : GeneralSecurityException("Portable source secret key is unavailable: $keyId")

internal class PortableSourceSecretAuthenticationException(
    cause: Throwable,
) : GeneralSecurityException("Portable source secret authentication failed", cause)
