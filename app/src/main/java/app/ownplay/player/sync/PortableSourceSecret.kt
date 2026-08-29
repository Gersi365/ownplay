package app.ownplay.player.sync

import javax.crypto.SecretKey

internal const val PORTABLE_SOURCE_SECRET_VERSION = 1
internal const val PORTABLE_SOURCE_SECRET_ALGORITHM = "AES-256-GCM"
internal const val PORTABLE_SOURCE_KIND_XTREAM = "xtream"
internal const val PORTABLE_SOURCE_KIND_REMOTE_M3U = "remote_m3u"

/**
 * Portable locator/credential material that may be synchronized only after encryption.
 *
 * Local M3U document URIs are intentionally excluded because they are device-local capabilities,
 * not portable locators.
 */
internal sealed interface PortableSourceSecret {
    val sourceKind: String

    data class Xtream(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : PortableSourceSecret {
        init {
            require(serverUrl.isNotBlank())
            require(username.isNotBlank())
        }

        override val sourceKind: String = PORTABLE_SOURCE_KIND_XTREAM

        override fun toString(): String =
            "PortableSourceSecret.Xtream(serverUrl=<redacted>, username=<redacted>, password=<redacted>)"
    }

    data class RemoteM3u(
        val playlistUrl: String,
        val epgUrl: String? = null,
    ) : PortableSourceSecret {
        init {
            require(playlistUrl.isNotBlank())
            epgUrl?.let { require(it.isNotBlank()) }
        }

        override val sourceKind: String = PORTABLE_SOURCE_KIND_REMOTE_M3U

        override fun toString(): String =
            "PortableSourceSecret.RemoteM3u(playlistUrl=<redacted>, epgUrl=${if (epgUrl == null) "null" else "<redacted>"})"
    }
}

/**
 * Ciphertext object suitable for a future blob store/transport. No locator or credential plaintext
 * is present in this object. The source identity is retained only so authenticated decryption can
 * bind the ciphertext to the source it belongs to.
 */
internal data class PortableEncryptedSourceSecret(
    val version: Int = PORTABLE_SOURCE_SECRET_VERSION,
    val algorithm: String = PORTABLE_SOURCE_SECRET_ALGORITHM,
    val keyId: String,
    val syncSourceId: String,
    val sourceKind: String,
    val nonceBase64Url: String,
    val ciphertextBase64Url: String,
) {
    init {
        require(version == PORTABLE_SOURCE_SECRET_VERSION)
        require(algorithm == PORTABLE_SOURCE_SECRET_ALGORITHM)
        require(keyId.isNotBlank())
        require(syncSourceId.isNotBlank())
        require(sourceKind in setOf(PORTABLE_SOURCE_KIND_XTREAM, PORTABLE_SOURCE_KIND_REMOTE_M3U))
        require(nonceBase64Url.isNotBlank())
        require(ciphertextBase64Url.isNotBlank())
    }

    override fun toString(): String =
        "PortableEncryptedSourceSecret(version=$version, algorithm=$algorithm, keyId=<opaque>, " +
            "syncSourceId=$syncSourceId, sourceKind=$sourceKind, nonce=<redacted>, ciphertext=<redacted>)"
}

/**
 * A portable sync key is deliberately supplied by an external provider. This layer does not define
 * how the key is created, paired, authenticated, backed up, or shared between devices.
 */
internal class PortableSourceSecretKey(
    val keyId: String,
    internal val secretKey: SecretKey,
) {
    init {
        require(keyId.isNotBlank())
        require(secretKey.algorithm.equals("AES", ignoreCase = true))
    }

    override fun toString(): String = "PortableSourceSecretKey(keyId=<opaque>, secretKey=<redacted>)"
}

internal interface PortableSourceSecretKeyProvider {
    fun currentKey(): PortableSourceSecretKey
    fun keyForId(keyId: String): PortableSourceSecretKey?
}
