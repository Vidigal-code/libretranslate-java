package com.vidigal.code.libretranslate.config;

import com.vidigal.code.libretranslate.exception.TranslationException;

/**
 * Builder for creating immutable LibreTranslateConfig instances with a fluent interface.
 *
 * This class allows configuring LibreTranslate client parameters in a flexible and type-safe manner.
 * It follows the Builder design pattern to construct {@link LibreTranslateConfig} objects.
 *
 * <p>Example usage:
 * <pre>
 * LibreTranslateConfig config = LibreTranslateConfig.builder()
 *     .apiUrl("https://libretranslate.com")
 *     .apiKey("your-api-key")
 *     .connectionTimeout(10000)
 *     .maxRequestsPerSecond(5)
 *     .build();
 * </pre>
 *
 * @author Kauan Vidigal
 * @version 1.0
 * @see LibreTranslateConfig
 */
public class Builder {

    private String apiUrl;
    private String apiKey = "";
    private int connectionTimeout = LibreTranslateConfig.DEFAULT_CONNECTION_TIMEOUT;
    private int socketTimeout = LibreTranslateConfig.DEFAULT_SOCKET_TIMEOUT;
    private int maxRetries = LibreTranslateConfig.DEFAULT_MAX_RETRIES;
    private int rateLimitCooldown = LibreTranslateConfig.DEFAULT_RATE_LIMIT_COOLDOWN;
    private int maxRequestsPerSecond = LibreTranslateConfig.DEFAULT_MAX_REQUESTS_PER_SECOND;
    private boolean enableRetry = LibreTranslateConfig.DEFAULT_ENABLE_RETRY;

    /**
     * Creates a new Builder instance with default configuration values.
     */
    public Builder() {
    }


    /**
     * Gets
     */
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
        return maxRequestsPerSecond;
    }

    public boolean isEnableRetry() {
        return enableRetry;
    }

    /**
     * Creates a new Builder instance initialized with an existing configuration.
     *
     * @param config The existing LibreTranslateConfig to copy settings from
     */
    public Builder(LibreTranslateConfig config) {
        this.apiUrl = config.getApiUrl();
        this.apiKey = config.getApiKey();
        this.connectionTimeout = config.getConnectionTimeout();
        this.socketTimeout = config.getSocketTimeout();
        this.maxRetries = config.getMaxRetries();
        this.rateLimitCooldown = config.getRateLimitCooldown();
        this.maxRequestsPerSecond = config.getMaxRequestsPerSecond();
        this.enableRetry = config.isRetryEnabled();
    }

    /**
     * Sets the base URL for the LibreTranslate API.
     *
     * @param apiUrl The full URL of the LibreTranslate API endpoint
     * @return This builder instance for method chaining
     */
    public Builder apiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        return this;
    }

    /**
     * Sets the API key for authentication with the LibreTranslate service.
     *
     * @param apiKey The authentication key for the API
     * @return This builder instance for method chaining
     */
    public Builder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectionTimeout Maximum time to establish a connection
     * @return This builder instance for method chaining
     */
    public Builder connectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Sets the socket timeout in milliseconds.
     *
     * @param socketTimeout Maximum time waiting for data transfer
     * @return This builder instance for method chaining
     */
    public Builder socketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    /**
     * Sets the maximum number of retry attempts for failed requests.
     *
     * @param maxRetries Number of times to retry a failed request
     * @return This builder instance for method chaining
     */
    public Builder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the cooldown time between rate-limited requests.
     *
     * @param rateLimitCooldown Time to wait between requests when rate limit is reached
     * @return This builder instance for method chaining
     */
    public Builder rateLimitCooldown(int rateLimitCooldown) {
        this.rateLimitCooldown = rateLimitCooldown;
        return this;
    }

    /**
     * Sets the maximum number of requests allowed per second.
     *
     * @param maxRequestsPerSecond Number of requests permitted per second
     * @return This builder instance for method chaining
     * @throws TranslationException If requests per second is outside the valid range (1-1000)
     */
    public Builder maxRequestsPerSecond(int maxRequestsPerSecond) {
        if (maxRequestsPerSecond < 1 || maxRequestsPerSecond > 1000) {
            throw new TranslationException("Max requests must be between 1-1000");
        }
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }

    /**
     * Configures whether retry mechanism is enabled for failed requests.
     *
     * @param enableRetry Flag to enable or disable automatic retries
     * @return This builder instance for method chaining
     */
    public Builder enableRetry(boolean enableRetry) {
        this.enableRetry = enableRetry;
        return this;
    }

    /**
     * Constructs a new LibreTranslateConfig instance with the configured parameters.
     *
     * @return A fully configured LibreTranslateConfig object
     * @throws TranslationException If any configuration parameters are invalid
     */
    public LibreTranslateConfig build() {
        return new LibreTranslateConfig(this);
    }
}