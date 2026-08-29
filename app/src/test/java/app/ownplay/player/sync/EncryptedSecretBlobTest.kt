package app.ownplay.player.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSecretBlobTest {
    @Test
    fun codecRoundTripPreservesEncryptedEnvelope() {
        val envelope = PortableEncryptedSourceSecret(
            keyId = "key-1",
            syncSourceId = "source-1",
            sourceKind = PORTABLE_SOURCE_KIND_XTREAM,
            nonceBase64Url = "bm9uY2U",
            ciphertextBase64Url = "Y2lwaGVydGV4dA",
        )

        val payload = EncryptedSecretBlobCodec.encode(envelope)
        val decoded = EncryptedSecretBlobCodec.decode(payload)

        assertEquals(envelope, decoded)
    }

    @Test
    fun contentReferenceDetectsPayloadTampering() {
        val payload = EncryptedSecretBlobCodec.encode(
            PortableEncryptedSourceSecret(
                keyId = "key-1",
                syncSourceId = "source-1",
                sourceKind = PORTABLE_SOURCE_KIND_REMOTE_M3U,
                nonceBase64Url = "bm9uY2U",
                ciphertextBase64Url = "Y2lwaGVydGV4dA",
            ),
        )
        val reference = EncryptedSecretBlobReference.fromPayload(payload)
        val tampered = payload.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        assertTrue(reference.verifies(payload))
        assertFalse(reference.verifies(tampered))
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedSecretBlob.restore(reference, tampered, createdAtEpochMillis = 1L)
        }
    }

    @Test
    fun referenceContainsDigestNotStorageLocation() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val reference = EncryptedSecretBlobReference.fromPayload(payload)

        assertTrue(reference.value.startsWith("ownplay-secret:v1:sha256:"))
        assertFalse(reference.value.contains("http"))
        assertFalse(reference.value.contains("/data/"))
    }
}
