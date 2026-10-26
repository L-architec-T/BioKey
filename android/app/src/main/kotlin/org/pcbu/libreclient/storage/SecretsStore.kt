package org.pcbu.libreclient.storage

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Per-device secrets (encKey, passwordKey) wrapped with an Android Keystore
 * AES key before landing in SharedPreferences. Room only ever sees non-secret
 * metadata ([org.pcbu.libreclient.storage.db.PairedDeviceEntity]).
 *
 * Default matches pcbu-desktop's own default ("Biometric encryption" toggle
 * OFF): the AES wrapping key has no `setUserAuthenticationRequired`, so
 * unlocking doesn't need a *second* biometric prompt beyond the one already
 * shown for the actual unlock decision. With the toggle ON (Réglages) the passwordKey is instead wrapped with an RSA key that
 * only decrypts through a strong-biometric [BiometricPrompt] CryptoObject —
 * see [preparePasswordKeyDecryption].
 *
 * Storage format: AES blobs stay exactly `IV || ciphertext` as written by
 * BioKey ≤ 0.1.6, so already-paired PCs keep working; RSA blobs are told apart
 * by the [RSA_PREFIX] on the Base64 string (a leading byte would be ambiguous
 * with the random IV).
 */
class SecretsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Cipher to hand to BiometricPrompt as a CryptoObject; [finish] only works once it's authenticated. */
    class BiometricDecryption internal constructor(val cipher: Cipher, private val ciphertext: ByteArray) {
        fun finish(authenticatedCipher: Cipher = cipher): String =
            String(authenticatedCipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun storeEncKey(deviceId: String, encKey: String) = store(encKeyPrefKey(deviceId), encKey)
    fun readEncKey(deviceId: String): String? = read(encKeyPrefKey(deviceId))

    fun storePasswordKey(deviceId: String, passwordKey: String, useBiometricGate: Boolean = false) =
        if (useBiometricGate) storeBiometric(passwordKeyPrefKey(deviceId), passwordKey)
        else store(passwordKeyPrefKey(deviceId), passwordKey)

    /** Null for a biometric-gated passwordKey: that one only comes out of [preparePasswordKeyDecryption]. */
    fun readPasswordKey(deviceId: String): String? = read(passwordKeyPrefKey(deviceId))

    fun isPasswordKeyBiometricGated(deviceId: String): Boolean =
        prefs.getString(passwordKeyPrefKey(deviceId), null)?.startsWith(RSA_PREFIX) == true

    /**
     * Null if the passwordKey isn't biometric-gated, or if the RSA key is gone or was
     * invalidated by a new fingerprint enrollment (the PC then has to be paired again).
     */
    fun preparePasswordKeyDecryption(deviceId: String): BiometricDecryption? {
        val encoded = prefs.getString(passwordKeyPrefKey(deviceId), null) ?: return null
        if (!encoded.startsWith(RSA_PREFIX)) return null
        val ciphertext = Base64.decode(encoded.removePrefix(RSA_PREFIX), Base64.NO_WRAP)
        return runCatching {
            val privateKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? PrivateKey ?: return null
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams())
            BiometricDecryption(cipher, ciphertext)
        }.onFailure { Log.w(TAG, "biometric passwordKey unusable for $deviceId", it) }.getOrNull()
    }

    /** Réglages "Chiffrement biométrique" turned on: moves a plain passwordKey under the
     * biometric RSA key right away (encrypting needs no fingerprint). No-op if already gated. */
    fun gatePasswordKeyWithBiometric(deviceId: String) {
        val plain = readPasswordKey(deviceId) ?: return
        storeBiometric(passwordKeyPrefKey(deviceId), plain)
    }

    fun deleteDevice(deviceId: String) {
        prefs.edit()
            .remove(encKeyPrefKey(deviceId))
            .remove(passwordKeyPrefKey(deviceId))
            .apply()
    }

    private fun store(prefKey: String, plaintext: String) {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob = iv + ciphertext
        prefs.edit().putString(prefKey, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private fun storeBiometric(prefKey: String, plaintext: String) {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricPublicKey(), oaepParams())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(prefKey, RSA_PREFIX + Base64.encodeToString(ciphertext, Base64.NO_WRAP)).apply()
    }

    private fun read(prefKey: String): String? {
        val encoded = prefs.getString(prefKey, null) ?: return null
        if (encoded.startsWith(RSA_PREFIX)) return null
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = blob.copyOfRange(GCM_IV_SIZE, blob.size)

        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun getOrCreateBiometricPublicKey(): PublicKey {
        // An invalidated key (new fingerprint enrolled) keeps its certificate, so encrypting a
        // fresh pairing with it would only produce another passwordKey that can never decrypt.
        if (isBiometricKeyInvalidated()) keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        keyStore.getCertificate(BIOMETRIC_KEY_ALIAS)?.let { return it.publicKey }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setKeySize(2048)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.initialize(builder.build())
        return generator.generateKeyPair().public
    }

    private fun isBiometricKeyInvalidated(): Boolean {
        val privateKey = runCatching { keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? PrivateKey }.getOrNull() ?: return false
        return try {
            Cipher.getInstance(RSA_TRANSFORMATION).init(Cipher.DECRYPT_MODE, privateKey, oaepParams())
            false
        } catch (e: KeyPermanentlyInvalidatedException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    // MGF1 stays SHA-1: it's the only MGF1 digest Android Keystore authorizes for an OAEP key by default
    // (setMgf1Digests is API 35+), so anything else encrypts fine but fails to decrypt.
    private fun oaepParams() = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    private fun encKeyPrefKey(deviceId: String) = "$deviceId.encKey"
    private fun passwordKeyPrefKey(deviceId: String) = "$deviceId.passwordKey"

    companion object {
        private const val TAG = "SecretsStore"
        private const val PREFS_NAME = "pcbu_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "pcbu_master_key"
        private const val BIOMETRIC_KEY_ALIAS = "pcbu_master_key_biometric"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
        private const val RSA_PREFIX = "rsa:"
        private const val GCM_IV_SIZE = 12 // Android Keystore's AES/GCM always uses a 96-bit IV.
        private const val GCM_TAG_BITS = 128
    }
}
