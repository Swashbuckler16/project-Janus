package com.atharva.securevault.security.storage

import android.content.Context
import android.util.Base64
import com.atharva.securevault.security.crypto.CryptoService
import com.atharva.securevault.security.crypto.HashAlgorithm
import com.atharva.securevault.security.keystore.KeyStoreManager
import org.json.JSONObject

/**
 * Encrypted data model representing the stored verification credentials.
 */
data class StoredPinRecord(
    val algorithm: HashAlgorithm,
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int
)

/**
 * Manages secure persistence of authentication credentials.
 * Protects credential records at rest using hardware-backed AES-256-GCM encryption.
 */
class SecurePinStore(
    context: Context,
    private val keyStoreManager: KeyStoreManager = KeyStoreManager(),
    private val cryptoService: CryptoService = CryptoService()
) {

    companion object {
        private const val PREFS_NAME = "project_janus_secure_store"
        private const val KEY_ENCRYPTED_RECORD = "encrypted_pin_record"
        private const val KEY_ACTIVE_ALGORITHM = "active_algorithm"
        private const val LEGACY_PREFS_NAME = "secure_vault_lab"
        private const val LEGACY_VERIFIER_KEY = "pin_verifier"
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Stores a new PIN with the selected key derivation algorithm.
     * Computes the salt and hash, serializes them, and encrypts the entire record with the Keystore key.
     */
    fun savePin(pin: String, algorithm: HashAlgorithm = HashAlgorithm.PBKDF2_HMAC_SHA256) {
        val (salt, hash, iterations) = when (algorithm) {
            HashAlgorithm.PBKDF2_HMAC_SHA256 -> {
                val saltBytes = cryptoService.generateSalt()
                val hashBytes = cryptoService.derivePbkdf2(
                    pin = pin,
                    salt = saltBytes,
                    iterations = CryptoService.DEFAULT_PBKDF2_ITERATIONS
                )
                Triple(saltBytes, hashBytes, CryptoService.DEFAULT_PBKDF2_ITERATIONS)
            }
            HashAlgorithm.LEGACY_SHA256 -> {
                val hashBytes = cryptoService.hashSha256(pin)
                Triple(ByteArray(0), hashBytes, 1)
            }
        }

        val json = JSONObject().apply {
            put("algorithm", algorithm.name)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            put("iterations", iterations)
        }

        val plaintextBytes = json.toString().toByteArray(Charsets.UTF_8)
        val encryptedBytes = keyStoreManager.encrypt(plaintextBytes)
        val encodedPayload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        preferences.edit()
            .putString(KEY_ENCRYPTED_RECORD, encodedPayload)
            .putString(KEY_ACTIVE_ALGORITHM, algorithm.name)
            .apply()

        // Security Architecture Fix:
        // Do NOT unconditionally persist unsalted SHA-256 in unencrypted SharedPreferences.
        // In hardened mode, actively purge legacy credentials to prevent migration-shim leakage.
        if (algorithm == HashAlgorithm.LEGACY_SHA256) {
            legacyPrefs.edit()
                .putString(LEGACY_VERIFIER_KEY, cryptoService.toHex(cryptoService.hashSha256(pin)))
                .apply()
        } else {
            legacyPrefs.edit()
                .remove(LEGACY_VERIFIER_KEY)
                .apply()
        }
    }

    /**
     * Retrieves and decrypts the stored verification record.
     */
    fun getStoredRecord(): StoredPinRecord? {
        val encryptedPayload = preferences.getString(KEY_ENCRYPTED_RECORD, null)
        if (encryptedPayload != null) {
            return try {
                val cipherBytes = Base64.decode(encryptedPayload, Base64.NO_WRAP)
                val decryptedBytes = keyStoreManager.decrypt(cipherBytes)
                val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))

                val algoName = json.optString("algorithm", HashAlgorithm.PBKDF2_HMAC_SHA256.name)
                val algorithm = try {
                    HashAlgorithm.valueOf(algoName)
                } catch (e: Exception) {
                    HashAlgorithm.PBKDF2_HMAC_SHA256
                }

                val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
                val hash = Base64.decode(json.getString("hash"), Base64.NO_WRAP)
                val iterations = json.optInt("iterations", CryptoService.DEFAULT_PBKDF2_ITERATIONS)

                StoredPinRecord(algorithm, salt, hash, iterations)
            } catch (e: Exception) {
                null
            }
        }

        // Backward compatibility: Migrate from legacy prototype SharedPreferences if found
        val legacyHashHex = legacyPrefs.getString(LEGACY_VERIFIER_KEY, null)
        if (legacyHashHex != null) {
            val hashBytes = cryptoService.fromHex(legacyHashHex)
            return StoredPinRecord(
                algorithm = HashAlgorithm.LEGACY_SHA256,
                salt = ByteArray(0),
                hash = hashBytes,
                iterations = 1
            )
        }

        return null
    }

    fun getActiveAlgorithm(): HashAlgorithm {
        val storedName = preferences.getString(KEY_ACTIVE_ALGORITHM, null) ?: return HashAlgorithm.PBKDF2_HMAC_SHA256
        return try {
            HashAlgorithm.valueOf(storedName)
        } catch (e: Exception) {
            HashAlgorithm.PBKDF2_HMAC_SHA256
        }
    }

    fun isConfigured(): Boolean {
        return preferences.contains(KEY_ENCRYPTED_RECORD) || legacyPrefs.contains(LEGACY_VERIFIER_KEY)
    }

    fun isHardwareProtected(): Boolean {
        return keyStoreManager.isHardwareBacked()
    }
}
