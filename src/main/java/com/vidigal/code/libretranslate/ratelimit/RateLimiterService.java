package com.vidigal.code.libretranslate.ratelimit;

/**
 * Interface for rate limiting service.
 * <p>
 * This interface defines the contract for a service that manages rate limiting
 * and throttling for API requests. It follows the Interface Segregation Principle
 * by focusing only on the core rate limiting operations.
 */
public interface RateLimiterService extends AutoCloseable {

    /**
     * Acquires a permit from this rate limiter, blocking if necessary.
     * 
     * @return true if a permit was acquired, false if the wait time would exceed the maximum
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    boolean acquire() throws InterruptedException;
    
    /**
     * Notifies the rate limiter that a rate limit has been exceeded by the API.
     * This allows the limiter to adapt its behavior.
     * 
     * @param retryAfterSeconds Suggested retry time from the API (HTTP 429 Retry-After header)
     */
    void notifyRateLimitExceeded(int retryAfterSeconds);
    
    /**
     * Gets the current metrics for this rate limiter.
     * 
     * @return A snapshot of the current rate limiter metrics
     */
    RateLimitMetrics getMetrics();
    
    /**
     * Resets all metrics counters for this rate limiter.
     */
    void resetMetrics();
    
    /**
     * Resets the entire rate limiter state to its initial configuration.
     */
    void reset();
} 