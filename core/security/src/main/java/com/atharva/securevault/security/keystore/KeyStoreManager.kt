package com.atharva.securevault.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages cryptographic operations backed by the Android hardware Keystore (TEE / StrongBox).
 * Encrypts and decrypts sensitive authentication records at rest using AES-256-GCM.
 */
class KeyStoreManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "project_janus_master_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves the existing AES-256 key or generates a new hardware-backed key in the Keystore.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * Encrypts plaintext data using AES-256-GCM.
     * Returns a payload containing [12-byte IV + Ciphertext with GCM Auth Tag].
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        // Prepend IV to ciphertext
        val output = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(ciphertext, 0, output, iv.size, ciphertext.size)
        return output
    }

    /**
     * Decrypts an encrypted payload produced by [encrypt].
     */
    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        require(encryptedPayload.size > GCM_IV_LENGTH_BYTES) { "Invalid ciphertext length" }

        val secretKey = getOrCreateMasterKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ciphertextLength = encryptedPayload.size - GCM_IV_LENGTH_BYTES
        val ciphertext = ByteArray(ciphertextLength)

        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPayload, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Checks if the master key is stored inside a hardware-backed security module (TEE / StrongBox).
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val secretKey = getOrCreateMasterKey()
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (e: Exception) {
            // Emulators or devices without TEE inspection support
            false
        }
    }
}
