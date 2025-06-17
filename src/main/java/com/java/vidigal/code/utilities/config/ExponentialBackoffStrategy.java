package com.java.vidigal.code.utilities.config;

/**
 * A strategy class for calculating delay times using exponential backoff.
 * <p>
 * Exponential backoff is commonly used in retry mechanisms to prevent overwhelming a system by
 * spacing out retry attempts after failures. The delay for each attempt is calculated as
 * {@code baseDelayMillis * multiplier^(attempt-1)}, capped at {@code maxDelayMillis}.
 * </p>
 * <p>
 * Example:
 * <pre>{@code
 * ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(100, 2.0, 10000);
 * long delay = strategy.getNextDelay(3); // Returns 400 ms (100 * 2.0^(3-1))
 * }</pre>
 * </p>
 *
 * @author Vidigal
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
     * @param baseDelayMillis the initial delay in milliseconds before retrying, must be positive
     * @param multiplier      the factor by which the delay increases per attempt, must be greater than 1.0
     * @param maxDelayMillis  the maximum delay allowed in milliseconds, must be positive and at least baseDelayMillis
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ExponentialBackoffStrategy(long baseDelayMillis, double multiplier, long maxDelayMillis) {
        if (baseDelayMillis <= 0) {
            throw new IllegalArgumentException("Base delay must be positive");
        }
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("Multiplier must be greater than 1.0");
        }
        if (maxDelayMillis <= 0) {
            throw new IllegalArgumentException("Maximum delay must be positive");
        }
        if (maxDelayMillis < baseDelayMillis) {
            throw new IllegalArgumentException("Maximum delay must be at least equal to base delay");
        }
        this.baseDelayMillis = baseDelayMillis;
        this.multiplier = multiplier;
        this.maxDelayMillis = maxDelayMillis;
    }

    /**
     * Calculates the next delay based on the retry attempt number.
     *
     * @param attempt the current attempt number, starting from 1
     * @return the calculated delay in milliseconds, capped at the maximum delay
     * @throws IllegalArgumentException if attempt is not positive
     */
    @Override
    public long getNextDelay(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        return Math.min((long) (baseDelayMillis * Math.pow(multiplier, (double) attempt - 1)), maxDelayMillis);
    }
}