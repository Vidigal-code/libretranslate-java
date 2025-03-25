package com.vidigal.code.libretranslate.config;

import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.ratelimit.RateLimiterService;

/**
 * Immutable configuration class for the LibreTranslate client.
 *
 * This class encapsulates all configuration settings for interacting with
 * the LibreTranslate API. It uses the Builder pattern to ensure type-safe
 * and flexible configuration creation.
 *
 * <p>Default values are provided for most configuration parameters to simplify setup:
 * <ul>
 *   <li>Connection Timeout: 5000 ms</li>
 *   <li>Socket Timeout: 10000 ms</li>
 *   <li>Max Retries: 3</li>
 *   <li>Rate Limit Cooldown: 5000 ms</li>
 *   <li>Max Requests per Second: 10</li>
 *   <li>Retry Enabled: true</li>
 * </ul>
 *
 * @author Kauan Vidigal
 * @version 1.0
 * @see Builder
 */
public class LibreTranslateConfig {

    // Default configuration constants
    public static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 10000;
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_RATE_LIMIT_COOLDOWN = 5000;
    public static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 10;
    public static final boolean DEFAULT_ENABLE_RETRY = true;

    // Immutable configuration fields
    private final String apiUrl;
    private final String apiKey;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final int maxRetries;
    private final int rateLimitCooldown;
    private final int maxRequestsPerSecond;
    private final boolean enableRetry;
    
    // Reference to rate limiter instance
    private volatile RateLimiterService rateLimiter;

    /**
     * Private constructor to enforce the use of the Builder pattern.
     *
     * @param builder The Builder instance used to construct this configuration
     * @throws TranslationException If any configuration parameters are invalid
     */
    public LibreTranslateConfig(Builder builder) {
        this.apiUrl = validateApiUrl(builder.getApiUrl());
        this.apiKey = builder.getApiKey() != null ? builder.getApiKey() : "";
        this.connectionTimeout = validateTimeout(builder.getConnectionTimeout(), "Connection timeout");
        this.socketTimeout = validateTimeout(builder.getSocketTimeout(), "Socket timeout");
        this.maxRetries = validateNonNegative(builder.getMaxRetries(), "Max retries");
        this.rateLimitCooldown = validateRateLimitCooldown(builder.getRateLimitCooldown());
        this.maxRequestsPerSecond = builder.getMaxRequestsPerSecond();
        this.enableRetry = builder.isEnableRetry();
        this.rateLimiter = null; // Will be set later by the client
    }

    // Validation methods
    private static String validateApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new TranslationException("API URL cannot be empty");
        }
        return apiUrl;
    }

    private static int validateTimeout(int timeout, String fieldName) {
        if (timeout <= 0) {
            throw new TranslationException(fieldName + " must be positive");
        }
        return timeout;
    }

    private static int validateNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new TranslationException(fieldName + " cannot be negative");
        }
        return value;
    }

    private static int validateRateLimitCooldown(int rateLimitCooldown) {
        if (rateLimitCooldown < 100 || rateLimitCooldown > 60000) {
            throw new TranslationException("Rate limit cooldown must be between 100 and 60000 ms (0.1-60 seconds)");
        }
        return rateLimitCooldown;
    }

    /**
     * Creates a new configuration builder with default settings.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getter methods
    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getRateLimitCooldown() {
        return rateLimitCooldown;
    }

    public int getMaxRequestsPerSecond() {
        return this.maxRequestsPerSecond;
    }

    public boolean isRetryEnabled() {
        return enableRetry;
    }

    /**
     * Gets the rate limiter associated with this configuration.
     * 
     * @return The rate limiter instance, or null if not set
     */
    public RateLimiterService getRateLimiter() {
        return rateLimiter;
    }
    
    /**
     * Sets the rate limiter for this configuration.
     * This method is package-private and intended to be called only by the LibreTranslateClient.
     * 
     * @param rateLimiter The rate limiter to associate with this configuration
     */
    void setRateLimiter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Creates a new configuration with an updated rate limit cooldown.
     *
     * @param newCooldown The new cooldown value in milliseconds
     * @return A new configuration instance with the updated cooldown
     * @throws TranslationException If the new cooldown is outside the valid range
     */
    public LibreTranslateConfig withRateLimitCooldown(int newCooldown) {
        return new Builder(this).rateLimitCooldown(newCooldown).build();
    }
}