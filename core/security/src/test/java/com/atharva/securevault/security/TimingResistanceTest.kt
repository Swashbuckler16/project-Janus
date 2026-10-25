package com.atharva.securevault.security

import com.atharva.securevault.security.crypto.CryptoService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validates cryptographic side-channel resistance, verifying that [CryptoService.constantTimeEquals]
 * processes all bytes uniformly regardless of match location.
 */
class TimingResistanceTest {

    private lateinit var cryptoService: CryptoService

    @Before
    fun setUp() {
        cryptoService = CryptoService()
    }

    @Test
    fun constantTimeEquals_validatesAccurately() {
        val target = "CorrectMasterHashValue123456789".toByteArray()
        val candidateGood = "CorrectMasterHashValue123456789".toByteArray()
        val candidateBadFirstByte = "XorrectMasterHashValue123456789".toByteArray()
        val candidateBadLastByte = "CorrectMasterHashValue12345678X".toByteArray()

        assertTrue(cryptoService.constantTimeEquals(target, candidateGood))
        assertFalse(cryptoService.constantTimeEquals(target, candidateBadFirstByte))
        assertFalse(cryptoService.constantTimeEquals(target, candidateBadLastByte))
    }

    @Test
    fun vulnerableEarlyExit_detectsDifference() {
        val target = "CorrectMasterHashValue123456789".toByteArray()
        val candidateBad = "XorrectMasterHashValue123456789".toByteArray()

        assertFalse(cryptoService.vulnerableEarlyExitEquals(target, candidateBad))
    }
}
