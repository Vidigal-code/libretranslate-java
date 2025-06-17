package com.java.vidigal.code.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe circuit breaker to prevent cascading failures in distributed systems.
 * <p>
 * The circuit breaker operates in three states: {@link State#CLOSED}, {@link State#OPEN}, and
 * {@link State#HALF_OPEN}. In {@code CLOSED}, requests are allowed. After reaching the failure
 * threshold, it transitions to {@code OPEN}, rejecting requests. After a reset timeout, it moves
 * to {@code HALF_OPEN}, allowing a single test request. A successful test resets to {@code CLOSED},
 * while a failure reopens the circuit.
 * </p>
 *
 * @author Vidigal
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final AtomicLong failureCount = new AtomicLong();
    private volatile State state = State.CLOSED;
    private volatile long lastFailureTime;
    /**
     * Creates a circuit breaker with the specified failure threshold and reset timeout.
     *
     * @param threshold the number of consecutive failures to trigger {@code OPEN} state, must be positive
     * @param timeout   the duration to wait in {@code OPEN} before transitioning to {@code HALF_OPEN}, must be non-negative
     * @throws IllegalArgumentException if threshold is not positive or timeout is null/negative
     */
    public CircuitBreaker(int threshold, Duration timeout) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be positive");
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Reset timeout must not be null or negative");
        }
        this.failureThreshold = threshold;
        this.resetTimeout = timeout;
    }

    /**
     * Checks if the circuit breaker is in the {@code OPEN} state.
     * <p>
     * If in {@code OPEN} and the reset timeout has elapsed, transitions to {@code HALF_OPEN}.
     * </p>
     *
     * @return true if the circuit breaker is {@code OPEN}, false otherwise
     */
    public boolean isOpen() {
        if (state == State.OPEN && System.currentTimeMillis() - lastFailureTime > resetTimeout.toMillis()) {
            state = State.HALF_OPEN;
            logger.info("Circuit breaker transitioned to HALF_OPEN after timeout");
        }
        return state == State.OPEN;
    }

    /**
     * Records a successful request.
     * <p>
     * If in {@code HALF_OPEN}, transitions to {@code CLOSED} and resets the failure count.
     * No action is taken in other states.
     * </p>
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount.set(0);
            logger.info("Circuit breaker transitioned to CLOSED after successful request");
        }
    }

    /**
     * Records a failed request.
     * <p>
     * Increments the failure count and transitions to {@code OPEN} if the threshold is reached
     * and the breaker is not already {@code OPEN}. Updates the last failure timestamp.
     * </p>
     */
    public void recordFailure() {
        long failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold && state != State.OPEN) {
            state = State.OPEN;
            lastFailureTime = System.currentTimeMillis();
            logger.warn("Circuit breaker opened after {} failures", failures);
        }
    }

    /**
     * Enum representing the circuit breaker states.
     */
    public enum State {
        /**
         * Allows all requests to pass through.
         */
        CLOSED,
        /**
         * Rejects all requests.
         */
        OPEN,
        /**
         * Allows a single test request to probe system health.
         */
        HALF_OPEN
    }
}
