package app.ownplay.player.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val IDENTITY_ALIAS_PREFIX = "ownplay.sync.identity.v1."
private const val STATE_ALIAS_PREFIX = "ownplay.sync.state.v1."
private const val PAIRING_STATE_PREFERENCES = "ownplay_secure_pairing_state"
private const val PAIRING_STATE_STORAGE_VERSION = "v1"
private const val PAIRING_STATE_TRANSFORMATION = "AES/GCM/NoPadding"
private const val PAIRING_STATE_GCM_TAG_BITS = 128
private const val PAIRING_IDENTITY_CURVE = "secp256r1"

/** Long-lived P-256 identity whose private key is generated and retained by Android Keystore. */
internal class AndroidKeystorePairingIdentityStore {
    fun getOrCreate(deviceId: String): PairingIdentityKey = synchronized(IDENTITY_LOCK) {
        require(deviceId.isNotBlank())
        val alias = deviceScopedAlias(IDENTITY_ALIAS_PREFIX, deviceId)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        val pair = if (existing != null) {
            KeyPair(existing.certificate.publicKey, existing.privateKey)
        } else {
            generateIdentity(alias)
        }
        PairingIdentityKey(
            identity = PairingDeviceIdentity(
                deviceId = deviceId,
                identityPublicKeyBase64Url = BASE64_ENCODER.encodeToString(pair.public.encoded),
            ),
            keyPair = pair,
        )
    }

    private fun generateIdentity(alias: String): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE_PROVIDER,
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(PAIRING_IDENTITY_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKeyPair()
    }

    private companion object {
        val IDENTITY_LOCK = Any()
        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

/**
 * Stores trusted peers and exportable OwnPlay group keys as one authenticated encrypted payload.
 * The wrapping key itself never leaves Android Keystore.
 */
internal class AndroidKeystorePairingStateStore(
    context: Context,
    private val localDeviceId: String,
) : ProvisionedSyncKeyRingStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PAIRING_STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val alias = deviceScopedAlias(STATE_ALIAS_PREFIX, localDeviceId)
    private val preferenceKey = "state.${deviceScopeId(localDeviceId)}"
    private val aad = "OwnPlay:DevicePairingState:v1:$localDeviceId".toByteArray(StandardCharsets.UTF_8)

    init {
        require(localDeviceId.isNotBlank())
    }

    override fun load(): ProvisionedSyncKeyRingSnapshot? = synchronized(STATE_LOCK) {
        val encoded = preferences.getString(preferenceKey, null) ?: return@synchronized null
        val parts = encoded.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == PAIRING_STATE_STORAGE_VERSION) {
            "Unsupported pairing state payload format"
        }
        val iv = BASE64_DECODER.decode(parts[1])
        val ciphertext = BASE64_DECODER.decode(parts[2])
        val plaintext = try {
            decrypt(getOrCreateWrappingKey(), iv, ciphertext)
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
        return@synchronized try {
            ProvisionedSyncKeyRingStateCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun save(snapshot: ProvisionedSyncKeyRingSnapshot) = synchronized(STATE_LOCK) {
        val plaintext = ProvisionedSyncKeyRingStateCodec.encode(snapshot)
        val encrypted = try {
            encrypt(getOrCreateWrappingKey(), plaintext)
        } finally {
            plaintext.fill(0)
        }
        val encoded = try {
            buildString {
                append(PAIRING_STATE_STORAGE_VERSION)
                append(':')
                append(BASE64_ENCODER.encodeToString(encrypted.iv))
                append(':')
                append(BASE64_ENCODER.encodeToString(encrypted.ciphertext))
            }
        } finally {
            encrypted.iv.fill(0)
            encrypted.ciphertext.fill(0)
        }
        check(preferences.edit().putString(preferenceKey, encoded).commit()) {
            "Unable to persist encrypted pairing state"
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(key: SecretKey, plaintext: ByteArray): ProtectedPairingState {
        val cipher = Cipher.getInstance(PAIRING_STATE_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        return ProtectedPairingState(
            iv = cipher.iv.copyOf(),
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    private fun decrypt(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(PAIRING_STATE_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(PAIRING_STATE_GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private data class ProtectedPairingState(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private companion object {
        val STATE_LOCK = Any()
        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val BASE64_DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}

/** Single-runtime owner for persisted pairing identity and key-ring state. */
internal class AndroidSecureDevicePairingState(
    context: Context,
    val localDeviceId: String,
) {
    private val identityStore = AndroidKeystorePairingIdentityStore()
    private val stateStore = AndroidKeystorePairingStateStore(context, localDeviceId)

    val identityKey: PairingIdentityKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        identityStore.getOrCreate(localDeviceId)
    }

    val keyRing: ProvisionedPortableSourceSecretKeyRing by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProvisionedPortableSourceSecretKeyRing(
            localDeviceId = localDeviceId,
            stateStore = stateStore,
        )
    }
}

private fun deviceScopedAlias(prefix: String, deviceId: String): String = prefix + deviceScopeId(deviceId)

private fun deviceScopeId(deviceId: String): String {
    require(deviceId.isNotBlank())
    val input = deviceId.toByteArray(StandardCharsets.UTF_8)
    val digest = try {
        MessageDigest.getInstance("SHA-256").digest(input)
    } finally {
        input.fill(0)
    }
    return try {
        Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOfRange(0, 16))
    } finally {
        digest.fill(0)
    }
}
