package com.vidigal.code.libretranslate.ratelimit;

/**
 * Configuration and status class for rate limiting.
 */
public class RateLimitConfig {

    private final int maxRequestsPerWindow;
    private final int currentRequestCount;

    public RateLimitConfig(int maxRequestsPerWindow, int currentRequestCount) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.currentRequestCount = currentRequestCount;
    }

    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }



    public int getCurrentRequestCount() {
        return currentRequestCount;
    }

    public double getRemainingRequestCapacity() {
        return Math.max(0, maxRequestsPerWindow - currentRequestCount);
    }

    @Override
    public String toString() {
        return String.format(
                "RateLimitConfig [maxRequests=%d, currentRequests=%d]",
                maxRequestsPerWindow, currentRequestCount
        );
    }
}