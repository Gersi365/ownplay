package app.ownplay.player.sync

import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSourceSecretCryptoTest {
    @Test
    fun xtreamRoundTripPreservesSecretWithoutLeakingToEnvelopeString() {
        val provider = TestKeyProvider(current = key("key-1"))
        val secret = PortableSourceSecret.Xtream(
            serverUrl = "https://iptv.example.test:8443",
            username = "alice@example.test",
            password = "correct horse battery staple",
        )

        val envelope = PortableSourceSecretCrypto.encrypt(
            syncSourceId = "sync-source-1",
            secret = secret,
            keyProvider = provider,
        )
        val decrypted = PortableSourceSecretCrypto.decrypt(envelope, provider)

        assertEquals(secret, decrypted)
        assertEquals(PORTABLE_SOURCE_KIND_XTREAM, envelope.sourceKind)
        assertFalse(envelope.toString().contains(secret.serverUrl))
        assertFalse(envelope.toString().contains(secret.username))
        assertFalse(envelope.toString().contains(secret.password))
        assertFalse(secret.toString().contains(secret.serverUrl))
        assertFalse(secret.toString().contains(secret.username))
        assertFalse(secret.toString().contains(secret.password))
    }

    @Test
    fun remoteM3uRoundTripPreservesPlaylistAndEpgUrls() {
        val provider = TestKeyProvider(current = key("key-1"))
        val secret = PortableSourceSecret.RemoteM3u(
            playlistUrl = "https://example.test/list.m3u?token=playlist-secret",
            epgUrl = "https://example.test/guide.xml?token=epg-secret",
        )

        val envelope = PortableSourceSecretCrypto.encrypt("source-m3u", secret, provider)

        assertEquals(secret, PortableSourceSecretCrypto.decrypt(envelope, provider))
        assertEquals(PORTABLE_SOURCE_KIND_REMOTE_M3U, envelope.sourceKind)
    }

    @Test
    fun ciphertextTamperingFailsAuthentication() {
        val provider = TestKeyProvider(current = key("key-1"))
        val envelope = PortableSourceSecretCrypto.encrypt(
            "source-1",
            PortableSourceSecret.Xtream("https://example.test", "user", "password"),
            provider,
        )
        val bytes = Base64.getUrlDecoder().decode(envelope.ciphertextBase64Url)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        val tampered = envelope.copy(
            ciphertextBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
        )

        assertThrows(PortableSourceSecretAuthenticationException::class.java) {
            PortableSourceSecretCrypto.decrypt(tampered, provider)
        }
    }

    @Test
    fun ciphertextCannotBeTransplantedToAnotherSourceIdentity() {
        val provider = TestKeyProvider(current = key("key-1"))
        val envelope = PortableSourceSecretCrypto.encrypt(
            "source-1",
            PortableSourceSecret.Xtream("https://example.test", "user", "password"),
            provider,
        )

        assertThrows(PortableSourceSecretAuthenticationException::class.java) {
            PortableSourceSecretCrypto.decrypt(
                envelope.copy(syncSourceId = "source-2"),
                provider,
            )
        }
    }

    @Test
    fun ciphertextCannotBeReclassifiedAsAnotherSourceKind() {
        val provider = TestKeyProvider(current = key("key-1"))
        val envelope = PortableSourceSecretCrypto.encrypt(
            "source-1",
            PortableSourceSecret.Xtream("https://example.test", "user", "password"),
            provider,
        )

        assertThrows(PortableSourceSecretAuthenticationException::class.java) {
            PortableSourceSecretCrypto.decrypt(
                envelope.copy(sourceKind = PORTABLE_SOURCE_KIND_REMOTE_M3U),
                provider,
            )
        }
    }

    @Test
    fun unavailableKeyFailsClosed() {
        val writer = TestKeyProvider(current = key("old-key"))
        val envelope = PortableSourceSecretCrypto.encrypt(
            "source-1",
            PortableSourceSecret.Xtream("https://example.test", "user", "password"),
            writer,
        )
        val reader = TestKeyProvider(current = key("new-key"))

        assertThrows(PortableSourceSecretKeyUnavailableException::class.java) {
            PortableSourceSecretCrypto.decrypt(envelope, reader)
        }
    }

    @Test
    fun keyRotationCanDecryptOldEnvelopeAndEncryptWithNewKey() {
        val old = key("old-key")
        val new = key("new-key")
        val writer = TestKeyProvider(current = old)
        val oldEnvelope = PortableSourceSecretCrypto.encrypt(
            "source-1",
            PortableSourceSecret.RemoteM3u("https://example.test/list.m3u"),
            writer,
        )
        val rotated = TestKeyProvider(current = new, additional = listOf(old))

        val oldSecret = PortableSourceSecretCrypto.decrypt(oldEnvelope, rotated)
        val newEnvelope = PortableSourceSecretCrypto.encrypt("source-1", oldSecret, rotated)

        assertEquals("old-key", oldEnvelope.keyId)
        assertEquals("new-key", newEnvelope.keyId)
        assertEquals(oldSecret, PortableSourceSecretCrypto.decrypt(newEnvelope, rotated))
    }

    @Test
    fun randomizedGcmNonceProducesDifferentCiphertextForSameSecret() {
        val provider = TestKeyProvider(current = key("key-1"))
        val secret = PortableSourceSecret.Xtream("https://example.test", "user", "password")

        val first = PortableSourceSecretCrypto.encrypt("source-1", secret, provider)
        val second = PortableSourceSecretCrypto.encrypt("source-1", secret, provider)

        assertTrue(first.nonceBase64Url != second.nonceBase64Url)
        assertTrue(first.ciphertextBase64Url != second.ciphertextBase64Url)
    }

    private class TestKeyProvider(
        current: PortableSourceSecretKey,
        additional: List<PortableSourceSecretKey> = emptyList(),
    ) : PortableSourceSecretKeyProvider {
        private val keys = (additional + current).associateBy(PortableSourceSecretKey::keyId)
        private val currentKey = current

        override fun currentKey(): PortableSourceSecretKey = currentKey

        override fun keyForId(keyId: String): PortableSourceSecretKey? = keys[keyId]
    }

    private fun key(keyId: String): PortableSourceSecretKey = PortableSourceSecretKey(
        keyId = keyId,
        secretKey = generateAes256Key(),
    )

    private fun generateAes256Key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
