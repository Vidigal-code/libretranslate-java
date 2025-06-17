package com.java.vidigal.code.utilities.config;

/**
 * A strategy class for calculating delay times using exponential backoff.
 * <p>
 * Exponential backoff is commonly used in retry mechanisms to prevent
 * overwhelming a system by spacing out retry attempts after failures.
 * </p>
 * <p>
 * Example:
 * <pre>{@code
 * ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(100, 2.0, 10000);
 * long delay = strategy.getNextDelay(3); // Will return 400 ms
 * }</pre>
 *
 * @author
 */
public class ExponentialBackoffStrategy implements RetryStrategy {

    /**
     * The initial base delay in milliseconds.
     */
    private final long baseDelayMillis;

    /**
     * The multiplier applied to the delay at each retry attempt.
     */
    private final double multiplier;

    /**
     * The maximum delay allowed in milliseconds.
     */
    private final long maxDelayMillis;

    /**
     * Constructs an {@code ExponentialBackoffStrategy} with specified parameters.
     *
     * @param baseDelayMillis The initial delay in milliseconds before retrying.
     *                        Must be greater than 0.
     * @param multiplier      The factor by which the delay increases after each attempt.
     *                        Must be greater than 1.0.
     * @param maxDelayMillis  The maximum delay allowed in milliseconds.
     *                        Must be greater than 0.
     * @throws IllegalArgumentException if any of the parameters are invalid.
     */
    public ExponentialBackoffStrategy(long baseDelayMillis, double multiplier, long maxDelayMillis) {
        if (baseDelayMillis <= 0 || multiplier <= 1.0 || maxDelayMillis <= 0) {
            throw new IllegalArgumentException("Invalid backoff parameters");
        }
        this.baseDelayMillis = baseDelayMillis;
        this.multiplier = multiplier;
        this.maxDelayMillis = maxDelayMillis;
    }

    /**
     * Calculates the next delay based on the retry attempt number.
     *
     * @param attempt The current attempt number (starting from 1).
     * @return The calculated delay in milliseconds, capped at the maximum delay.
     */
    public long getNextDelay(int attempt) {
        return Math.min((long) (baseDelayMillis * Math.pow(multiplier, attempt - 1)), maxDelayMillis);
    }
}

