package com.java.vidigal.code.utilities.config;

/**
 * Interface for defining a custom retry strategy.
 */
public interface RetryStrategy {
    /**
     * Calculates the delay before the next retry attempt.
     *
     * @param attempt The current retry attempt number (1-based).
     * @return The delay in milliseconds.
     */
    long getNextDelay(int attempt);
}

