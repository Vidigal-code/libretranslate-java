package com.vidigal.code.libretranslate.ratelimit;

import java.util.concurrent.TimeUnit;

/**
 * Factory for creating rate limiter service instances.
 * <p>
 * This class follows the Factory pattern to provide various ways to create
 * and configure rate limiter instances according to different requirements.
 */
public final class RateLimiterFactory {

    /**
     * Private constructor to prevent instantiation.
     */
    private RateLimiterFactory() {
        throw new AssertionError("Utility class, do not instantiate");
    }

    /**
     * Creates a default rate limiter service with 10 requests per second.
     *
     * @return A newly created rate limiter service
     */
    public static RateLimiterService createDefault() {
        return new RateLimiter(10);
    }

    /**
     * Creates a rate limiter service with the specified requests per second.
     *
     * @param requestsPerSecond The maximum number of requests allowed per second
     * @return A newly created rate limiter service
     * @throws IllegalArgumentException if the requests per second is less than 1
     */
    public static RateLimiterService create(int requestsPerSecond) {
        return new RateLimiter(requestsPerSecond);
    }

    /**
     * Creates a fully customized rate limiter service.
     *
     * @param requestsPerSecond       The maximum number of requests allowed per second
     * @param burstCapacityMultiplier Multiplier for burst capacity (e.g., 2.0 allows 2x bursts)
     * @param maxWaitTimeMs           Maximum time to wait for a permit in milliseconds
     * @return A newly created rate limiter service
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static RateLimiterService create(int requestsPerSecond, double burstCapacityMultiplier, long maxWaitTimeMs) {
        return new RateLimiter(
                requestsPerSecond,
                burstCapacityMultiplier,
                TimeUnit.MILLISECONDS.toNanos(maxWaitTimeMs)
        );
    }

    /**
     * Creates a low-throughput rate limiter suitable for public APIs with strict limits.
     * <p>
     * The rate limiter is configured for 5 requests per second, with a burst capacity
     * of 1.5x and a maximum wait time of 3 seconds.
     *
     * @return A newly created low-throughput rate limiter service
     */
    public static RateLimiterService createLowThroughput() {
        return create(5, 1.5, 3000);
    }

    /**
     * Creates a high-throughput rate limiter suitable for private APIs with high limits.
     * <p>
     * The rate limiter is configured for as many as 100 requests per second, with a burst
     * capacity of 3x and a maximum wait time of 10 seconds.
     *
     * @return A newly created high-throughput rate limiter service
     */
    public static RateLimiterService createHighThroughput() {
        return create(100, 3.0, 10000);
    }
} 