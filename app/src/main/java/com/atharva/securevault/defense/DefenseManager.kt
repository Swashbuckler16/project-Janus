package com.atharva.securevault.defense

import android.content.Context
import com.atharva.securevault.DefenseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enterprise defensive controller managing token-bucket traffic shaping,
 * persistent multi-tier progressive penalties, and tamper-resistant lockouts.
 */
class DefenseManager(
    context: Context,
    private val tokenBucket: TokenBucket = TokenBucket(capacity = 5.0, refillRatePerSecond = 0.5)
) {
    companion object {
        private const val PREFS_NAME = "project_janus_defense_state"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"
        private const val KEY_LAST_BACKOFF_SEC = "last_backoff_sec"

        const val THROTTLE_DELAY_MS = 1500L
        const val BASE_LOCKOUT_SECONDS = 30L
        const val MAX_ATTEMPTS_BEFORE_CRITICAL = 10
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Legacy sliding window state for comparative benchmarking
    private val slidingWindowMs = 10_000L
    private val slidingWindowMaxAttempts = 5
    private var slidingWindowStartTime = 0L
    private var slidingWindowAttempts = 0

    private val _defenseState = MutableStateFlow(calculateCurrentState(DefenseMode.ENTERPRISE_TOKEN_BUCKET))
    val defenseState: StateFlow<DefenseState> = _defenseState.asStateFlow()

    /**
     * Evaluates whether an incoming verification request is permitted under the active [DefenseMode].
     * Injects artificial latency if throttled, or rejects if locked out/depleted.
     */
    fun evaluateBeforeVerification(mode: DefenseMode): DefenseState {
        when (mode) {
            DefenseMode.NONE -> {
                val state = DefenseState(
                    isAllowed = true,
                    tier = LockoutTier.NORMAL,
                    failedAttempts = getFailedAttempts(),
                    remainingTokens = tokenBucket.getAvailableTokens()
                )
                _defenseState.value = state
                return state
            }

            DefenseMode.SLIDING_WINDOW -> {
                val now = System.currentTimeMillis()
                if (now - slidingWindowStartTime >= slidingWindowMs) {
                    slidingWindowStartTime = now
                    slidingWindowAttempts = 0
                }

                if (slidingWindowAttempts >= slidingWindowMaxAttempts) {
                    val retryAfter = ((slidingWindowMs - (now - slidingWindowStartTime)) / 1000).coerceAtLeast(1)
                    val state = DefenseState(
                        isAllowed = false,
                        tier = LockoutTier.TEMPORARY_LOCKOUT,
                        failedAttempts = slidingWindowAttempts,
                        retryAfterSeconds = retryAfter,
                        reason = "Sliding window threshold exceeded (5 req / 10s)"
                    )
                    _defenseState.value = state
                    return state
                }

                slidingWindowAttempts++
                val state = DefenseState(
                    isAllowed = true,
                    tier = LockoutTier.NORMAL,
                    failedAttempts = slidingWindowAttempts
                )
                _defenseState.value = state
                return state
            }

            DefenseMode.ENTERPRISE_TOKEN_BUCKET -> {
                val now = System.currentTimeMillis()
                val lockoutUntil = getLockoutUntilMs()

                // Check 1: Persistent Lockout Check
                if (now < lockoutUntil) {
                    val remainingSec = ((lockoutUntil - now) / 1000).coerceAtLeast(1)
                    val tier = calculateTier(getFailedAttempts())
                    val state = DefenseState(
                        isAllowed = false,
                        tier = tier,
                        failedAttempts = getFailedAttempts(),
                        remainingLockoutSeconds = remainingSec,
                        retryAfterSeconds = remainingSec,
                        reason = if (tier == LockoutTier.PERMANENT_LOCKOUT) {
                            "Vault critically locked. Security lab reset required."
                        } else {
                            "Device locked due to repeated authentication failures."
                        }
                    )
                    _defenseState.value = state
                    return state
                }

                // Check 2: Token Bucket Traffic Regulation
                val tokenConsumed = tokenBucket.tryConsume(1.0)
                if (!tokenConsumed) {
                    val state = DefenseState(
                        isAllowed = false,
                        tier = calculateTier(getFailedAttempts()),
                        failedAttempts = getFailedAttempts(),
                        retryAfterSeconds = 2L,
                        remainingTokens = tokenBucket.getAvailableTokens(),
                        reason = "Request rate limit exceeded. Token bucket depleted."
                    )
                    _defenseState.value = state
                    return state
                }

                // Check 3: Progressive Throttling Penalty (Tier 1: 4-5 failed attempts)
                val failed = getFailedAttempts()
                val tier = calculateTier(failed)
                val penaltyDelay = if (tier == LockoutTier.THROTTLED) THROTTLE_DELAY_MS else 0L

                val state = DefenseState(
                    isAllowed = true,
                    tier = tier,
                    failedAttempts = failed,
                    penaltyDelayMs = penaltyDelay,
                    remainingTokens = tokenBucket.getAvailableTokens()
                )
                _defenseState.value = state
                return state
            }
        }
    }

    /**
     * Records the outcome of a verification attempt, updating persistent failed counters,
     * exponential backoff timers, and lockout tiers.
     */
    fun recordAttemptResult(isSuccess: Boolean, mode: DefenseMode): DefenseState {
        if (isSuccess) {
            // Successful authentication resets defensive penalties
            resetFailedAttempts()
            tokenBucket.reset()
            val state = DefenseState(
                isAllowed = true,
                tier = LockoutTier.NORMAL,
                failedAttempts = 0,
                remainingTokens = tokenBucket.capacity
            )
            _defenseState.value = state
            return state
        }

        // Increment persistent failed attempts counter
        val newFailed = getFailedAttempts() + 1
        saveFailedAttempts(newFailed)

        val tier = calculateTier(newFailed)
        var lockoutSec = 0L

        when (tier) {
            LockoutTier.TEMPORARY_LOCKOUT -> {
                // Exponential backoff: 6th attempt = 30s, 7th = 60s, 8th = 120s, 9th = 300s
                lockoutSec = when (newFailed) {
                    6 -> BASE_LOCKOUT_SECONDS        // 30s
                    7 -> BASE_LOCKOUT_SECONDS * 2    // 60s
                    8 -> BASE_LOCKOUT_SECONDS * 4    // 120s
                    else -> 300L                     // 5 mins
                }
                val lockoutUntil = System.currentTimeMillis() + (lockoutSec * 1000L)
                saveLockoutUntilMs(lockoutUntil, lockoutSec)
            }
            LockoutTier.PERMANENT_LOCKOUT -> {
                // Critical lockdown: 24-hour lockout or until lab reset
                lockoutSec = 86400L
                val lockoutUntil = System.currentTimeMillis() + (lockoutSec * 1000L)
                saveLockoutUntilMs(lockoutUntil, lockoutSec)
            }
            else -> {
                // Tier 0 or Tier 1
            }
        }

        val state = DefenseState(
            isAllowed = lockoutSec == 0L,
            tier = tier,
            failedAttempts = newFailed,
            remainingLockoutSeconds = lockoutSec,
            retryAfterSeconds = if (lockoutSec > 0) lockoutSec else null,
            penaltyDelayMs = if (tier == LockoutTier.THROTTLED) THROTTLE_DELAY_MS else 0L,
            remainingTokens = tokenBucket.getAvailableTokens()
        )
        _defenseState.value = state
        return state
    }

    /**
     * Resets all persistent failed counters and clears lockouts.
     */
    fun resetDefenseState() {
        preferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_LAST_BACKOFF_SEC, 0L)
            .apply()

        slidingWindowAttempts = 0
        slidingWindowStartTime = 0L
        tokenBucket.reset()

        val state = DefenseState(
            isAllowed = true,
            tier = LockoutTier.NORMAL,
            failedAttempts = 0,
            remainingTokens = tokenBucket.capacity
        )
        _defenseState.value = state
    }

    fun isLockedOut(): Boolean {
        val now = System.currentTimeMillis()
        val lockoutUntil = getLockoutUntilMs()
        return now < lockoutUntil
    }

    fun getRemainingLockoutSeconds(): Long {
        val now = System.currentTimeMillis()
        val lockoutUntil = getLockoutUntilMs()
        return if (now < lockoutUntil) ((lockoutUntil - now) / 1000).coerceAtLeast(1) else 0L
    }

    fun getFailedAttempts(): Int = preferences.getInt(KEY_FAILED_ATTEMPTS, 0)

    private fun getLockoutUntilMs(): Long = preferences.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)

    private fun saveFailedAttempts(count: Int) {
        preferences.edit().putInt(KEY_FAILED_ATTEMPTS, count).apply()
    }

    private fun saveLockoutUntilMs(timestampMs: Long, durationSec: Long) {
        preferences.edit()
            .putLong(KEY_LOCKOUT_UNTIL_MS, timestampMs)
            .putLong(KEY_LAST_BACKOFF_SEC, durationSec)
            .apply()
    }

    private fun resetFailedAttempts() {
        preferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    private fun calculateTier(failedAttempts: Int): LockoutTier {
        return when {
            failedAttempts >= MAX_ATTEMPTS_BEFORE_CRITICAL -> LockoutTier.PERMANENT_LOCKOUT
            failedAttempts >= 6 -> LockoutTier.TEMPORARY_LOCKOUT
            failedAttempts >= 4 -> LockoutTier.THROTTLED
            else -> LockoutTier.NORMAL
        }
    }

    private fun calculateCurrentState(mode: DefenseMode): DefenseState {
        val failed = getFailedAttempts()
        val tier = calculateTier(failed)
        val remainingSec = getRemainingLockoutSeconds()
        return DefenseState(
            isAllowed = remainingSec == 0L,
            tier = tier,
            failedAttempts = failed,
            remainingLockoutSeconds = remainingSec,
            retryAfterSeconds = if (remainingSec > 0) remainingSec else null,
            remainingTokens = tokenBucket.getAvailableTokens()
        )
    }
}
