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
 * Limits the rate of operations (e.g., API requests) using a token bucket algorithm, where tokens
 * are refilled at a fixed rate based on the maximum requests per second. Supports dynamic updates
 * to the rate and cooldown period, optional logging when used with caching, and provides statistics
 * via {@link RateLimiterStats}. Uses a {@link ReentrantLock} for thread safety and SLF4J for logging.
 * </p>
 *
 * @author Vidigal
 */
public class TokenBucketRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    private static final long LOG_FREQUENCY = 100;
    private static final String MAX_REQUESTS_POSITIVE = "Max requests per second must be positive";
    private static final String COOLDOWN_NON_NEGATIVE = "Cooldown period must be non-negative";
    private final boolean cacheEnabled;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillNanos;
    private final AtomicLong refillCount = new AtomicLong();
    private final AtomicLong accessCount = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition tokenAvailable = lock.newCondition();
    private volatile long capacity;
    private volatile long refillIntervalNanos;
    private volatile long cooldownMillis;

    /**
     * Constructs a new {@code TokenBucketRateLimiter} with the specified parameters.
     *
     * @param maxRequestsPerSecond the maximum number of requests allowed per second, must be positive
     * @param cooldownMillis       the cooldown period in milliseconds when no tokens are available, must be non-negative
     * @param cacheEnabled         whether logging is enabled (typically tied to caching)
     * @throws IllegalArgumentException if {@code maxRequestsPerSecond} is not positive or {@code cooldownMillis} is negative
     */
    public TokenBucketRateLimiter(long maxRequestsPerSecond, long cooldownMillis, boolean cacheEnabled) {
        if (maxRequestsPerSecond <= 0) {
            logger.error(MAX_REQUESTS_POSITIVE);
            throw new IllegalArgumentException(MAX_REQUESTS_POSITIVE);
        }
        if (cooldownMillis < 0) {
            logger.error(COOLDOWN_NON_NEGATIVE);
            throw new IllegalArgumentException(COOLDOWN_NON_NEGATIVE);
        }
        this.capacity = maxRequestsPerSecond;
        this.refillIntervalNanos = 1_000_000_000L / maxRequestsPerSecond;
        this.cooldownMillis = cooldownMillis;
        this.cacheEnabled = cacheEnabled;
        this.tokens = new AtomicLong(maxRequestsPerSecond);
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Updates the rate limiter's configuration dynamically.
     *
     * @param maxRequestsPerSecond the new maximum requests per second, must be positive
     * @param cooldownMillis       the new cooldown period in milliseconds, must be non-negative
     * @throws IllegalArgumentException if {@code maxRequestsPerSecond} is not positive or {@code cooldownMillis} is negative
     */
    public void update(long maxRequestsPerSecond, long cooldownMillis) {
        if (maxRequestsPerSecond <= 0) {
            logger.error(MAX_REQUESTS_POSITIVE);
            throw new IllegalArgumentException(MAX_REQUESTS_POSITIVE);
        }
        if (cooldownMillis < 0) {
            logger.error(COOLDOWN_NON_NEGATIVE);
            throw new IllegalArgumentException(COOLDOWN_NON_NEGATIVE);
        }
        lock.lock();
        try {
            this.capacity = maxRequestsPerSecond;
            this.refillIntervalNanos = 1_000_000_000L / maxRequestsPerSecond;
            this.cooldownMillis = cooldownMillis;
            tokens.updateAndGet(current -> Math.min(current, maxRequestsPerSecond));
            tokenAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquires a token, blocking until one is available or the cooldown period expires.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
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
                boolean tokenReceived = tokenAvailable.await(cooldownMillis, TimeUnit.MILLISECONDS);
                if (!tokenReceived && cacheEnabled && shouldLog()) {
                    logger.debug("Cooldown period expired, retrying token acquisition");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to acquire a token without blocking.
     *
     * @return true if a token was acquired, false otherwise
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
            if (tokens.get() > 0) {
                lock.lock();
                try {
                    tokenAvailable.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Retrieves the current rate limiter statistics.
     *
     * @return a {@link RateLimiterStats} record with current tokens, capacity, and refill count
     */
    public RateLimiterStats getStats() {
        refill();
        return new RateLimiterStats(tokens.get(), capacity, refillCount.get());
    }

    /**
     * Determines if a rate limiter operation should be logged based on access frequency.
     *
     * @return true if the current access count is a multiple of {@link #LOG_FREQUENCY}
     */
    private boolean shouldLog() {
        return accessCount.incrementAndGet() % LOG_FREQUENCY == 0;
    }

    /**
     * Record representing rate limiter statistics.
     *
     * @param currentTokens the current number of available tokens
     * @param capacity      the maximum number of tokens the bucket can hold
     * @param refillCount   the number of token refill events
     */
    public record RateLimiterStats(long currentTokens, long capacity, long refillCount) {
    }
}