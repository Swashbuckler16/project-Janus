package com.atharva.securevault.defense

/**
 * Thread-safe implementation of the Token Bucket rate-limiting algorithm.
 * Regulates incoming request burst traffic and continuous flow rates.
 *
 * @param capacity Maximum burst capacity (maximum tokens the bucket can hold).
 * @param refillRatePerSecond Rate at which tokens are replenished per second.
 */
class TokenBucket(
    val capacity: Double = 5.0,
    val refillRatePerSecond: Double = 0.5
) {
    private var tokens: Double = capacity
    private var lastRefillTimestampNanos: Long = System.nanoTime()
    private val lock = Any()

    /**
     * Attempts to consume [tokensToConsume] from the bucket.
     * Returns true if sufficient tokens were available, false otherwise.
     */
    fun tryConsume(tokensToConsume: Double = 1.0): Boolean {
        synchronized(lock) {
            refill()
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume
                return true
            }
            return false
        }
    }

    /**
     * Returns current available token balance after calculating elapsed refill.
     */
    fun getAvailableTokens(): Double {
        synchronized(lock) {
            refill()
            return tokens
        }
    }

    /**
     * Resets bucket to full capacity.
     */
    fun reset() {
        synchronized(lock) {
            tokens = capacity
            lastRefillTimestampNanos = System.nanoTime()
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSeconds = (now - lastRefillTimestampNanos) / 1_000_000_000.0
        if (elapsedSeconds > 0) {
            tokens = (tokens + elapsedSeconds * refillRatePerSecond).coerceAtMost(capacity)
            lastRefillTimestampNanos = now
        }
    }
}
