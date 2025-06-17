package com.java.vidigal.code.utilities.config;

import com.java.vidigal.code.client.LibreTranslateClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Configuration class for the LibreTranslate API client, encapsulating settings for API access, rate limiting,
 * retries, caching, and thread management.
 * <p>
 * This immutable class is constructed using the {@link LibreTranslateConfigBuilder} and includes validation
 * to ensure required fields (e.g., API URL, auth key) are properly set. It supports features like
 * persistent caching, automatic thread shutdown, and configurable timeouts. The class uses SLF4J
 * for logging validation errors and is designed for use in the {@link LibreTranslateClientImpl}.
 * </p>
 *
 * @see LibreTranslateConfigBuilder
 * @see LibreTranslateClientImpl
 * @since 1.0
 */
public class LibreTranslateConfig {

    /**
     * Logger for recording configuration validation errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(LibreTranslateConfig.class);

    /**
     * The LibreTranslate API endpoint URL (e.g., "https://translate.fedilab.app/translate").
     */
    private final String apiUrl;

    /**
     * The api key for accessing the LibreTranslate API.
     */
    private final String apiKey;

    /**
     * The connection timeout for HTTP requests, in milliseconds.
     */
    private final int connectionTimeout;

    /**
     * The socket timeout for HTTP requests, in milliseconds.
     */
    private final int socketTimeout;

    /**
     * The maximum number of requests per second allowed by the rate limiter.
     */
    private final int maxRequestsPerSecond;

    /**
     * The maximum number of retry attempts for failed requests.
     */
    private final int maxRetries;

    /**
     * The cooldown period for rate limiting, in milliseconds.
     */
    private final long rateLimitCooldown;

    /**
     * Flag indicating whether retry logic is enabled for transient failures.
     */
    private final boolean retryEnabled;

    /**
     * Strategy for calculating retry delay intervals using exponential backoff.
     */
    private final ExponentialBackoffStrategy retryStrategy;

    /**
     * Flag indicating whether caching of translation responses is enabled.
     */
    private final boolean cacheEnabled;

    /**
     * Flag indicating whether persistent cache storage to disk is enabled.
     */
    private final boolean persistentCacheEnabled;

    /**
     * File path for persistent cache storage, or null if not enabled.
     */
    private final String persistentCachePath;

    /**
     * Flag indicating whether threads should be automatically closed on JVM shutdown.
     */
    private final boolean closedThreadAuto;

    /**
     * Time-to-live for cache entries, in milliseconds; 0 disables TTL.
     */
    private final long cacheTtlMillis;

    /**
     * Interval for cache cleanup, in milliseconds; required if caching is enabled.
     */
    private final long cleanupIntervalMillis;

    /**
     * Flag indicating whether client instances should be tracked for automatic shutdown.
     */
    private final boolean trackInstances;

    /**
     * Constructs a new {@code LibreTranslateConfig} using the provided builder.
     * <p>
     * Initializes all configuration fields and performs validation to ensure required fields are
     * properly set. Validation errors are logged via SLF4J and result in an
     * {@link IllegalArgumentException}.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .apiUrl("https://translate.fedilab.app/translate")
     *     .authKey("your-auth-key")
     *     .enableCache(true)
     *     .build();
     * }</pre>
     * </p>
     *
     * @param builder The builder containing configuration values.
     * @throws IllegalArgumentException if validation fails (e.g., null or empty auth key, invalid timeouts).
     * @since 1.0
     */
    LibreTranslateConfig(LibreTranslateConfigBuilder builder) {
        this.apiUrl = builder.getApiUrl();
        this.apiKey = builder.getApiKey();
        this.connectionTimeout = builder.getConnectionTimeout();
        this.socketTimeout = builder.getSocketTimeout();
        this.maxRequestsPerSecond = builder.getMaxRequestsPerSecond();
        this.maxRetries = builder.getMaxRetries();
        this.rateLimitCooldown = builder.getRateLimitCooldown();
        this.retryEnabled = builder.isRetryEnabled();
        this.cacheEnabled = builder.isCacheEnabled();
        this.persistentCacheEnabled = builder.isPersistentCacheEnabled();
        this.persistentCachePath = builder.getPersistentCachePath();
        this.closedThreadAuto = builder.isClosedThreadAuto();
        this.cacheTtlMillis = builder.getCacheTtlMillis();
        this.cleanupIntervalMillis = builder.getCleanupIntervalMillis();
        this.retryStrategy = builder.getRetryStrategy();
        this.trackInstances = builder.isTrackInstances();
        validate();
    }

    /**
     * Creates a new builder for constructing a {@code LibreTranslateConfig} instance.
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .apiUrl("https://translate.fedilab.app/translate")
     *     .authKey("your-auth-key")
     *     .build();
     * }</pre>
     * </p>
     *
     * @return A new {@link LibreTranslateConfigBuilder} instance.
     * @see LibreTranslateConfigBuilder
     * @since 1.0
     */
    public static LibreTranslateConfigBuilder builder() {
        return new LibreTranslateConfigBuilder();
    }

    /**
     * Creates a {@link LibreTranslateConfig} instance from system properties or environment variables.
     * <p>
     * Loads configuration values with the following precedence:
     * <ol>
     *     <li>System properties (e.g., {@code -Dlibretranslate.api.url=https://translate.fedilab.app/translate})</li>
     *     <li>Environment variables (e.g., {@code LIBRETRANSLATE_API_URL=https://translate.fedilab.app/translate})</li>
     *     <li>Default values from a new {@link LibreTranslateConfigBuilder}</li>
     * </ol>
     * </p>
     * <p>
     * Supported configuration keys:
     * <ul>
     *     <li>{@code libretranslate.api.url}: API endpoint URL</li>
     *     <li>{@code libretranslate.auth.key}: Authentication key</li>
     *     <li>{@code libretranslate.connection.timeout}: Connection timeout in milliseconds</li>
     *     <li>{@code libretranslate.socket.timeout}: Socket timeout in milliseconds</li>
     *     <li>{@code libretranslate.max.requests.per.second}: Maximum requests per second</li>
     *     <li>{@code libretranslate.max.retries}: Maximum retry attempts</li>
     *     <li>{@code libretranslate.rate.limit.cooldown}: Rate limit cooldown in milliseconds</li>
     *     <li>{@code libretranslate.retry.enabled}: Enable retry logic (true/false)</li>
     *     <li>{@code libretranslate.cache.enabled}: Enable caching (true/false)</li>
     *     <li>{@code libretranslate.persistent.cache.enabled}: Enable persistent caching (true/false)</li>
     *     <li>{@code libretranslate.persistent.cache.path}: Persistent cache file path</li>
     *     <li>{@code libretranslate.closed.thread.auto}: Enable auto thread closure (true/false)</li>
     *     <li>{@code libretranslate.cache.ttl.millis}: Cache TTL in milliseconds</li>
     *     <li>{@code libretranslate.cleanup.interval.millis}: Cache cleanup interval in milliseconds</li>
     *     <li>{@code libretranslate.track.instances}: Track client instances (true/false)</li>
     * </ul>
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * // Set system properties
     * System.setProperty("libretranslate.api.url", "https://translate.fedilab.app/translate");
     * System.setProperty("libretranslate.auth.key", "your-auth-key");
     * LibreTranslateConfig config = LibreTranslateConfig.fromProperties();
     * }</pre>
     * </p>
     *
     * @return A configured {@link LibreTranslateConfig} instance.
     * @throws IllegalArgumentException if required properties are missing or invalid.
     * @see LibreTranslateConfigBuilder
     * @since 1.0
     */
    public static LibreTranslateConfig fromProperties() {
        LibreTranslateConfigBuilder builder = LibreTranslateConfig.builder();

        // Helper method to get property or environment variable
        Function<String, String> getProperty = key -> {
            String prop = System.getProperty(key);
            if (prop != null) return prop;
            String envKey = key.replace(".", "_").toUpperCase();
            return System.getenv(envKey);
        };

        // Load properties
        String apiUrl = getProperty.apply("libretranslate.api.url");
        if (apiUrl != null) builder.apiUrl(apiUrl);

        String authKey = getProperty.apply("libretranslate.auth.key");
        if (authKey != null) builder.apiKey(authKey);

        String connTimeout = getProperty.apply("libretranslate.connection.timeout");
        if (connTimeout != null) builder.connectionTimeout(Integer.parseInt(connTimeout));

        String socketTimeout = getProperty.apply("libretranslate.socket.timeout");
        if (socketTimeout != null) builder.socketTimeout(Integer.parseInt(socketTimeout));

        String maxReqPerSec = getProperty.apply("libretranslate.max.requests.per.second");
        if (maxReqPerSec != null) builder.maxRequestsPerSecond(Integer.parseInt(maxReqPerSec));

        String maxRetries = getProperty.apply("libretranslate.max.retries");
        if (maxRetries != null) builder.maxRetries(Integer.parseInt(maxRetries));

        String rateLimitCooldown = getProperty.apply("libretranslate.rate.limit.cooldown");
        if (rateLimitCooldown != null) builder.rateLimitCooldown(Long.parseLong(rateLimitCooldown));

        String retryEnabled = getProperty.apply("libretranslate.retry.enabled");
        if (retryEnabled != null) builder.enableRetry(Boolean.parseBoolean(retryEnabled));

        String cacheEnabled = getProperty.apply("libretranslate.cache.enabled");
        if (cacheEnabled != null) builder.enableCache(Boolean.parseBoolean(cacheEnabled));

        String persistentCacheEnabled = getProperty.apply("libretranslate.persistent.cache.enabled");
        String persistentCachePath = getProperty.apply("libretranslate.persistent.cache.path");
        if (persistentCacheEnabled != null) {
            builder.enablePersistentCache(Boolean.parseBoolean(persistentCacheEnabled), persistentCachePath);
        }

        String closedThreadAuto = getProperty.apply("libretranslate.closed.thread.auto");
        if (closedThreadAuto != null) builder.closedThreadAuto(Boolean.parseBoolean(closedThreadAuto));

        String cacheTtlMillis = getProperty.apply("libretranslate.cache.ttl.millis");
        if (cacheTtlMillis != null) builder.cacheTtlMillis(Long.parseLong(cacheTtlMillis));

        String cleanupIntervalMillis = getProperty.apply("libretranslate.cleanup.interval.millis");
        if (cleanupIntervalMillis != null) builder.cleanupIntervalMillis(Long.parseLong(cleanupIntervalMillis));

        String trackInstances = getProperty.apply("libretranslate.track.instances");
        if (trackInstances != null) builder.trackInstances(Boolean.parseBoolean(trackInstances));

        return builder.build();
    }

    /**
     * Validates the configuration fields.
     * <p>
     * Ensures that required fields (e.g., auth key, API URL) are non-null and non-empty, and that
     * numerical values (e.g., max requests, timeouts) are valid. Logs errors via SLF4J and throws
     * an {@link IllegalArgumentException} if validation fails.
     * </p>
     *
     * @throws IllegalArgumentException if any validation check fails.
     * @since 1.0
     */
    private void validate() {
        if (retryEnabled && retryStrategy == null) {
            logger.error("Retry strategy must be specified when retries are enabled");
            throw new IllegalArgumentException("Retry strategy must be specified when retries are enabled");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            logger.error("LibreTranslate API URL is null or empty");
            throw new IllegalArgumentException("LibreTranslate API URL cannot be null or empty");
        }
        if (maxRequestsPerSecond <= 0) {
            logger.error("Max requests per second must be positive");
            throw new IllegalArgumentException("Max requests per second must be positive");
        }
        if (cacheTtlMillis < 0) {
            logger.error("Cache TTL must be non-negative");
            throw new IllegalArgumentException("Cache TTL must be non-negative");
        }
        if (persistentCacheEnabled && (persistentCachePath == null || persistentCachePath.isBlank())) {
            logger.error("Persistent cache path must be specified");
            throw new IllegalArgumentException("Persistent cache path cannot be null or empty");
        }
        if (cacheEnabled && cleanupIntervalMillis <= 0) {
            logger.error("Cleanup interval must be positive when cache is enabled");
            throw new IllegalArgumentException("Cleanup interval must be positive");
        }
    }

    /**
     * Checks if client instances should be tracked for automatic shutdown.
     *
     * @return {@code true} if instance tracking is enabled; {@code false} otherwise.
     * @since 1.0
     */
    public boolean isTrackInstances() {
        return trackInstances;
    }

    /**
     * Returns the LibreTranslate API endpoint URL.
     *
     * @return The API endpoint URL (e.g., "https://translate.fedilab.app/translate").
     * @since 1.0
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * Returns the認証 key for accessing the LibreTranslate API.
     *
     * @return The authentication key.
     * @since 1.0
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the connection timeout for HTTP requests.
     *
     * @return The connection timeout in milliseconds.
     * @since 1.0
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Returns the socket timeout for HTTP requests.
     *
     * @return The socket timeout in milliseconds.
     * @since 1.0
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Returns the maximum number of requests per second allowed by the rate limiter.
     *
     * @return The maximum requests per second.
     * @since 1.0
     */
    public int getMaxRequestsPerSecond() {
        return maxRequestsPerSecond;
    }

    /**
     * Returns the maximum number of retry attempts for failed requests.
     *
     * @return The maximum retry attempts.
     * @since 1.0
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the cooldown period for rate limiting.
     *
     * @return The rate limit cooldown in milliseconds.
     * @since 1.0
     */
    public long getRateLimitCooldown() {
        return rateLimitCooldown;
    }

    /**
     * Checks if retry logic is enabled for transient failures.
     *
     * @return {@code true} if retry logic is enabled; {@code false} otherwise.
     * @since 1.0
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    /**
     * Returns the retry strategy used when retry is enabled.
     *
     * @return The {@link ExponentialBackoffStrategy} for retry delays, or {@code null} if retries are disabled.
     * @see ExponentialBackoffStrategy
     * @since 1.0
     */
    public ExponentialBackoffStrategy getRetryStrategy() {
        return retryStrategy;
    }

    /**
     * Checks if response caching is enabled.
     *
     * @return {@code true} if caching is enabled; {@code false} otherwise.
     * @since 1.0
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Checks if persistent disk caching is enabled.
     *
     * @return {@code true} if persistent caching is enabled; {@code false} otherwise.
     * @since 1.0
     */
    public boolean isPersistentCacheEnabled() {
        return persistentCacheEnabled;
    }

    /**
     * Returns the file path for persistent cache storage.
     *
     * @return The persistent cache file path, or {@code null} if not specified.
     * @since 1.0
     */
    public String getPersistentCachePath() {
        return persistentCachePath;
    }

    /**
     * Checks if threads should be automatically closed on JVM shutdown.
     *
     * @return {@code true} if automatic thread closure is enabled; {@code false} otherwise.
     * @since 1.0
     */
    public boolean isClosedThreadAuto() {
        return closedThreadAuto;
    }

    /**
     * Returns the time-to-live for cache entries.
     *
     * @return The cache TTL in milliseconds; 0 disables TTL.
     * @since 1.0
     */
    public long getCacheTtlMillis() {
        return cacheTtlMillis;
    }

    /**
     * Returns the interval for performing cache cleanup.
     *
     * @return The cache cleanup interval in milliseconds.
     * @since 1.0
     */
    public long getCleanupIntervalMillis() {
        return cleanupIntervalMillis;
    }
}
