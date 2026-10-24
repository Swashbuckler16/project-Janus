package com.atharva.securevault.security.domain

import com.atharva.securevault.security.crypto.HashAlgorithm

/**
 * Detailed cryptographic and execution telemetry for an authentication attempt.
 */
data class VerificationResult(
    val isSuccess: Boolean,
    val executionTimeNanos: Long,
    val algorithm: HashAlgorithm,
    val isConstantTime: Boolean,
    val isHardwareBacked: Boolean,
    val errorMessage: String? = null
) {
    val executionTimeMs: Double
        get() = executionTimeNanos / 1_000_000.0
}
