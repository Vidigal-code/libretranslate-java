package com.java.vidigal.code.exception;

/**
 * An exception to encapsulate backoff duration for retry operations.
 * <p>
 * Used in asynchronous retry mechanisms to indicate the delay before the next attempt.
 * </p>
 *
 * @author Vidigal
 */
public class BackoffException extends Exception {

    private final long backoff;

    /**
     * Constructs a new backoff exception with the specified duration.
     *
     * @param backoff the backoff duration in milliseconds, must be non-negative
     * @throws IllegalArgumentException if backoff is negative
     */
    public BackoffException(long backoff) {
        if (backoff < 0) {
            throw new IllegalArgumentException("Backoff duration cannot be negative: " + backoff);
        }
        this.backoff = backoff;
    }

    /**
     * Returns the backoff duration.
     *
     * @return the backoff duration in milliseconds
     */
    public long getBackoff() {
        return backoff;
    }
}
