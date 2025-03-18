package com.vidigal.code.libretranslate.config;

import com.vidigal.code.libretranslate.exception.TranslationException;

/**
 * Configuration for the LibreTranslate client.
 * Uses the Builder pattern to create immutable configuration instances.
 */
public class LibreTranslateConfig {

    // Default values
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final int DEFAULT_SOCKET_TIMEOUT = 10000;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_RATE_LIMIT_COOLDOWN = 5000;
    private static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 10;
    private static final boolean DEFAULT_ENABLE_RETRY = true;

    // Configuration fields
    private final String apiUrl;
    private final String apiKey;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final int maxRetries;
    private final int rateLimitCooldown;
    private final int maxRequestsPerSecond;
    private final boolean enableRetry;

    /**
     * Private constructor to enforce the use of the Builder pattern.
     *
     * @param builder The Builder instance used to construct this configuration
     */
    private LibreTranslateConfig(Builder builder) {
        this.apiUrl = validateApiUrl(builder.apiUrl);
        this.apiKey = builder.apiKey != null ? builder.apiKey : "";
        this.connectionTimeout = validateTimeout(builder.connectionTimeout, "Connection timeout");
        this.socketTimeout = validateTimeout(builder.socketTimeout, "Socket timeout");
        this.maxRetries = validateNonNegative(builder.maxRetries, "Max retries");
        this.rateLimitCooldown = validateRateLimitCooldown(builder.rateLimitCooldown);
        this.maxRequestsPerSecond = builder.maxRequestsPerSecond;
        this.enableRetry = builder.enableRetry;
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
     * Creates a new configuration builder.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters
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
     * Creates a new configuration with a new rate limit cooldown.
     *
     * @param newCooldown The new cooldown value in milliseconds
     * @return A new configuration instance with updated cooldown
     */
    public LibreTranslateConfig withRateLimitCooldown(int newCooldown) {
        return new Builder(this).rateLimitCooldown(newCooldown).build();
    }

    /**
     * Builder for LibreTranslateConfig.
     */
    public static class Builder {

        private String apiUrl;
        private String apiKey = "";
        private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private int rateLimitCooldown = DEFAULT_RATE_LIMIT_COOLDOWN;
        private int maxRequestsPerSecond = DEFAULT_MAX_REQUESTS_PER_SECOND;
        private boolean enableRetry = DEFAULT_ENABLE_RETRY;

        /**
         * Constructor for creating a new builder.
         */
        public Builder() {
        }

        /**
         * Constructor for creating a builder from an existing configuration.
         *
         * @param config The existing configuration
         */
        public Builder(LibreTranslateConfig config) {
            this.apiUrl = config.apiUrl;
            this.apiKey = config.apiKey;
            this.connectionTimeout = config.connectionTimeout;
            this.socketTimeout = config.socketTimeout;
            this.maxRetries = config.maxRetries;
            this.rateLimitCooldown = config.rateLimitCooldown;
            this.maxRequestsPerSecond = config.maxRequestsPerSecond;
            this.enableRetry = config.enableRetry;
        }

        // Setter methods
        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder socketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder rateLimitCooldown(int rateLimitCooldown) {
            this.rateLimitCooldown = rateLimitCooldown;
            return this;
        }


        public Builder maxRequestsPerSecond(int maxRequestsPerSecond) {
            if (maxRequestsPerSecond < 1 || maxRequestsPerSecond > 1000) {
                throw new TranslationException("Max requests must be between 1-1000");
            }
            this.maxRequestsPerSecond = maxRequestsPerSecond;
            return this;
        }


        public Builder enableRetry(boolean enableRetry) {
            this.enableRetry = enableRetry;
            return this;
        }



        /**
         * Builds a new LibreTranslateConfig instance.
         *
         * @return A new LibreTranslateConfig instance
         */
        public LibreTranslateConfig build() {
            return new LibreTranslateConfig(this);
        }
    }
}