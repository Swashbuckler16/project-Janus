package com.atharva.securevault.security

import android.content.Context
import com.atharva.securevault.security.crypto.HashAlgorithm
import com.atharva.securevault.security.storage.SecurePinStore

/**
 * High-level laboratory PIN configuration store.
 * Backed by [SecurePinStore] with hardware-backed AES-256-GCM encryption.
 */
class LabPinStore(context: Context) {

    private val secureStore = SecurePinStore(context)

    fun savePin(pin: String, algorithm: HashAlgorithm = HashAlgorithm.PBKDF2_HMAC_SHA256) {
        secureStore.savePin(pin, algorithm)
    }

    fun getActiveAlgorithm(): HashAlgorithm {
        return secureStore.getActiveAlgorithm()
    }

    fun isConfigured(): Boolean {
        return secureStore.isConfigured()
    }

    fun getConfiguredPin(): String? {
        return secureStore.getConfiguredPin()
    }

    fun isHardwareProtected(): Boolean {
        return secureStore.isHardwareProtected()
    }
}