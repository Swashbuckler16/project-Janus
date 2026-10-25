package com.atharva.securevault.defense

/**
 * Snapshot of current defense status, lockout parameters, and rate-limiting metrics.
 */
data class DefenseState(
    val isAllowed: Boolean,
    val tier: LockoutTier,
    val failedAttempts: Int,
    val remainingLockoutSeconds: Long = 0L,
    val penaltyDelayMs: Long = 0L,
    val retryAfterSeconds: Long? = null,
    val remainingTokens: Double = 0.0,
    val reason: String? = null
)
