package com.java.vidigal.code.test.ratelimit;

import com.java.vidigal.code.utilities.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link TokenBucketRateLimiter} class, verifying its functionality in enforcing
 * rate limits using a token bucket algorithm. Tests cover token acquisition within capacity, token
 * refill over time, blocking behavior when tokens are depleted, handling of concurrent access, and
 * validation of constructor parameters.
 */
class TokenBucketRateLimiterTest {

    /**
     * Instance of {@link TokenBucketRateLimiter} under test, initialized with a capacity of 5 tokens
     * and a refill period of 1000 milliseconds.
     */
    private TokenBucketRateLimiter rateLimiter;

    /**
     * Sets up the test environment before each test. Initializes a new
     * {@link TokenBucketRateLimiter} with a capacity of 5 tokens, a refill period of 1000 milliseconds,
     * and logging disabled.
     */
    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter(5, 1000, false);
    }

    /**
     * Tests that {@link TokenBucketRateLimiter#acquire} allows acquiring tokens within the configured
     * capacity. Verifies that after acquiring all available tokens, the current token count is zero
     * and the capacity remains as configured.
     *
     * @throws InterruptedException if the thread is interrupted during token acquisition.
     */
    @Test
    void shouldAcquireTokensWithinCapacity() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquire();
        }
        TokenBucketRateLimiter.RateLimiterStats stats = rateLimiter.getStats();
        assertEquals(0, stats.currentTokens(), "Current tokens should be 0 after acquiring all");
        assertEquals(5, stats.capacity(), "Capacity should remain 5");
    }

    /**
     * Tests that tokens are refilled over time according to the configured refill period. Verifies
     * that after depleting tokens and waiting for the refill period, new tokens are available for
     * acquisition, and the current token count is non-negative.
     *
     * @throws InterruptedException if the thread is interrupted during token acquisition or sleep.
     */
    @Test
    void shouldRefillTokensAfterTime() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquire();
        }
        Thread.sleep(1100); // Wait for refill (1 token per 200ms, 5 tokens in ~1000ms)
        rateLimiter.acquire();
        TokenBucketRateLimiter.RateLimiterStats stats = rateLimiter.getStats();
        assertTrue(stats.currentTokens() >= 0, "Current tokens should be non-negative after refill");
    }

    /**
     * Tests that {@link TokenBucketRateLimiter#acquire} blocks when tokens are depleted until new
     * tokens are refilled. Verifies that multiple threads attempting to acquire tokens wait for the
     * refill period, and the total execution time reflects the cooldown period.
     *
     * @throws InterruptedException if the thread is interrupted while awaiting the latch.
     */
    @Test
    void shouldBlockWhenTokensDepleted() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(6);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.acquire();
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "All threads should complete within 2 seconds");
        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed >= 1000, "Elapsed time should reflect cooldown period");
        executor.shutdown();
    }

    /**
     * Tests that constructing a {@link TokenBucketRateLimiter} with an invalid (zero or negative)
     * capacity throws an {@link IllegalArgumentException} with the appropriate error message.
     */
    @Test
    void shouldThrowExceptionForInvalidCapacity() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, 1000, false),
                "Expected IllegalArgumentException for invalid capacity"
        );
        assertEquals("Max requests per second must be positive", exception.getMessage(),
                "Exception message should indicate positive capacity required");
    }

    /**
     * Tests that {@link TokenBucketRateLimiter} handles concurrent access correctly. Verifies that
     * multiple threads acquiring tokens respect the rate limit, and the current token count remains
     * within the configured capacity.
     *
     * @throws InterruptedException if the thread is interrupted while awaiting the latch.
     */
    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.acquire();
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "All threads should complete within 3 seconds");
        TokenBucketRateLimiter.RateLimiterStats stats = rateLimiter.getStats();
        assertTrue(stats.currentTokens() <= 5, "Current tokens should not exceed capacity");
        executor.shutdown();
    }
}