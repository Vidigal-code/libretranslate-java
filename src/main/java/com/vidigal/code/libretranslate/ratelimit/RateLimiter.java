package com.vidigal.code.libretranslate.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Advanced adaptive token bucket rate limiter implementation.
 * <p>
 * Features:
 * - Efficient token bucket algorithm with precise timing
 * - Adaptive behavior based on API responses
 * - Supports burst capacity for handling spikes in traffic
 * - Comprehensive metrics for monitoring
 * - Configurable parameters for fine-tuning
 * - Thread-safe implementation using read-write locks
 */
public class RateLimiter implements RateLimiterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiter.class);
    private static final long MIN_SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * Flag to enable/disable detailed logging
     */
    public static boolean DETAILED_LOGGING = false;

    // Configuration parameters
    private final int baseRequestRate;
    private final double burstCapacityMultiplier;
    private final long maxWaitTimeNanos;
    // Thread synchronization
    private final ReentrantReadWriteLock lock;
    // Metrics
    private final AtomicLong totalRequests;
    private final AtomicLong permittedRequests;
    private final AtomicLong throttledRequests;
    private final AtomicLong totalWaitTimeNanos;
    private volatile int currentMaxRequestRate;
    // Token bucket state
    private volatile double tokensAvailable;
    private volatile long lastRefillTimestampNanos;
    private volatile long nanosPerToken;
    private volatile long lastThrottleTimestampMillis;
    private volatile long backoffExpirationTimestampMillis;
    private volatile boolean inBackoffMode;

    /**
     * Creates a new rate limiter with the specified request rate.
     *
     * @param requestsPerSecond Maximum number of requests allowed per second
     * @throws IllegalArgumentException if requestsPerSecond is less than 1
     */
    public RateLimiter(int requestsPerSecond) {
        this(requestsPerSecond, 2.0, TimeUnit.SECONDS.toNanos(10));
    }

    /**
     * Creates a new rate limiter with detailed configuration.
     *
     * @param requestsPerSecond       Maximum number of requests allowed per second
     * @param burstCapacityMultiplier Multiplier for burst capacity (e.g., 2.0 allows 2x bursts)
     * @param maxWaitTimeNanos        Maximum time to wait for a permit in nanoseconds
     * @throws IllegalArgumentException if parameters are invalid
     */
    public RateLimiter(int requestsPerSecond, double burstCapacityMultiplier, long maxWaitTimeNanos) {
        validateParameters(requestsPerSecond, burstCapacityMultiplier, maxWaitTimeNanos);

        this.baseRequestRate = requestsPerSecond;
        this.currentMaxRequestRate = requestsPerSecond;
        this.burstCapacityMultiplier = burstCapacityMultiplier;
        this.maxWaitTimeNanos = maxWaitTimeNanos;

        this.nanosPerToken = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond;
        this.tokensAvailable = requestsPerSecond; // Start with a full bucket
        this.lastRefillTimestampNanos = System.nanoTime();

        this.lock = new ReentrantReadWriteLock(true); // Fair scheduling

        this.totalRequests = new AtomicLong(0);
        this.permittedRequests = new AtomicLong(0);
        this.throttledRequests = new AtomicLong(0);
        this.totalWaitTimeNanos = new AtomicLong(0);
        this.lastThrottleTimestampMillis = 0;
        this.backoffExpirationTimestampMillis = 0;
        this.inBackoffMode = false;

        if (DETAILED_LOGGING) {
            LOGGER.info("Initialized rate limiter: baseRate={}/sec, burstCapacity={}/sec, maxWaitTime={}ms",
                    requestsPerSecond,
                    (int) (requestsPerSecond * burstCapacityMultiplier),
                    TimeUnit.NANOSECONDS.toMillis(maxWaitTimeNanos));
        }
    }

    /**
     * Validates constructor parameters.
     */
    private void validateParameters(int requestsPerSecond, double burstCapacityMultiplier, long maxWaitTimeNanos) {
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("Requests per second must be at least 1, got: " + requestsPerSecond);
        }
        if (burstCapacityMultiplier < 1.0) {
            throw new IllegalArgumentException("Burst capacity multiplier must be at least 1.0, got: " + burstCapacityMultiplier);
        }
        if (maxWaitTimeNanos < 0) {
            throw new IllegalArgumentException("Max wait time cannot be negative, got: " + maxWaitTimeNanos);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acquire() throws InterruptedException {
        totalRequests.incrementAndGet();

        // Fast-path: check if we can immediately acquire without locking
        if (!inBackoffMode && tryAcquireImmediately()) {
            permittedRequests.incrementAndGet();
            return true;
        }

        return acquireWithBackoffAwareness();
    }

    /**
     * Tries to acquire a permit without waiting or blocking.
     */
    private boolean tryAcquireImmediately() {
        // First, do a volatile read check without locking
        double currentTokens = tokensAvailable;
        if (currentTokens >= 1.0) {
            // Try with a read lock first
            lock.readLock().lock();
            try {
                // Recheck state under lock
                if (tokensAvailable >= 1.0) {
                    // Only refill if we've been running for a while (optimization)
                    long now = System.nanoTime();
                    long elapsed = now - lastRefillTimestampNanos;
                    if (elapsed > nanosPerToken * 10) {
                        // Upgrade to write lock to refill and consume
                        lock.readLock().unlock();
                        lock.writeLock().lock();
                        try {
                            refillTokens();
                            if (tokensAvailable >= 1.0) {
                                tokensAvailable -= 1.0;
                                permittedRequests.incrementAndGet();
                                return true;
                            }
                        } finally {
                            lock.readLock().lock();
                            lock.writeLock().unlock();
                        }
                    } else if (tokensAvailable >= 1.0) {
                        // Simple case: enough tokens without refilling
                        lock.readLock().unlock();
                        lock.writeLock().lock();
                        try {
                            tokensAvailable -= 1.0;
                            return true;
                        } finally {
                            lock.writeLock().unlock();
                        }
                    }
                }
            } finally {
                // Only unlock if we still hold the lock
                if (lock.getReadHoldCount() > 0) {
                    lock.readLock().unlock();
                }
            }
        }
        return false;
    }

    /**
     * Acquires a permit with awareness of backoff status, waiting if necessary.
     */
    private boolean acquireWithBackoffAwareness() throws InterruptedException {
        long waitStartNanos = System.nanoTime();
        lock.writeLock().lock();
        try {
            // Check if we're in backoff mode and if it's expired
            checkAndUpdateBackoffMode();

            // Refill tokens based on elapsed time
            refillTokens();

            // Check if we have tokens available
            if (tokensAvailable >= 1.0) {
                tokensAvailable -= 1.0;
                permittedRequests.incrementAndGet();
                return true;
            }

            // Calculate wait time based on current rate
            long requiredWaitTimeNanos = calculateWaitTimeNanos();

            // Check if wait time exceeds maximum
            if (requiredWaitTimeNanos > maxWaitTimeNanos) {
                throttledRequests.incrementAndGet();
                LOGGER.warn("Request throttled: required wait time {}ms exceeds maximum {}ms",
                        TimeUnit.NANOSECONDS.toMillis(requiredWaitTimeNanos),
                        TimeUnit.NANOSECONDS.toMillis(maxWaitTimeNanos));
                lastThrottleTimestampMillis = System.currentTimeMillis();
                return false;
            }

            // Release lock while waiting
            if (requiredWaitTimeNanos > MIN_SLEEP_NANOS) {
                lock.writeLock().unlock();
                try {
                    TimeUnit.NANOSECONDS.sleep(requiredWaitTimeNanos);
                } finally {
                    lock.writeLock().lock();
                }

                // Update metrics
                long actualWaitNanos = System.nanoTime() - waitStartNanos;
                totalWaitTimeNanos.addAndGet(actualWaitNanos);

                // Refill tokens again after waiting
                refillTokens();
            }

            // Consume token
            tokensAvailable -= 1.0;
            permittedRequests.incrementAndGet();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates the token bucket based on elapsed time.
     * Must be called with write lock held.
     */
    private void refillTokens() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillTimestampNanos;

        if (elapsedNanos > 0) {
            // Calculate tokens to add based on current rate limit
            double tokensToAdd = (double) elapsedNanos / nanosPerToken;

            // The burst capacity is the maximum number of tokens the bucket can hold
            double maxTokens = currentMaxRequestRate * burstCapacityMultiplier;

            // Add tokens and cap at the burst capacity
            tokensAvailable = Math.min(tokensAvailable + tokensToAdd, maxTokens);

            // Update the timestamp
            lastRefillTimestampNanos = nowNanos;
        }
    }

    /**
     * Calculates the wait time needed to get a token.
     * Must be called with write lock held.
     */
    private long calculateWaitTimeNanos() {
        // How many tokens we need (we're missing 1.0 tokens)
        double tokensNeeded = 1.0 - tokensAvailable;

        // Convert to nanoseconds
        return (long) (tokensNeeded * nanosPerToken);
    }

    /**
     * Checks if the backoff mode has expired and updates the state.
     * Must be called with write lock held.
     */
    private void checkAndUpdateBackoffMode() {
        if (inBackoffMode) {
            long now = System.currentTimeMillis();
            if (now >= backoffExpirationTimestampMillis) {
                // Backoff expired, gradually increase rate
                inBackoffMode = false;

                // Gradually increase the rate back to base over time
                // For this simple case, immediately restore to base rate
                currentMaxRequestRate = baseRequestRate;
                nanosPerToken = TimeUnit.SECONDS.toNanos(1) / currentMaxRequestRate;

                LOGGER.info("Backoff mode expired, restored rate to {}/sec", currentMaxRequestRate);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyRateLimitExceeded(int retryAfterSeconds) {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();

            // Ensure retry time is reasonable
            int effectiveRetrySeconds = Math.min(Math.max(retryAfterSeconds, 1), 60);

            // Calculate new backoff expiration time
            backoffExpirationTimestampMillis = now + (effectiveRetrySeconds * 1000L);

            // If this is a repeated backoff (within 30 seconds of the last one), reduce rate more aggressively
            boolean repeatedBackoff = (now - lastThrottleTimestampMillis) < 30_000;

            // Reduce the rate limit
            if (repeatedBackoff) {
                // More aggressive reduction for repeated backoffs
                currentMaxRequestRate = Math.max(1, currentMaxRequestRate / 2);
            } else {
                // Standard reduction
                currentMaxRequestRate = Math.max(1, (int) (currentMaxRequestRate * 0.75));
            }

            // Update tokens per request
            nanosPerToken = TimeUnit.SECONDS.toNanos(1) / currentMaxRequestRate;

            // Set backoff mode
            inBackoffMode = true;

            // Update throttle timestamp
            lastThrottleTimestampMillis = now;

            LOGGER.info("Rate limit exceeded, entering backoff mode: reduced rate to {}/sec, backoff expires in {}s, repeated={}",
                    currentMaxRequestRate, effectiveRetrySeconds, repeatedBackoff);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimitMetrics getMetrics() {
        lock.readLock().lock();
        try {
            long now = System.currentTimeMillis();
            long backoffRemainingMs = inBackoffMode ?
                    Math.max(0, backoffExpirationTimestampMillis - now) : 0;

            return new RateLimitMetrics(
                    baseRequestRate,
                    currentMaxRequestRate,
                    (int) tokensAvailable,
                    totalRequests.get(),
                    permittedRequests.get(),
                    throttledRequests.get(),
                    TimeUnit.NANOSECONDS.toMillis(totalWaitTimeNanos.get()),
                    inBackoffMode,
                    backoffRemainingMs
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetMetrics() {
        lock.writeLock().lock();
        try {
            totalRequests.set(0);
            permittedRequests.set(0);
            throttledRequests.set(0);
            totalWaitTimeNanos.set(0);
            LOGGER.debug("Rate limiter metrics reset");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        lock.writeLock().lock();
        try {
            // Reset configuration
            currentMaxRequestRate = baseRequestRate;
            nanosPerToken = TimeUnit.SECONDS.toNanos(1) / currentMaxRequestRate;

            // Reset bucket state
            tokensAvailable = baseRequestRate * burstCapacityMultiplier;
            lastRefillTimestampNanos = System.nanoTime();

            // Reset metrics
            totalRequests.set(0);
            permittedRequests.set(0);
            throttledRequests.set(0);
            totalWaitTimeNanos.set(0);

            // Reset backoff state
            inBackoffMode = false;
            lastThrottleTimestampMillis = 0;
            backoffExpirationTimestampMillis = 0;

            LOGGER.info("Rate limiter fully reset to initial state: {}/sec", baseRequestRate);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // Nothing to clean up, this class doesn't use any resources that require explicit cleanup
    }
}