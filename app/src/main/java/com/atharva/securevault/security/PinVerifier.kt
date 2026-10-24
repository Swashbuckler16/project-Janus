package com.atharva.securevault.security

import android.content.Context
import com.atharva.securevault.security.crypto.CryptoService
import com.atharva.securevault.security.crypto.HashAlgorithm
import com.atharva.securevault.security.domain.VerificationResult
import com.atharva.securevault.security.storage.SecurePinStore

/**
 * Enterprise-grade PIN verification engine.
 * Computes cryptographic hashes and supports both constant-time verification (hardened)
 * and deliberate early-exit comparisons (research/vulnerability benchmarking).
 */
class PinVerifier(
    context: Context,
    private val pinStore: SecurePinStore = SecurePinStore(context),
    private val cryptoService: CryptoService = CryptoService()
) {

    fun verify(pin: String): Boolean {
        return verifyWithTelemetry(pin).isSuccess
    }

    /**
     * Executes verification and returns high-resolution execution telemetry.
     */
    fun verifyWithTelemetry(pin: String): VerificationResult {
        val record = pinStore.getStoredRecord()
            ?: return VerificationResult(
                isSuccess = false,
                executionTimeNanos = 0L,
                algorithm = HashAlgorithm.LEGACY_SHA256,
                isConstantTime = false,
                isHardwareBacked = pinStore.isHardwareProtected(),
                errorMessage = "No PIN configured"
            )

        val startTime = System.nanoTime()

        val isSuccess: Boolean
        val isConstantTime: Boolean

        when (record.algorithm) {
            HashAlgorithm.PBKDF2_HMAC_SHA256 -> {
                val candidateHash = cryptoService.derivePbkdf2(
                    pin = pin,
                    salt = record.salt,
                    iterations = record.iterations
                )
                isSuccess = cryptoService.constantTimeEquals(candidateHash, record.hash)
                isConstantTime = true
            }
            HashAlgorithm.LEGACY_SHA256 -> {
                val candidateHash = cryptoService.hashSha256(pin)
                // Vulnerable early-exit comparison for side-channel timing analysis
                isSuccess = cryptoService.vulnerableEarlyExitEquals(candidateHash, record.hash)
                isConstantTime = false
            }
        }

        val elapsedNanos = System.nanoTime() - startTime

        return VerificationResult(
            isSuccess = isSuccess,
            executionTimeNanos = elapsedNanos,
            algorithm = record.algorithm,
            isConstantTime = isConstantTime,
            isHardwareBacked = pinStore.isHardwareProtected()
        )
    }

    fun getActiveAlgorithm(): HashAlgorithm = pinStore.getActiveAlgorithm()

    fun isHardwareProtected(): Boolean = pinStore.isHardwareProtected()
}