package com.atharva.securevault.security.crypto

/**
 * Defines cryptographic key derivation and verification algorithms supported by Project Janus.
 */
enum class HashAlgorithm(val displayName: String, val isHardened: Boolean) {
    /**
     * Legacy unsalted SHA-256 with non-constant-time comparison.
     * Included as an empirical baseline to demonstrate rainbow table vulnerability and timing side-channels.
     */
    LEGACY_SHA256("Legacy SHA-256 (Vulnerable)", isHardened = false),

    /**
     * Industry-grade PBKDF2 with HMAC-SHA256, random 128-bit salt, 100,000 iterations,
     * and constant-time equality checks.
     */
    PBKDF2_HMAC_SHA256("PBKDF2-HMAC-SHA256 (100k rounds)", isHardened = true)
}
