package com.atharva.securevault.security.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Core cryptographic services providing key derivation, hashing, and constant-time comparisons.
 */
class CryptoService {

    companion object {
        const val DEFAULT_PBKDF2_ITERATIONS = 100_000
        const val SALT_LENGTH_BYTES = 16
        const val DERIVED_KEY_LENGTH_BITS = 256
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random salt.
     */
    fun generateSalt(lengthBytes: Int = SALT_LENGTH_BYTES): ByteArray {
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a cryptographic key/hash from a PIN using PBKDF2 with HMAC-SHA256.
     * High iteration count (100,000) prevents GPU and ASIC brute-forcing.
     */
    fun derivePbkdf2(
        pin: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        keyLengthBits: Int = DERIVED_KEY_LENGTH_BITS
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Computes raw unsalted SHA-256 (for legacy vulnerability benchmarking).
     */
    fun hashSha256(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Constant-time comparison using java.security.MessageDigest.isEqual.
     * Resistant to side-channel timing analysis attacks.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }

    /**
     * Early-exit comparison that leaks timing information character by character.
     * Used exclusively in research mode to demonstrate timing side-channel vulnerabilities.
     */
    fun vulnerableEarlyExitEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) {
                return false // Early exit leaks timing
            }
        }
        return true
    }

    fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun fromHex(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = (
                (Character.digit(hex[i], 16) shl 4) +
                Character.digit(hex[i + 1], 16)
            ).toByte()
            i += 2
        }
        return data
    }
}
