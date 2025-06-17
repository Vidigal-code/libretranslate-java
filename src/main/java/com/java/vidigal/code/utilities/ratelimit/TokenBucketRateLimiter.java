package com.java.vidigal.code.utilities.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe token bucket rate limiter with monitoring capabilities.
 * <p>
 * This implementation limits the rate of operations (e.g., API requests) using a token bucket
 * algorithm, where tokens are refilled at a fixed rate based on the maximum requests per second.
 * It supports dynamic updates to the rate and cooldown period, optional logging when used with
 * caching, and provides statistics via {@link RateLimiterStats}. The limiter uses a
 * {@link ReentrantLock} for thread safety and SLF4J for logging.
 * </p>
 */
public class TokenBucketRateLimiter {

    /**
     * Logger for recording rate limiter operations and errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    /**
     * Frequency of logging operations (every N accesses).
     */
    private static final long LOG_FREQUENCY = 100;
    /**
     * Flag indicating whether logging is enabled (typically when caching is used).
     */
    private final boolean cacheEnabled;
    /**
     * Current number of available tokens.
     */
    private final AtomicLong tokens;
    /**
     * Timestamp of the last token refill in nanoseconds.
     */
    private final AtomicLong lastRefillNanos;
    /**
     * Counter for the number of token refill events.
     */
    private final AtomicLong refillCount = new AtomicLong();
    /**
     * Counter for total access attempts, used to control logging frequency.
     */
    private final AtomicLong accessCount = new AtomicLong();
    /**
     * Lock for synchronizing token acquisition and updates.
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Condition for signaling when tokens become available.
     */
    private final Condition tokenAvailable = lock.newCondition();
    /**
     * Maximum number of tokens the bucket can hold, equivalent to max requests per second.
     */
    private volatile long capacity;
    /**
     * Time interval in nanoseconds between token refills.
     */
    private volatile long refillIntervalNanos;
    /**
     * Cooldown period in milliseconds for waiting when no tokens are available.
     */
    private volatile long cooldownMillis;

    /**
     * Constructs a new {@code TokenBucketRateLimiter} with the specified parameters.
     *
     * @param maxRequestsPerSecond The maximum number of requests allowed per second, must be positive.
     * @param cooldownMillis       The cooldown period in milliseconds for waiting when no tokens are available.
     * @param cacheEnabled         Whether logging is enabled (typically tied to caching).
     * @throws IllegalArgumentException if {@code maxRequestsPerSecond} is not positive.
     */
    public TokenBucketRateLimiter(long maxRequestsPerSecond, long cooldownMillis, boolean cacheEnabled) {

        if (maxRequestsPerSecond <= 0) {
            logger.error("Max requests per second must be positive");
            throw new IllegalArgumentException("Max requests per second must be positive");
        }

        this.capacity = maxRequestsPerSecond;
        this.refillIntervalNanos = 1_000_000_000L / maxRequestsPerSecond;
        this.cooldownMillis = cooldownMillis;
        this.cacheEnabled = cacheEnabled;
        this.tokens = new AtomicLong(maxRequestsPerSecond);
        this.lastRefillNanos = new AtomicLong(System.nanoTime());

        //logger.info("Initialized rate limiter: capacity={}, cooldown={}ms", maxRequestsPerSecond, cooldownMillis);
    }

    /**
     * Updates the rate limiter's configuration dynamically.
     * <p>
     * Adjusts the capacity, refill interval, and cooldown period. Ensures the current token count
     * does not exceed the new capacity. Signals waiting threads to retry token acquisition.
     * </p>
     *
     * @param maxRequestsPerSecond The new maximum requests per second, must be positive.
     * @param cooldownMillis       The new cooldown period in milliseconds.
     * @throws IllegalArgumentException if {@code maxRequestsPerSecond} is not positive.
     */
    public void update(long maxRequestsPerSecond, long cooldownMillis) {

        if (maxRequestsPerSecond <= 0) {
            logger.error("Max requests per second must be positive");
            throw new IllegalArgumentException("Max requests per second must be positive");
        }

        lock.lock();
        try {

            this.capacity = maxRequestsPerSecond;
            this.refillIntervalNanos = 1_000_000_000L / maxRequestsPerSecond;
            this.cooldownMillis = cooldownMillis;
            tokens.updateAndGet(current -> Math.min(current, maxRequestsPerSecond));

            //logger.info("Updated rate limiter: capacity={}, cooldown={}ms", maxRequestsPerSecond, cooldownMillis);

            tokenAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquires a token, blocking until one is available or the cooldown period expires.
     * <p>
     * Refills tokens based on elapsed time before attempting to acquire. If no tokens are available,
     * the thread waits up to the cooldown period before retrying.
     * </p>
     *
     * @throws InterruptedException if the thread is interrupted while waiting for a token.
     */
    public void acquire() throws InterruptedException {
        lock.lock();
        try {
            while (true) {
                refill();
                long currentTokens = tokens.get();
                if (currentTokens > 0 && tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                    if (cacheEnabled && shouldLog()) {
                        logger.debug("Acquired token, remaining: {}", currentTokens - 1);
                    }
                    return;
                }
                tokenAvailable.await(cooldownMillis, TimeUnit.MILLISECONDS);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to acquire a token without blocking.
     * <p>
     * Refills tokens based on elapsed time before attempting to acquire. Returns immediately,
     * indicating whether a token was acquired.
     * </p>
     *
     * @return True if a token was acquired, false otherwise.
     */
    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            long currentTokens = tokens.get();
            if (currentTokens > 0 && tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                if (cacheEnabled && shouldLog()) {
                    logger.debug("Non-blocking token acquired, remaining: {}", currentTokens - 1);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Refills tokens based on elapsed time since the last refill.
     * <p>
     * Calculates the number of tokens to add based on the refill interval and updates the token
     * count, ensuring it does not exceed the capacity. Signals waiting threads if tokens are added.
     * </p>
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillNanos.get();
        long elapsedNanos = now - lastRefill;
        long tokensToAdd = elapsedNanos / refillIntervalNanos;

        if (tokensToAdd > 0 && lastRefillNanos.compareAndSet(lastRefill, now)) {

            tokens.updateAndGet(current -> Math.min(current + tokensToAdd, capacity));
            refillCount.incrementAndGet();

            if (cacheEnabled && shouldLog()) {
                logger.debug("Refilled {} tokens, current: {}", tokensToAdd, tokens.get());
            }

            lock.lock();
            try {
                tokenAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Retrieves the current rate limiter statistics.
     * <p>
     * Refills tokens before collecting stats to ensure accuracy.
     * </p>
     *
     * @return A {@link RateLimiterStats} record containing current tokens, capacity, and refill count.
     */
    public RateLimiterStats getStats() {
        refill();
        return new RateLimiterStats(tokens.get(), capacity, refillCount.get());
    }

    /**
     * Determines if a rate limiter operation should be logged based on access frequency.
     *
     * @return True if the current access count is a multiple of {@link #LOG_FREQUENCY}.
     */
    private boolean shouldLog() {
        return accessCount.incrementAndGet() % LOG_FREQUENCY == 0;
    }

    /**
     * Record representing rate limiter statistics.
     *
     * @param currentTokens The current number of available tokens.
     * @param capacity      The maximum number of tokens the bucket can hold.
     * @param refillCount   The number of token refill events.
     */
    public record RateLimiterStats(long currentTokens, long capacity, long refillCount) {
    }
}