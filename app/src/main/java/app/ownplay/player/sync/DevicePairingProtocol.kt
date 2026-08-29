package app.ownplay.player.sync

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val DEVICE_PAIRING_PROTOCOL_VERSION = 1
private const val PAIRING_CURVE = "secp256r1"
private const val PAIRING_SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val PAIRING_KEY_AGREEMENT_ALGORITHM = "ECDH"
private const val PAIRING_HKDF_ALGORITHM = "HmacSHA256"
private const val PAIRING_SESSION_KEY_BYTES = 32
private val PAIRING_HKDF_INFO = "OwnPlay device pairing session v1".toByteArray(StandardCharsets.UTF_8)

internal data class PairingDeviceIdentity(
    val deviceId: String,
    val identityPublicKeyBase64Url: String,
) {
    init {
        require(deviceId.isNotBlank())
        require(identityPublicKeyBase64Url.isNotBlank())
    }

    val fingerprint: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(identityPublicKeyBytes())
            return try {
                digest.copyOfRange(0, 12).joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
            } finally {
                digest.fill(0)
            }
        }

    internal fun publicKey(): PublicKey = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(identityPublicKeyBytes()),
    )

    private fun identityPublicKeyBytes(): ByteArray =
        Base64.getUrlDecoder().decode(identityPublicKeyBase64Url)
}

/** Long-lived device identity; production Android storage may supply a Keystore-backed key pair. */
internal class PairingIdentityKey internal constructor(
    val identity: PairingDeviceIdentity,
    private val keyPair: KeyPair,
) {
    internal fun sign(payload: ByteArray): ByteArray = Signature.getInstance(PAIRING_SIGNATURE_ALGORITHM).run {
        initSign(keyPair.private)
        update(payload)
        sign()
    }

    override fun toString(): String =
        "PairingIdentityKey(deviceId=${identity.deviceId}, identityFingerprint=${identity.fingerprint}, privateKey=<redacted>)"

    companion object {
        fun generate(
            deviceId: String,
            secureRandom: SecureRandom = SecureRandom(),
        ): PairingIdentityKey {
            require(deviceId.isNotBlank())
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec(PAIRING_CURVE), secureRandom)
            val pair = generator.generateKeyPair()
            return PairingIdentityKey(
                identity = PairingDeviceIdentity(
                    deviceId = deviceId,
                    identityPublicKeyBase64Url = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(pair.public.encoded),
                ),
                keyPair = pair,
            )
        }
    }
}

internal data class DevicePairingOffer(
    val version: Int = DEVICE_PAIRING_PROTOCOL_VERSION,
    val sessionId: String,
    val initiator: PairingDeviceIdentity,
    val initiatorEphemeralPublicKeyBase64Url: String,
    val initiatorNonceBase64Url: String,
    val initiatorSignatureBase64Url: String,
) {
    init {
        require(version == DEVICE_PAIRING_PROTOCOL_VERSION)
        require(sessionId.isNotBlank())
        require(initiatorEphemeralPublicKeyBase64Url.isNotBlank())
        require(initiatorNonceBase64Url.isNotBlank())
        require(initiatorSignatureBase64Url.isNotBlank())
    }
}

internal data class DevicePairingAnswer(
    val version: Int = DEVICE_PAIRING_PROTOCOL_VERSION,
    val sessionId: String,
    val responder: PairingDeviceIdentity,
    val responderEphemeralPublicKeyBase64Url: String,
    val responderNonceBase64Url: String,
    val responderSignatureBase64Url: String,
) {
    init {
        require(version == DEVICE_PAIRING_PROTOCOL_VERSION)
        require(sessionId.isNotBlank())
        require(responderEphemeralPublicKeyBase64Url.isNotBlank())
        require(responderNonceBase64Url.isNotBlank())
        require(responderSignatureBase64Url.isNotBlank())
    }
}

internal data class DevicePairingConfirmation(
    val version: Int = DEVICE_PAIRING_PROTOCOL_VERSION,
    val sessionId: String,
    val transcriptHashBase64Url: String,
    val initiatorSignatureBase64Url: String,
) {
    init {
        require(version == DEVICE_PAIRING_PROTOCOL_VERSION)
        require(sessionId.isNotBlank())
        require(transcriptHashBase64Url.isNotBlank())
        require(initiatorSignatureBase64Url.isNotBlank())
    }
}

internal class DevicePairingSessionKey internal constructor(
    private val keyBytes: ByteArray,
) {
    init {
        require(keyBytes.size == PAIRING_SESSION_KEY_BYTES)
    }

    internal fun copyBytes(): ByteArray = keyBytes.copyOf()

    override fun toString(): String = "DevicePairingSessionKey(<redacted>)"
}

internal data class DevicePairingVerification(
    val shortCode: String,
    val fingerprint: String,
) {
    init {
        require(shortCode.matches(Regex("\\d{8}")))
        require(fingerprint.isNotBlank())
    }
}

internal class DevicePairingCandidate internal constructor(
    val localDeviceId: String,
    val peer: PairingDeviceIdentity,
    val verification: DevicePairingVerification,
    val transcriptHashBase64Url: String,
    internal val sessionKey: DevicePairingSessionKey,
) {
    override fun toString(): String =
        "DevicePairingCandidate(localDeviceId=$localDeviceId, peerDeviceId=${peer.deviceId}, " +
            "verification=$verification, transcriptHash=<opaque>, sessionKey=<redacted>)"
}

internal data class DevicePairingInitiatorResult(
    val candidate: DevicePairingCandidate,
    val confirmation: DevicePairingConfirmation,
)

internal class DevicePairingInitiatorContext internal constructor(
    internal val identityKey: PairingIdentityKey,
    internal val ephemeralPrivateKey: PrivateKey,
    val offer: DevicePairingOffer,
) {
    override fun toString(): String =
        "DevicePairingInitiatorContext(deviceId=${identityKey.identity.deviceId}, sessionId=${offer.sessionId}, ephemeralPrivateKey=<redacted>)"
}

internal class DevicePairingResponderContext internal constructor(
    internal val identityKey: PairingIdentityKey,
    internal val ephemeralPrivateKey: PrivateKey,
    internal val offer: DevicePairingOffer,
    val answer: DevicePairingAnswer,
    internal val transcriptHash: ByteArray,
) {
    override fun toString(): String =
        "DevicePairingResponderContext(deviceId=${identityKey.identity.deviceId}, sessionId=${offer.sessionId}, ephemeralPrivateKey=<redacted>)"
}

/**
 * Mutual-authenticated local pairing protocol.
 *
 * Identity keys sign the handshake transcript. Ephemeral P-256 ECDH derives a session key through
 * HKDF-SHA256. The user-visible verification code/fingerprint binds both identities, both ephemeral
 * keys, both nonces and the session id. Transport is deliberately out of scope.
 */
internal object DevicePairingProtocol {
    fun createOffer(
        identityKey: PairingIdentityKey,
        secureRandom: SecureRandom = SecureRandom(),
    ): DevicePairingInitiatorContext {
        val ephemeral = ephemeralKeyPair(secureRandom)
        val sessionId = randomToken(16, secureRandom)
        val nonce = randomToken(24, secureRandom)
        val unsigned = unsignedOfferBytes(
            version = DEVICE_PAIRING_PROTOCOL_VERSION,
            sessionId = sessionId,
            initiator = identityKey.identity,
            ephemeralPublicKey = encodeKey(ephemeral.public),
            nonce = nonce,
        )
        val signature = identityKey.sign(unsigned)
        unsigned.fill(0)
        return DevicePairingInitiatorContext(
            identityKey = identityKey,
            ephemeralPrivateKey = ephemeral.private,
            offer = DevicePairingOffer(
                sessionId = sessionId,
                initiator = identityKey.identity,
                initiatorEphemeralPublicKeyBase64Url = encodeKey(ephemeral.public),
                initiatorNonceBase64Url = nonce,
                initiatorSignatureBase64Url = encode(signature),
            ),
        ).also { signature.fill(0) }
    }

    fun answerOffer(
        identityKey: PairingIdentityKey,
        offer: DevicePairingOffer,
        secureRandom: SecureRandom = SecureRandom(),
    ): DevicePairingResponderContext {
        verifyOffer(offer)
        require(identityKey.identity.deviceId != offer.initiator.deviceId) {
            "A device cannot pair with itself"
        }
        val ephemeral = ephemeralKeyPair(secureRandom)
        val nonce = randomToken(24, secureRandom)
        val unsignedAnswer = DevicePairingAnswer(
            sessionId = offer.sessionId,
            responder = identityKey.identity,
            responderEphemeralPublicKeyBase64Url = encodeKey(ephemeral.public),
            responderNonceBase64Url = nonce,
            responderSignatureBase64Url = "pending",
        )
        val transcript = transcriptBytes(offer, unsignedAnswer)
        val transcriptHash = MessageDigest.getInstance("SHA-256").digest(transcript)
        val signature = identityKey.sign(transcript)
        transcript.fill(0)
        return DevicePairingResponderContext(
            identityKey = identityKey,
            ephemeralPrivateKey = ephemeral.private,
            offer = offer,
            answer = unsignedAnswer.copy(responderSignatureBase64Url = encode(signature)),
            transcriptHash = transcriptHash,
        ).also { signature.fill(0) }
    }

    fun acceptAnswer(
        context: DevicePairingInitiatorContext,
        answer: DevicePairingAnswer,
    ): DevicePairingInitiatorResult {
        require(answer.sessionId == context.offer.sessionId) { "Pairing session id mismatch" }
        require(answer.responder.deviceId != context.offer.initiator.deviceId) {
            "A device cannot pair with itself"
        }
        val transcript = transcriptBytes(context.offer, answer)
        verifySignature(
            publicKey = answer.responder.publicKey(),
            payload = transcript,
            signatureBase64Url = answer.responderSignatureBase64Url,
            errorMessage = "Responder pairing signature is invalid",
        )
        val transcriptHash = MessageDigest.getInstance("SHA-256").digest(transcript)
        transcript.fill(0)
        val candidate = deriveCandidate(
            localDeviceId = context.identityKey.identity.deviceId,
            peer = answer.responder,
            privateKey = context.ephemeralPrivateKey,
            peerEphemeralPublicKeyBase64Url = answer.responderEphemeralPublicKeyBase64Url,
            transcriptHash = transcriptHash,
        )
        val confirmationPayload = confirmationPayload(context.offer.sessionId, transcriptHash)
        val confirmationSignature = context.identityKey.sign(confirmationPayload)
        confirmationPayload.fill(0)
        val confirmation = DevicePairingConfirmation(
            sessionId = context.offer.sessionId,
            transcriptHashBase64Url = encode(transcriptHash),
            initiatorSignatureBase64Url = encode(confirmationSignature),
        )
        confirmationSignature.fill(0)
        transcriptHash.fill(0)
        return DevicePairingInitiatorResult(candidate, confirmation)
    }

    fun acceptConfirmation(
        context: DevicePairingResponderContext,
        confirmation: DevicePairingConfirmation,
    ): DevicePairingCandidate {
        require(confirmation.sessionId == context.offer.sessionId) { "Pairing session id mismatch" }
        val suppliedHash = decode(confirmation.transcriptHashBase64Url)
        try {
            require(MessageDigest.isEqual(suppliedHash, context.transcriptHash)) {
                "Pairing transcript hash mismatch"
            }
        } finally {
            suppliedHash.fill(0)
        }
        val confirmationPayload = confirmationPayload(context.offer.sessionId, context.transcriptHash)
        try {
            verifySignature(
                publicKey = context.offer.initiator.publicKey(),
                payload = confirmationPayload,
                signatureBase64Url = confirmation.initiatorSignatureBase64Url,
                errorMessage = "Initiator pairing confirmation is invalid",
            )
        } finally {
            confirmationPayload.fill(0)
        }
        return deriveCandidate(
            localDeviceId = context.identityKey.identity.deviceId,
            peer = context.offer.initiator,
            privateKey = context.ephemeralPrivateKey,
            peerEphemeralPublicKeyBase64Url = context.offer.initiatorEphemeralPublicKeyBase64Url,
            transcriptHash = context.transcriptHash,
        )
    }

    private fun verifyOffer(offer: DevicePairingOffer) {
        val unsigned = unsignedOfferBytes(
            version = offer.version,
            sessionId = offer.sessionId,
            initiator = offer.initiator,
            ephemeralPublicKey = offer.initiatorEphemeralPublicKeyBase64Url,
            nonce = offer.initiatorNonceBase64Url,
        )
        try {
            verifySignature(
                publicKey = offer.initiator.publicKey(),
                payload = unsigned,
                signatureBase64Url = offer.initiatorSignatureBase64Url,
                errorMessage = "Initiator pairing signature is invalid",
            )
        } finally {
            unsigned.fill(0)
        }
    }

    private fun deriveCandidate(
        localDeviceId: String,
        peer: PairingDeviceIdentity,
        privateKey: PrivateKey,
        peerEphemeralPublicKeyBase64Url: String,
        transcriptHash: ByteArray,
    ): DevicePairingCandidate {
        val peerKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(decode(peerEphemeralPublicKeyBase64Url)),
        )
        val sharedSecret = KeyAgreement.getInstance(PAIRING_KEY_AGREEMENT_ALGORITHM).run {
            init(privateKey)
            doPhase(peerKey, true)
            generateSecret()
        }
        val derived = try {
            hkdfSha256(
                inputKeyMaterial = sharedSecret,
                salt = transcriptHash,
                info = PAIRING_HKDF_INFO,
                outputBytes = PAIRING_SESSION_KEY_BYTES,
            )
        } finally {
            sharedSecret.fill(0)
        }
        return DevicePairingCandidate(
            localDeviceId = localDeviceId,
            peer = peer,
            verification = verification(transcriptHash),
            transcriptHashBase64Url = encode(transcriptHash),
            sessionKey = DevicePairingSessionKey(derived),
        )
    }

    private fun verification(hash: ByteArray): DevicePairingVerification {
        val unsigned = ByteBuffer.wrap(hash, 0, 4).int.toLong() and 0xffff_ffffL
        val shortCode = (unsigned % 100_000_000L).toString().padStart(8, '0')
        val fingerprint = hash.copyOfRange(0, 8)
            .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }
            .chunked(4)
            .joinToString("-")
        return DevicePairingVerification(shortCode, fingerprint)
    }

    private fun transcriptBytes(offer: DevicePairingOffer, answer: DevicePairingAnswer): ByteArray =
        canonicalBytes(
            "OwnPlayPairingTranscript",
            offer.version.toString(),
            offer.sessionId,
            offer.initiator.deviceId,
            offer.initiator.identityPublicKeyBase64Url,
            offer.initiatorEphemeralPublicKeyBase64Url,
            offer.initiatorNonceBase64Url,
            offer.initiatorSignatureBase64Url,
            answer.responder.deviceId,
            answer.responder.identityPublicKeyBase64Url,
            answer.responderEphemeralPublicKeyBase64Url,
            answer.responderNonceBase64Url,
        )

    private fun unsignedOfferBytes(
        version: Int,
        sessionId: String,
        initiator: PairingDeviceIdentity,
        ephemeralPublicKey: String,
        nonce: String,
    ): ByteArray = canonicalBytes(
        "OwnPlayPairingOffer",
        version.toString(),
        sessionId,
        initiator.deviceId,
        initiator.identityPublicKeyBase64Url,
        ephemeralPublicKey,
        nonce,
    )

    private fun confirmationPayload(sessionId: String, transcriptHash: ByteArray): ByteArray =
        canonicalBytes(
            "OwnPlayPairingConfirmation",
            DEVICE_PAIRING_PROTOCOL_VERSION.toString(),
            sessionId,
            encode(transcriptHash),
        )

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

    private fun ephemeralKeyPair(secureRandom: SecureRandom): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(PAIRING_CURVE), secureRandom)
        return generator.generateKeyPair()
    }

    private fun verifySignature(
        publicKey: PublicKey,
        payload: ByteArray,
        signatureBase64Url: String,
        errorMessage: String,
    ) {
        val signatureBytes = decode(signatureBase64Url)
        val valid = try {
            Signature.getInstance(PAIRING_SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(payload)
                verify(signatureBytes)
            }
        } finally {
            signatureBytes.fill(0)
        }
        require(valid) { errorMessage }
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int,
    ): ByteArray {
        require(outputBytes in 1..32)
        val extract = Mac.getInstance(PAIRING_HKDF_ALGORITHM)
        extract.init(SecretKeySpec(salt, PAIRING_HKDF_ALGORITHM))
        val pseudoRandomKey = extract.doFinal(inputKeyMaterial)
        return try {
            val expand = Mac.getInstance(PAIRING_HKDF_ALGORITHM)
            expand.init(SecretKeySpec(pseudoRandomKey, PAIRING_HKDF_ALGORITHM))
            expand.update(info)
            expand.update(1.toByte())
            expand.doFinal().copyOf(outputBytes)
        } finally {
            pseudoRandomKey.fill(0)
        }
    }

    private fun randomToken(byteCount: Int, secureRandom: SecureRandom): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return try {
            encode(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun encodeKey(publicKey: PublicKey): String = encode(publicKey.encoded)
    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
