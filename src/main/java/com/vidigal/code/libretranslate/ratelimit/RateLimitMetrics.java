package com.vidigal.code.libretranslate.ratelimit;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable class that contains metrics and state information for a rate limiter.
 * <p>
 * This class provides a snapshot of rate limiter performance at a point in time.
 */
public class RateLimitMetrics {
    private final int baseRequestRate;
    private final int currentRequestRate;
    private final int availableTokens;
    private final long totalRequests;
    private final long permittedRequests;
    private final long throttledRequests;
    private final long totalWaitTimeMs;
    private final boolean inBackoffMode;
    private final long backoffRemainingMs;

    /**
     * Creates a new metrics snapshot for a rate limiter.
     *
     * @param baseRequestRate    The configured base request rate per second
     * @param currentRequestRate The current active request rate per second
     * @param availableTokens    The number of available tokens in the bucket
     * @param totalRequests      Total number of requests attempted
     * @param permittedRequests  Number of requests permitted
     * @param throttledRequests  Number of requests throttled
     * @param totalWaitTimeMs    Total time spent waiting for permits in milliseconds
     * @param inBackoffMode      Whether the rate limiter is currently in backoff mode
     * @param backoffRemainingMs Time remaining in current backoff in milliseconds
     */
    public RateLimitMetrics(
            int baseRequestRate,
            int currentRequestRate,
            int availableTokens,
            long totalRequests,
            long permittedRequests,
            long throttledRequests,
            long totalWaitTimeMs,
            boolean inBackoffMode,
            long backoffRemainingMs) {
        this.baseRequestRate = baseRequestRate;
        this.currentRequestRate = currentRequestRate;
        this.availableTokens = availableTokens;
        this.totalRequests = totalRequests;
        this.permittedRequests = permittedRequests;
        this.throttledRequests = throttledRequests;
        this.totalWaitTimeMs = totalWaitTimeMs;
        this.inBackoffMode = inBackoffMode;
        this.backoffRemainingMs = backoffRemainingMs;
    }

    /**
     * @return The configured base request rate per second
     */
    public int getBaseRequestRate() {
        return baseRequestRate;
    }

    /**
     * @return The current active request rate per second
     */
    public int getCurrentRequestRate() {
        return currentRequestRate;
    }

    /**
     * @return The number of available tokens in the bucket
     */
    public int getAvailableTokens() {
        return availableTokens;
    }

    /**
     * @return Total number of requests attempted
     */
    public long getTotalRequests() {
        return totalRequests;
    }

    /**
     * @return Number of requests permitted
     */
    public long getPermittedRequests() {
        return permittedRequests;
    }

    /**
     * @return Number of requests throttled
     */
    public long getThrottledRequests() {
        return throttledRequests;
    }

    /**
     * @return Total time spent waiting for permits in milliseconds
     */
    public long getTotalWaitTimeMs() {
        return totalWaitTimeMs;
    }

    /**
     * @return Whether the rate limiter is currently in backoff mode
     */
    public boolean isInBackoffMode() {
        return inBackoffMode;
    }

    /**
     * @return Time remaining in current backoff in milliseconds
     */
    public long getBackoffRemainingMs() {
        return backoffRemainingMs;
    }

    /**
     * @return The success rate (0.0-1.0) representing the ratio of permitted to total requests
     */
    public double getSuccessRate() {
        return totalRequests > 0 ? (double) permittedRequests / totalRequests : 1.0;
    }

    /**
     * @return Average waiting time per request in milliseconds
     */
    public double getAverageWaitTimeMs() {
        return permittedRequests > 0 ? (double) totalWaitTimeMs / permittedRequests : 0.0;
    }

    /**
     * @return The current throttling percentage (0.0-100.0)
     */
    public double getThrottlingPercentage() {
        return totalRequests > 0 ? (double) throttledRequests / totalRequests * 100.0 : 0.0;
    }

    /**
     * @return All metrics as a key-value map
     */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("baseRequestRate", baseRequestRate);
        map.put("currentRequestRate", currentRequestRate);
        map.put("availableTokens", availableTokens);
        map.put("totalRequests", totalRequests);
        map.put("permittedRequests", permittedRequests);
        map.put("throttledRequests", throttledRequests);
        map.put("totalWaitTimeMs", totalWaitTimeMs);
        map.put("inBackoffMode", inBackoffMode);
        map.put("backoffRemainingMs", backoffRemainingMs);
        map.put("successRate", getSuccessRate());
        map.put("averageWaitTimeMs", getAverageWaitTimeMs());
        map.put("throttlingPercentage", getThrottlingPercentage());
        return map;
    }

    @Override
    public String toString() {
        return String.format(
                "RateLimitMetrics[rate=%d/%d, tokens=%d, success=%.1f%%, throttled=%.1f%%, backoff=%b]",
                currentRequestRate, baseRequestRate, availableTokens,
                getSuccessRate() * 100.0, getThrottlingPercentage(),
                inBackoffMode
        );
    }
} 