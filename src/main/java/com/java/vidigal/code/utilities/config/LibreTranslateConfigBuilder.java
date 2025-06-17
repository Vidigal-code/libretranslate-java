package com.java.vidigal.code.utilities.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A builder class for creating {@link LibreTranslateConfig} instances with validated configuration settings.
 * <p>
 * This class provides a fluent API to configure the LibreTranslate API client, including API access, timeouts,
 * rate limiting, retries, caching, and thread management. It enforces validation constraints (e.g.,
 * minimum/maximum timeouts, non-empty API URL) and logs errors using SLF4J. The builder pattern
 * ensures that {@link LibreTranslateConfig} instances are immutable and properly initialized.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * LibreTranslateConfig config = LibreTranslateConfig.builder()
 *     .apiUrl("https://translate.fedilab.app/translate")
 *     .authKey("your-auth-key")
 *     .connectionTimeout(5000)
 *     .maxRetries(3)
 *     .enableCache(true)
 *     .build();
 * }</pre>
 *
 * @author Vidigal
 * @version 1.0
 * @see LibreTranslateConfig
 * @see ExponentialBackoffStrategy
 * @since 1.0
 */
public class LibreTranslateConfigBuilder {

    /**
     * Logger for recording validation errors during configuration building.
     */
    private static final Logger logger = LoggerFactory.getLogger(LibreTranslateConfigBuilder.class);
    /**
     * Minimum allowed connection timeout in milliseconds (100ms).
     */
    private static final int MIN_CONNECTION_TIMEOUT = 100;
    /**
     * Maximum allowed connection timeout in milliseconds (30,000ms).
     */
    private static final int MAX_CONNECTION_TIMEOUT = 30_000;
    /**
     * Minimum allowed socket timeout in milliseconds (100ms).
     */
    private static final int MIN_SOCKET_TIMEOUT = 100;
    /**
     * Maximum allowed socket timeout in milliseconds (60,000ms).
     */
    private static final int MAX_SOCKET_TIMEOUT = 60_000;
    /**
     * Minimum allowed requests per second for rate limiting (1).
     */
    private static final int MIN_REQUESTS_PER_SECOND = 1;
    /**
     * Maximum allowed requests per second for rate limiting (100).
     */
    private static final int MAX_REQUESTS_PER_SECOND = 100;
    /**
     * Minimum allowed retry attempts (0).
     */
    private static final int MIN_RETRIES = 0;
    /**
     * Maximum allowed retry attempts (5).
     */
    private static final int MAX_RETRIES = 5;
    /**
     * Minimum allowed rate limit cooldown in milliseconds (0ms).
     */
    private static final long MIN_COOLDOWN = 0;
    /**
     * Maximum allowed rate limit cooldown in milliseconds (60,000ms).
     */
    private static final long MAX_COOLDOWN = 60_000;
    /**
     * Minimum allowed cache time-to-live in milliseconds (0ms, meaning no expiration).
     */
    private static final long MIN_CACHE_TTL = 0;
    /**
     * Minimum allowed cache cleanup interval in milliseconds (1,000ms).
     */
    private static final long MIN_CLEANUP_INTERVAL = 1000;
    /**
     * The retry strategy used for handling failed requests with exponential backoff.
     * Initialized with default values: initial delay of 1000ms, multiplier of 2.0, and max delay of 30,000ms.
     */
    private ExponentialBackoffStrategy retryStrategy = new ExponentialBackoffStrategy(1000, 2.0, 30_000);
    /**
     * The LibreTranslate API endpoint URL (e.g., "https://translate.fedilab.app/translate").
     */
    private String apiUrl;

    /**
     * The authentication key for accessing the LibreTranslate API.
     */
    private String apiKey;

    /**
     * The connection timeout for HTTP requests, in milliseconds (default: 5,000ms).
     */
    private int connectionTimeout = 5000;

    /**
     * The socket timeout for HTTP requests, in milliseconds (default: 10,000ms).
     */
    private int socketTimeout = 10000;

    /**
     * The maximum number of requests per second allowed (default: 10).
     */
    private int maxRequestsPerSecond = 10;

    /**
     * The maximum number of retry attempts for failed requests (default: 3).
     */
    private int maxRetries = 3;

    /**
     * The cooldown period for rate limiting, in milliseconds (default: 5,000ms).
     */
    private long rateLimitCooldown = 5000;

    /**
     * Flag indicating whether retry logic is enabled (default: true).
     */
    private boolean retryEnabled = true;

    /**
     * Flag indicating whether caching is enabled (default: false).
     */
    private boolean cacheEnabled = false;

    /**
     * Flag indicating whether persistent cache storage is enabled (default: false).
     */
    private boolean persistentCacheEnabled = false;

    /**
     * File path for persistent cache storage.
     */
    private String persistentCachePath;

    /**
     * Flag indicating whether threads should auto-close on JVM shutdown (default: false).
     */
    private boolean closedThreadAuto = false;

    /**
     * Time-to-live for cache entries, in milliseconds (default: 3,600,000ms or 1 hour).
     */
    private long cacheTtlMillis = 3_600_000;

    /**
     * Interval for cache cleanup, in milliseconds (default: 1,800,000ms or 30 minutes).
     */
    private long cleanupIntervalMillis = 1_800_000;

    /**
     * Flag indicating whether client instances should be tracked for automatic shutdown (default: false).
     */
    private boolean trackInstances = false;

    /**
     * Creates a new instance of {@code LibreTranslateConfigBuilder} with default configuration values.
     * <p>
     * Default values include:
     * <ul>
     *     <li>Connection timeout: 5,000ms</li>
     *     <li>Socket timeout: 10,000ms</li>
     *     <li>Max requests per second: 10</li>
     *     <li>Max retries: 3</li>
     *     <li>Rate limit cooldown: 5,000ms</li>
     *     <li>Retry enabled: true</li>
     *     <li>Cache enabled: false</li>
     *     <li>Persistent cache enabled: false</li>
     *     <li>Cache TTL: 3,600,000ms (1 hour)</li>
     *     <li>Cache cleanup interval: 1,800,000ms (30 minutes)</li>
     *     <li>Track instances: false</li>
     *     <li>Retry strategy: {@link ExponentialBackoffStrategy} with initial delay 1,000ms, multiplier 2.0, max delay 30,000ms</li>
     * </ul>
     * </p>
     *
     * @since 1.0
     */
    public LibreTranslateConfigBuilder() {
        // Default constructor with initialized default values
    }

    /**
     * Enables or disables tracking of client instances for automatic shutdown.
     * <p>
     * When enabled, client instances are tracked in a static collection, allowing automatic resource cleanup
     * during JVM shutdown. This is useful for standalone applications but should be used cautiously in
     * managed environments (e.g., Spring, Jakarta EE) to avoid memory leaks.
     * </p>
     *
     * @param trackInstances {@code true} to enable instance tracking; {@code false} to disable.
     * @since 1.0
     */
    public void trackInstances(boolean trackInstances) {
        this.trackInstances = trackInstances;
    }

    /**
     * Sets the retry strategy for handling failed requests with exponential backoff.
     * <p>
     * The retry strategy defines the delay between retry attempts, including the initial delay,
     * multiplier for subsequent retries, and maximum delay to prevent excessive waiting.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .retryStrategy(new ExponentialBackoffStrategy(500, 1.5, 10_000))
     *     .build();
     * }</pre>
     * </p>
     *
     * @param retryStrategy The exponential backoff strategy to use; must not be null.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code retryStrategy} is null.
     * @see ExponentialBackoffStrategy
     * @since 1.0
     */
    public LibreTranslateConfigBuilder retryStrategy(ExponentialBackoffStrategy retryStrategy) {
        if (retryStrategy == null) {
            logger.error("Retry strategy cannot be null");
            throw new IllegalArgumentException("Retry strategy cannot be null");
        }
        this.retryStrategy = retryStrategy;
        return this;
    }

    /**
     * Sets the LibreTranslate API endpoint URL.
     * <p>
     * This is typically the free or pro LibreTranslate API endpoint:
     * <ul>
     *     <li>API: {@code https://translate.fedilab.app/translate}</li>
     * </ul>
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .apiUrl("https://translate.fedilab.app/translate")
     *     .build();
     * }</pre>
     * </p>
     *
     * @param apiUrl The API URL; must not be null or blank.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code apiUrl} is null or blank.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder apiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            logger.error("API URL is null or empty");
            throw new IllegalArgumentException("API URL cannot be null or blank");
        }
        this.apiUrl = apiUrl;
        return this;
    }

    /**
     * Sets the authentication key for the LibreTranslate API.
     * <p>
     * This is the API key provided by LibreTranslate for accessing their translation services.
     * It should be kept secure and not exposed in logs or version control.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .apiKey("your-auth-key")
     *     .build();
     * }</pre>
     * </p>
     *
     * @param apiKey The authentication key; must not be null or blank.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code authKey} is null or blank.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder apiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("Auth key is null or empty");
            throw new IllegalArgumentException("Auth key cannot be null or blank");
        }
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Sets the connection timeout for HTTP requests.
     * <p>
     * This timeout specifies how long to wait when establishing a connection to the LibreTranslate API.
     * A shorter timeout fails faster but may cause issues with slow networks, while a longer
     * timeout provides more reliability but may delay error detection.
     * </p>
     *
     * @param connectionTimeout The timeout in milliseconds, between {@value #MIN_CONNECTION_TIMEOUT} and {@value #MAX_CONNECTION_TIMEOUT}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code connectionTimeout} is out of the valid range.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder connectionTimeout(int connectionTimeout) {
        if (connectionTimeout < MIN_CONNECTION_TIMEOUT || connectionTimeout > MAX_CONNECTION_TIMEOUT) {
            logger.error("Connection timeout out of range: {}", connectionTimeout);
            throw new IllegalArgumentException(
                    String.format("Connection timeout must be between %d and %d ms", MIN_CONNECTION_TIMEOUT, MAX_CONNECTION_TIMEOUT));
        }
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Sets the socket timeout for HTTP requests.
     * <p>
     * This timeout specifies how long to wait for data after a connection is established.
     * It helps handle cases where the server accepts a connection but is slow to respond.
     * </p>
     *
     * @param socketTimeout The timeout in milliseconds, between {@value #MIN_SOCKET_TIMEOUT} and {@value #MAX_SOCKET_TIMEOUT}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code socketTimeout} is out of the valid range.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder socketTimeout(int socketTimeout) {
        if (socketTimeout < MIN_SOCKET_TIMEOUT || socketTimeout > MAX_SOCKET_TIMEOUT) {
            logger.error("Socket timeout out of range: {}", socketTimeout);
            throw new IllegalArgumentException(
                    String.format("Socket timeout must be between %d and %d ms", MIN_SOCKET_TIMEOUT, MAX_SOCKET_TIMEOUT));
        }
        this.socketTimeout = socketTimeout;
        return this;
    }

    /**
     * Sets the maximum number of requests per second for rate limiting.
     * <p>
     * This setting controls how frequently requests can be sent to the LibreTranslate API to avoid
     * exceeding rate limits, which could result in HTTP 429 (Too Many Requests) errors.
     * </p>
     *
     * @param maxRequestsPerSecond The maximum requests per second, between {@value #MIN_REQUESTS_PER_SECOND} and {@value #MAX_REQUESTS_PER_SECOND}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code maxRequestsPerSecond} is out of the valid range.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder maxRequestsPerSecond(int maxRequestsPerSecond) {
        if (maxRequestsPerSecond < MIN_REQUESTS_PER_SECOND || maxRequestsPerSecond > MAX_REQUESTS_PER_SECOND) {
            logger.error("Max requests per second out of range: {}", maxRequestsPerSecond);
            throw new IllegalArgumentException(
                    String.format("Max requests per second must be between %d and %d", MIN_REQUESTS_PER_SECOND, MAX_REQUESTS_PER_SECOND));
        }
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }

    /**
     * Sets the maximum number of retry attempts for failed requests.
     * <p>
     * When a request fails due to transient errors (e.g., network issues, server errors),
     * the client retries up to this many times using the configured exponential backoff strategy.
     * </p>
     *
     * @param maxRetries The maximum retry attempts, between {@value #MIN_RETRIES} and {@value #MAX_RETRIES}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code maxRetries} is out of the valid range.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder maxRetries(int maxRetries) {
        if (maxRetries < MIN_RETRIES || maxRetries > MAX_RETRIES) {
            logger.error("Max retries out of range: {}", maxRetries);
            throw new IllegalArgumentException(
                    String.format("Max retries must be between %d and %d", MIN_RETRIES, MAX_RETRIES));
        }
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the cooldown period for rate limiting.
     * <p>
     * When the rate limit is exceeded, the client waits for this duration before making additional
     * requests, helping to prevent continuous rate limit violations.
     * </p>
     *
     * @param rateLimitCooldown The cooldown period in milliseconds, between {@value #MIN_COOLDOWN} and {@value #MAX_COOLDOWN}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code rateLimitCooldown} is out of the valid range.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder rateLimitCooldown(long rateLimitCooldown) {
        if (rateLimitCooldown < MIN_COOLDOWN || rateLimitCooldown > MAX_COOLDOWN) {
            logger.error("Rate limit cooldown out of range: {}", rateLimitCooldown);
            throw new IllegalArgumentException(
                    String.format("Rate limit cooldown must be between %d and %d ms", MIN_COOLDOWN, MAX_COOLDOWN));
        }
        this.rateLimitCooldown = rateLimitCooldown;
        return this;
    }

    /**
     * Enables or disables retry logic for transient failures.
     * <p>
     * When enabled, the client automatically retries failed requests using the configured
     * exponential backoff strategy. When disabled, requests fail immediately without retries.
     * </p>
     *
     * @param retryEnabled {@code true} to enable retries; {@code false} to disable.
     * @return This builder instance for method chaining.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder enableRetry(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
        return this;
    }

    /**
     * Enables or disables caching of translation responses.
     * <p>
     * When enabled, translation results are cached in memory to avoid redundant API calls for
     * identical requests, improving performance and reducing API usage.
     * </p>
     *
     * @param cacheEnabled {@code true} to enable caching; {@code false} to disable.
     * @return This builder instance for method chaining.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder enableCache(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        return this;
    }

    /**
     * Enables or disables persistent cache storage and sets the cache file path.
     * <p>
     * When enabled, the cache is persisted to disk, allowing cached translations to survive
     * application restarts. This is useful for long-running applications or maintaining cache
     * across sessions.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * LibreTranslateConfig config = LibreTranslateConfig.builder()
     *     .enablePersistentCache(true, "/path/to/cache.db")
     *     .build();
     * }</pre>
     * </p>
     *
     * @param persistentCacheEnabled {@code true} to enable persistent caching; {@code false} to disable.
     * @param persistentCachePath    The file path for persistent cache storage; must not be null or blank if enabled.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code persistentCachePath} is null or blank when {@code persistentCacheEnabled} is true.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder enablePersistentCache(boolean persistentCacheEnabled, String persistentCachePath) {
        this.persistentCacheEnabled = persistentCacheEnabled;
        if (persistentCacheEnabled && (persistentCachePath == null || persistentCachePath.isBlank())) {
            logger.error("Persistent cache path is null or empty");
            throw new IllegalArgumentException("Persistent cache path cannot be null or empty");
        }
        this.persistentCachePath = persistentCachePath;
        return this;
    }

    /**
     * Enables or disables automatic thread closure on JVM shutdown.
     * <p>
     * When enabled, background threads (e.g., cache cleanup threads) are automatically shut down
     * during JVM shutdown, ensuring clean application termination and preventing thread leaks.
     * </p>
     *
     * @param closedThreadAuto {@code true} to enable auto-closure; {@code false} to disable.
     * @return This builder instance for method chaining.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder closedThreadAuto(boolean closedThreadAuto) {
        this.closedThreadAuto = closedThreadAuto;
        return this;
    }

    /**
     * Sets the time-to-live for cache entries.
     * <p>
     * This determines how long cached translation results remain valid before being considered
     * stale and removed. A value of 0 means cache entries do not expire based on time.
     * </p>
     *
     * @param cacheTtlMillis The TTL in milliseconds; must be non-negative.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code cacheTtlMillis} is negative.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder cacheTtlMillis(long cacheTtlMillis) {
        if (cacheTtlMillis < MIN_CACHE_TTL) {
            logger.error("Cache TTL is negative: {}", cacheTtlMillis);
            throw new IllegalArgumentException("Cache TTL must be non-negative");
        }
        this.cacheTtlMillis = cacheTtlMillis;
        return this;
    }

    /**
     * Checks if client instance tracking is enabled.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return {@code true} if instance tracking is enabled; {@code false} otherwise.
     * @since 1.0
     */
    boolean isTrackInstances() {
        return trackInstances;
    }

    /**
     * Sets the interval for cache cleanup operations.
     * <p>
     * The cache cleanup process runs periodically to remove expired entries and free memory.
     * A shorter interval keeps the cache leaner but uses more CPU, while a longer interval
     * reduces CPU usage but may allow expired entries to accumulate.
     * </p>
     *
     * @param cleanupIntervalMillis The cleanup interval in milliseconds; must be at least {@value #MIN_CLEANUP_INTERVAL}.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if {@code cleanupIntervalMillis} is less than {@value #MIN_CLEANUP_INTERVAL}.
     * @since 1.0
     */
    public LibreTranslateConfigBuilder cleanupIntervalMillis(long cleanupIntervalMillis) {
        if (cleanupIntervalMillis < MIN_CLEANUP_INTERVAL) {
            logger.error("Cleanup interval too low: {}", cleanupIntervalMillis);
            throw new IllegalArgumentException("Cleanup interval must be at least " + MIN_CLEANUP_INTERVAL + " ms");
        }
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        return this;
    }

    /**
     * Returns the configured retry strategy.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The {@link ExponentialBackoffStrategy} for retry delays.
     * @since 1.0
     */
    ExponentialBackoffStrategy getRetryStrategy() {
        return retryStrategy;
    }

    /**
     * Builds a new {@link LibreTranslateConfig} instance with the configured settings.
     * <p>
     * This method creates an immutable configuration object. The builder can be reused after
     * calling this method to create additional configurations. Validation is performed by
     * the {@link LibreTranslateConfig} constructor.
     * </p>
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
     * @return A new {@link LibreTranslateConfig} instance with the current configuration.
     * @throws IllegalStateException if required configuration values (e.g., {@code apiUrl}, {@code authKey}) are not set.
     * @see LibreTranslateConfig
     * @since 1.0
     */
    public LibreTranslateConfig build() {
        return new LibreTranslateConfig(this);
    }

    /**
     * Returns the configured API URL.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The API URL, or {@code null} if not set.
     * @since 1.0
     */
    String getApiUrl() {
        return apiUrl;
    }

    /**
     * Returns the configured authentication key.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The authentication key, or {@code null} if not set.
     * @since 1.0
     */
    String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the configured connection timeout.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The connection timeout in milliseconds.
     * @since 1.0
     */
    int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Returns the configured socket timeout.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The socket timeout in milliseconds.
     * @since 1.0
     */
    int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Returns the configured maximum requests per second.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The maximum requests per second.
     * @since 1.0
     */
    int getMaxRequestsPerSecond() {
        return maxRequestsPerSecond;
    }

    /**
     * Returns the configured maximum retry attempts.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The maximum retry attempts.
     * @since 1.0
     */
    int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the configured rate limit cooldown period.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The rate limit cooldown period in milliseconds.
     * @since 1.0
     */
    long getRateLimitCooldown() {
        return rateLimitCooldown;
    }

    /**
     * Indicates whether retry logic is enabled.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return {@code true} if retry logic is enabled; {@code false} otherwise.
     * @since 1.0
     */
    boolean isRetryEnabled() {
        return retryEnabled;
    }

    /**
     * Indicates whether caching is enabled.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return {@code true} if caching is enabled; {@code false} otherwise.
     * @since 1.0
     */
    boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Indicates whether persistent cache storage is enabled.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return {@code true} if persistent caching is enabled; {@code false} otherwise.
     * @since 1.0
     */
    boolean isPersistentCacheEnabled() {
        return persistentCacheEnabled;
    }

    /**
     * Returns the configured persistent cache file path.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The persistent cache file path, or {@code null} if not set.
     * @since 1.0
     */
    String getPersistentCachePath() {
        return persistentCachePath;
    }

    /**
     * Indicates whether automatic thread closure is enabled.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return {@code true} if automatic thread closure is enabled; {@code false} otherwise.
     * @since 1.0
     */
    boolean isClosedThreadAuto() {
        return closedThreadAuto;
    }

    /**
     * Returns the configured cache time-to-live.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The cache TTL in milliseconds.
     * @since 1.0
     */
    long getCacheTtlMillis() {
        return cacheTtlMillis;
    }

    /**
     * Returns the configured cache cleanup interval.
     * <p>
     * This method is package-private and used internally by the {@link LibreTranslateConfig} class
     * during construction.
     * </p>
     *
     * @return The cache cleanup interval in milliseconds.
     * @since 1.0
     */
    long getCleanupIntervalMillis() {
        return cleanupIntervalMillis;
    }
}
