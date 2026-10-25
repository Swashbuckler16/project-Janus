package com.atharva.securevault.security

import com.atharva.securevault.security.crypto.CryptoService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying cryptographic correctness, salt randomness,
 * and constant-time comparison behaviors of [CryptoService].
 */
class CryptoServiceTest {

    private lateinit var cryptoService: CryptoService

    @Before
    fun setUp() {
        cryptoService = CryptoService()
    }

    @Test
    fun pbkdf2_deterministic_withIdenticalInputs() {
        val pin = "1337"
        val salt = "0123456789abcdef".toByteArray()
        val iterations = 10_000

        val derived1 = cryptoService.derivePbkdf2(pin, salt, iterations)
        val derived2 = cryptoService.derivePbkdf2(pin, salt, iterations)

        assertArrayEquals("Identical PIN and salt must yield deterministic derived bytes", derived1, derived2)
    }

    @Test
    fun pbkdf2_differs_withDifferentSalts() {
        val pin = "1337"
        val salt1 = cryptoService.generateSalt()
        val salt2 = cryptoService.generateSalt()

        val derived1 = cryptoService.derivePbkdf2(pin, salt1, 10_000)
        val derived2 = cryptoService.derivePbkdf2(pin, salt2, 10_000)

        assertFalse("Different salts must produce distinct derived hashes", derived1.contentEquals(derived2))
    }

    @Test
    fun pbkdf2_differs_withDifferentPins() {
        val salt = cryptoService.generateSalt()

        val derived1 = cryptoService.derivePbkdf2("1234", salt, 10_000)
        val derived2 = cryptoService.derivePbkdf2("1235", salt, 10_000)

        assertFalse("Different PINs must produce distinct derived hashes", derived1.contentEquals(derived2))
    }

    @Test
    fun constantTimeEquals_correctness() {
        val bytesA = "SecureKeyBytes123".toByteArray()
        val bytesB = "SecureKeyBytes123".toByteArray()
        val bytesC = "DifferentKeyBytes".toByteArray()

        assertTrue(cryptoService.constantTimeEquals(bytesA, bytesB))
        assertFalse(cryptoService.constantTimeEquals(bytesA, bytesC))
    }

    @Test
    fun sha256_knownVector() {
        // SHA-256("1234") = 03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4
        val expectedHex = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        val computed = cryptoService.hashSha256("1234")
        val computedHex = cryptoService.toHex(computed)

        assertEquals(expectedHex, computedHex)
    }

    @Test
    fun hexConversion_roundtrip() {
        val original = cryptoService.generateSalt()
        val hex = cryptoService.toHex(original)
        val restored = cryptoService.fromHex(hex)

        assertArrayEquals(original, restored)
    }
}
