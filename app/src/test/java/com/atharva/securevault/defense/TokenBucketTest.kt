package com.atharva.securevault.defense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying traffic shaping, depletion, and refill dynamics of [TokenBucket].
 */
class TokenBucketTest {

    private lateinit var tokenBucket: TokenBucket

    @Before
    fun setUp() {
        // 3 tokens capacity, refill 1 token per second
        tokenBucket = TokenBucket(capacity = 3.0, refillRatePerSecond = 1.0)
    }

    @Test
    fun initialCapacity_isFull() {
        assertEquals(3.0, tokenBucket.getAvailableTokens(), 0.05)
    }

    @Test
    fun consumeTokens_decreasesBalance() {
        assertTrue(tokenBucket.tryConsume(1.0))
        assertEquals(2.0, tokenBucket.getAvailableTokens(), 0.05)

        assertTrue(tokenBucket.tryConsume(2.0))
        assertEquals(0.0, tokenBucket.getAvailableTokens(), 0.05)
    }

    @Test
    fun consumeExcessiveTokens_failsWhenDepleted() {
        assertTrue(tokenBucket.tryConsume(3.0))
        // Bucket is now empty, next request must fail
        assertFalse(tokenBucket.tryConsume(1.0))
    }

    @Test
    fun reset_restoresFullCapacity() {
        assertTrue(tokenBucket.tryConsume(3.0))
        assertFalse(tokenBucket.tryConsume(1.0))

        tokenBucket.reset()
        assertEquals(3.0, tokenBucket.getAvailableTokens(), 0.05)
        assertTrue(tokenBucket.tryConsume(1.0))
    }
}
