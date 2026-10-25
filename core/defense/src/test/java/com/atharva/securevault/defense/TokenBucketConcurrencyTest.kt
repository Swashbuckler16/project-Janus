package com.atharva.securevault.defense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Validates thread-safety of [TokenBucket] under high-concurrency race condition scenarios.
 */
class TokenBucketConcurrencyTest {

    @Test
    fun concurrentConsumption_doesNotExceedCapacity() {
        val capacity = 50.0
        val tokenBucket = TokenBucket(capacity = capacity, refillRatePerSecond = 0.0) // zero refill during burst test

        val numberOfThreads = 100
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(numberOfThreads)
        val successfulConsumptions = AtomicInteger(0)

        for (i in 0 until numberOfThreads) {
            executor.submit {
                try {
                    startLatch.await()
                    if (tokenBucket.tryConsume(1.0)) {
                        successfulConsumptions.incrementAndGet()
                    }
                } finally {
                    completionLatch.countDown()
                }
            }
        }

        // Fire all threads simultaneously
        startLatch.countDown()
        completionLatch.await()
        executor.shutdown()

        // Total successful consumptions must exactly match initial capacity
        assertEquals(
            "Concurrent consumptions must not exceed bucket capacity",
            capacity.toInt(),
            successfulConsumptions.get()
        )
        assertTrue(tokenBucket.getAvailableTokens() >= 0.0)
    }
}
