package com.java.vidigal.code.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.vidigal.code.exception.LibreTranslateApiException;
import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.language.Language;
import com.java.vidigal.code.request.Translation;
import com.java.vidigal.code.request.TranslationRequest;
import com.java.vidigal.code.request.TranslationResponse;
import com.java.vidigal.code.utilities.cache.TranslationCache;
import com.java.vidigal.code.utilities.config.LibreTranslateConfig;
import com.java.vidigal.code.utilities.config.RetryStrategy;
import com.java.vidigal.code.utilities.ratelimit.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A robust implementation of {@link LibreTranslateClient} for interacting with the LibreTranslate translation API.
 * <p>
 * This thread-safe class supports synchronous and asynchronous translations with features such as:
 * <ul>
 *     <li>Caching via {@link TranslationCache} for improved performance.</li>
 *     <li>Rate limiting with {@link TokenBucketRateLimiter} to respect API quotas.</li>
 *     <li>Retry logic for transient failures using exponential backoff.</li>
 *     <li>Circuit breaker pattern to prevent cascading failures.</li>
 *     <li>Monitoring statistics for operational insights.</li>
 * </ul>
 * It uses {@link HttpClient} for HTTP communication, Jackson for JSON processing, and SLF4J for logging.
 * Implements {@link Closeable} for graceful resource cleanup.
 * </p>
 *
 * @author Vidigal
 * @version 1.0
 * @since 1.0
 */
public class LibreTranslateClientImpl implements LibreTranslateClient, Closeable {

    /** Logger for this class */
    private static final Logger logger = LoggerFactory.getLogger(LibreTranslateClientImpl.class);

    /** Thread-safe set to track all active instances for proper shutdown handling */
    private static final Set<LibreTranslateClientImpl> INSTANCES = ConcurrentHashMap.newKeySet();

    /** Flag to ensure shutdown hook is registered only once */
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /** Error message constant for async translation failures */
    private static final String ASYNC_TRANSLATION_FAILED = "Async translation failed";

    /** HTTP client for making API requests */
    private final HttpClient httpClient;

    /** Jackson ObjectMapper for JSON serialization/deserialization */
    private final ObjectMapper objectMapper;

    /** Rate limiter to control API request frequency */
    private final TokenBucketRateLimiter rateLimiter;

    /** Virtual thread executor for async operations */
    private final ExecutorService virtualThreadExecutor;

    /** Counter for successful translations */
    private final AtomicLong successCount = new AtomicLong();

    /** Counter for failed translations */
    private final AtomicLong failureCount = new AtomicLong();

    /** Total latency accumulator in nanoseconds */
    private final AtomicLong totalLatencyNanos = new AtomicLong();

    /** Map tracking different types of errors and their counts */
    private final Map<String, AtomicLong> errorTypeCounts = new ConcurrentHashMap<>();

    /** Circuit breaker to prevent cascading failures */
    private final CircuitBreaker circuitBreaker;

    /** Atomic reference to the current configuration */
    private final AtomicReference<LibreTranslateConfig> config = new AtomicReference<>();

    /** Translation cache for improved performance (nullable) */
    private TranslationCache cache;

    /**
     * Constructs a client with the specified configuration.
     * <p>
     * Creates a default HTTP client with HTTP/2 support and connection timeout
     * based on the provided configuration. Also initializes a default ObjectMapper
     * for JSON processing.
     * </p>
     * <p>
     * <strong>Warning:</strong> If {@link LibreTranslateConfig#isTrackInstances()} is enabled, instances are
     * tracked in a static collection, which may cause memory leaks in managed environments (e.g., Spring,
     * Jakarta EE) during application redeployment. Use with caution and prefer container-managed lifecycle
     * (e.g., {@code @PreDestroy}) in such environments.
     * </p>
     *
     * @param config the client configuration, must not be null
     * @throws IllegalArgumentException if config is null
     */
    public LibreTranslateClientImpl(LibreTranslateConfig config) {
        this(config, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
                .build(), new ObjectMapper());
    }

    /**
     * Constructs a client with injected dependencies for testing.
     * <p>
     * This constructor allows dependency injection for testing purposes, enabling
     * mocking of HTTP client and ObjectMapper behaviors. It initializes all
     * components including rate limiter, cache, virtual thread executor, and
     * circuit breaker based on the provided configuration.
     * </p>
     * <p>
     * <strong>Warning:</strong> If {@link LibreTranslateConfig#isTrackInstances()} is enabled, instances are
     * tracked in a static collection, which may cause memory leaks in managed environments (e.g., Spring,
     * Jakarta EE) during application redeployment. Use with caution and prefer container-managed lifecycle
     * (e.g., {@code @PreDestroy}) in such environments.
     * </p>
     *
     * @param config       the client configuration, must not be null
     * @param httpClient   the HTTP client for API communication
     * @param objectMapper the JSON mapper for serialization
     * @throws IllegalArgumentException if config is null
     */
    public LibreTranslateClientImpl(LibreTranslateConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.config.set(config);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = new TokenBucketRateLimiter(
                config.getMaxRequestsPerSecond(),
                config.getRateLimitCooldown(),
                config.isCacheEnabled()
        );
        this.cache = config.isCacheEnabled() ? new TranslationCache(
                config.getCacheTtlMillis(),
                1000,
                config.isPersistentCacheEnabled(),
                config.getPersistentCachePath()
        ) : null;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.circuitBreaker = new CircuitBreaker(5, Duration.ofSeconds(30));
        if (config.isTrackInstances()) {
            INSTANCES.add(this);
        }
        if (config.isClosedThreadAuto() && config.isTrackInstances()) {
            registerShutdownHook();
        }
    }

    /**
     * Reloads the client configuration dynamically.
     * <p>
     * Updates rate limiter and cache settings atomically. Creates a new cache if enabled,
     * or clears and removes it if disabled. This method is thread-safe and allows for
     * runtime configuration changes without recreating the client instance.
     * </p>
     * <p>
     * If persistent cache is disabled in the new configuration, the persistent cache file
     * will be deleted from the filesystem.
     * </p>
     *
     * @param newConfig the new configuration, must not be null
     * @throws IllegalArgumentException if newConfig is null
     * @throws LibreTranslateException if persistent cache file deletion fails
     */
    public void reloadConfig(LibreTranslateConfig newConfig) throws LibreTranslateException {
        if (newConfig == null) {
            logger.error("New configuration cannot be null");
            throw new IllegalArgumentException("New configuration cannot be null");
        }
        synchronized (this) {
            this.config.set(newConfig);
            this.rateLimiter.update(newConfig.getMaxRequestsPerSecond(), newConfig.getRateLimitCooldown());
            if (newConfig.isCacheEnabled() && this.cache == null) {
                this.cache = new TranslationCache(
                        newConfig.getCacheTtlMillis(),
                        1000,
                        newConfig.isPersistentCacheEnabled(),
                        newConfig.getPersistentCachePath()
                );
            } else if (!newConfig.isCacheEnabled() && this.cache != null) {
                if (newConfig.isPersistentCacheEnabled()) {
                    try {
                        Files.deleteIfExists(Path.of(newConfig.getPersistentCachePath()));
                    } catch (IOException e) {
                        logger.error("Failed to delete persistent cache file", e);
                        throw new LibreTranslateException("Failed to delete persistent cache", e);
                    }
                }
                this.cache.clear();
                this.cache = null;
            }
        }
    }

    /**
     * Performs synchronous translation of the provided request.
     * <p>
     * This method implements the complete translation flow including:
     * <ul>
     *     <li>Circuit breaker check to prevent requests during service failures</li>
     *     <li>Cache lookup for previously translated content</li>
     *     <li>Rate limiting to respect API quotas</li>
     *     <li>Retry logic with exponential backoff for transient failures</li>
     *     <li>Monitoring statistics updates</li>
     * </ul>
     * </p>
     *
     * @param request the translation request containing text and language information
     * @return the translation response with translated text and detected source language
     * @throws LibreTranslateException if translation fails or service is unavailable
     */
    @Override
    public TranslationResponse translate(TranslationRequest request) throws LibreTranslateException {
        if (circuitBreaker.isOpen()) {
            logger.error("Circuit breaker is open, rejecting request");
            throw new LibreTranslateException("Service temporarily unavailable");
        }
        try {
            if (config.get().isCacheEnabled()) {
                TranslationResponse cached = cache.get(
                        String.join("", request.getTextSegments()),
                        request.getTargetLang(),
                        request.getSourceLang()
                );
                if (cached != null) {
                    logger.debug("Cache hit for request: {}", request.getTargetLang());
                    circuitBreaker.recordSuccess();
                    return cached;
                }
            }
            TranslationResponse response = executeWithRetry(() -> {
                rateLimiter.acquire();
                return sendRequest(request);
            });
            circuitBreaker.recordSuccess();
            return response;
        } catch (InterruptedException e) {
            logger.error("Translation interrupted", e);
            Thread.currentThread().interrupt();
            circuitBreaker.recordFailure();
            failureCount.incrementAndGet();
            incrementErrorCount("InterruptedException");
            throw new LibreTranslateException("Translation interrupted", e);
        } catch (Exception e) {
            logger.error("Translation failed", e);
            circuitBreaker.recordFailure();
            failureCount.incrementAndGet();
            incrementErrorCount(e.getClass().getSimpleName());
            throw new LibreTranslateException("Translation failed", e);
        }
    }

    /**
     * Performs asynchronous translation of the provided request.
     * <p>
     * This method returns immediately with a CompletableFuture that will be completed
     * when the translation is finished. It implements the same features as the synchronous
     * version but in a non-blocking manner using virtual threads.
     * </p>
     * <p>
     * The method includes circuit breaker protection and cache lookup before attempting
     * the actual API call. Failed futures will contain LibreTranslateException as the cause.
     * </p>
     *
     * @param request the translation request containing text and language information
     * @return a CompletableFuture that will complete with the translation response
     */
    @Override
    public CompletableFuture<TranslationResponse> translateAsync(TranslationRequest request) {
        if (circuitBreaker.isOpen()) {
            logger.error("Circuit breaker is open, rejecting async request");
            return CompletableFuture.failedFuture(new LibreTranslateException("Service temporarily unavailable"));
        }
        if (config.get().isCacheEnabled()) {
            TranslationResponse cached = cache.get(
                    String.join("", request.getTextSegments()),
                    request.getTargetLang(),
                    request.getSourceLang()
            );
            if (cached != null) {
                logger.debug("Async cache hit for request: {}", request.getTargetLang());
                circuitBreaker.recordSuccess();
                return CompletableFuture.completedFuture(cached);
            }
        }
        return executeWithAsyncRetry(request, 0);
    }

    /**
     * Sends the actual HTTP request to the LibreTranslate API.
     * <p>
     * This method handles the low-level HTTP communication including:
     * <ul>
     *     <li>Request body construction with proper JSON format</li>
     *     <li>HTTP headers setup including Content-Type and User-Agent</li>
     *     <li>Response parsing and error handling</li>
     *     <li>Cache storage of successful translations</li>
     *     <li>Latency measurement and statistics updates</li>
     * </ul>
     * </p>
     *
     * @param request the translation request to send
     * @return the parsed translation response
     * @throws Exception if the HTTP request fails, API returns an error, or JSON parsing fails
     */
    private TranslationResponse sendRequest(TranslationRequest request) throws Exception {
        long startTime = System.nanoTime();
        try {
            Map<String, Object> requestBody = new ConcurrentHashMap<>();
            requestBody.put("q", request.getTextSegments());
            requestBody.put("source", request.getSourceLang() != null ?
                    request.getSourceLang().toLowerCase() :
                    Language.AUTO.getCode().toLowerCase());
            requestBody.put("target", request.getTargetLang().toLowerCase());
            requestBody.put("format", "text");

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.get().getApiUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "LibreTranslateClient/1.0")
                    .timeout(Duration.ofMillis(config.get().getSocketTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errorType = "HTTP_" + response.statusCode();
                incrementErrorCount(errorType);
                logger.error("LibreTranslate API request failed with status: {}, response body: {}", response.statusCode(), response.body());
                throw new LibreTranslateApiException("LibreTranslate API request failed with status: " + response.statusCode() + ", response: " + response.body(), response.statusCode());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
            });
            Object translatedTextObj = responseBody.get("translatedText");

            String detectedSourceLang = extractDetectedLanguage(responseBody.get("detectedLanguage"));

            logger.debug("Response body structure: {}", responseBody);
            logger.debug("DetectedLanguage extracted: {}", detectedSourceLang);

            List<Translation> translations;
            if (translatedTextObj instanceof List<?> translatedList) {
                translations = translatedList.stream()
                        .filter(String.class::isInstance)
                        .map(text -> new Translation((String) text, detectedSourceLang))
                        .collect(Collectors.toList());
            } else if (translatedTextObj instanceof String translatedText) {
                translations = List.of(new Translation(translatedText, detectedSourceLang));
            } else {
                logger.warn("Unexpected translatedText type: {}", translatedTextObj != null ? translatedTextObj.getClass() : "null");
                translations = List.of(new Translation("", detectedSourceLang));
            }

            TranslationResponse translationResponse = new TranslationResponse(translations);

            if (config.get().isCacheEnabled()) {
                cache.put(
                        String.join("", request.getTextSegments()),
                        request.getTargetLang(),
                        request.getSourceLang(),
                        translationResponse
                );
            }

            successCount.incrementAndGet();
            totalLatencyNanos.addAndGet(System.nanoTime() - startTime);
            return translationResponse;
        } catch (Exception e) {
            incrementErrorCount(e.getClass().getSimpleName());
            logger.error("Failed to send translation request", e);
            totalLatencyNanos.addAndGet(System.nanoTime() - startTime);
            throw e;
        }
    }

    /**
     * Extracts the detected source language from the API response.
     * <p>
     * The LibreTranslate API may return detected language information in various formats:
     * <ul>
     *     <li>As a Map with a "language" key</li>
     *     <li>As a List of Maps containing language information</li>
     *     <li>As a simple String</li>
     *     <li>As null if no detection was performed</li>
     * </ul>
     * This method handles all these cases robustly.
     * </p>
     *
     * @param detectedLanguageObj the detected language object from the API response
     * @return the detected language code as a String, or null if not available
     * @throws LibreTranslateException if parsing the detected language fails
     */
    private String extractDetectedLanguage(Object detectedLanguageObj) throws LibreTranslateException {
        if (detectedLanguageObj == null) {
            return null;
        }

        try {
            if (detectedLanguageObj instanceof Map<?, ?> detectedLanguageMap) {
                Object languageValue = detectedLanguageMap.get("language");
                return languageValue instanceof String ? (String) languageValue : null;
            }

            if (detectedLanguageObj instanceof List<?> detectedLanguageList && !detectedLanguageList.isEmpty()) {
                Object firstElement = detectedLanguageList.get(0);
                if (firstElement instanceof Map<?, ?> firstLanguageMap) {
                    Object languageValue = firstLanguageMap.get("language");
                    return languageValue instanceof String ? (String) languageValue : null;
                } else if (firstElement instanceof String) {
                    return (String) firstElement;
                }
            }

            if (detectedLanguageObj instanceof String) {
                return (String) detectedLanguageObj;
            }

            logger.warn("Unsupported detectedLanguage format: {} with value: {}",
                    detectedLanguageObj.getClass().getSimpleName(), detectedLanguageObj);
            return null;

        } catch (Exception e) {
            logger.error("Error parsing detected language: {}", e.getMessage(), e);
            throw new LibreTranslateException("Error parsing detected language", e);
        }
    }

    /**
     * Executes a supplier with retry logic for synchronous operations.
     * <p>
     * This method implements exponential backoff retry strategy for transient failures.
     * It only retries on specific HTTP status codes (429, 500, 502, 503) and IO exceptions.
     * Non-retryable errors are immediately propagated to the caller.
     * </p>
     *
     * @param <T> the return type of the supplier
     * @param supplier the operation to execute with retry logic
     * @return the result of the successful operation
     * @throws Exception if all retry attempts fail or a non-retryable error occurs
     */
    private <T> T executeWithRetry(SupplierWithException<T> supplier) throws Exception {
        if (!config.get().isRetryEnabled()) {
            return supplier.get();
        }
        int attempts = 0;
        Exception lastException = null;
        Set<Integer> retryableStatusCodes = Set.of(429, 500, 502, 503);
        RetryStrategy retryStrategy = config.get().getRetryStrategy();
        while (attempts <= config.get().getMaxRetries()) {
            try {
                return supplier.get();
            } catch (LibreTranslateApiException e) {
                if (!retryableStatusCodes.contains(e.getStatusCode())) {
                    logger.error("Non-retryable API error: {}", e.getStatusCode());
                    throw e;
                }
                lastException = e;
            } catch (IOException e) {
                lastException = e;
                logger.error("IO error during request", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LibreTranslateException("Retry execution interrupted", e);
            } catch (Exception e) {
                logger.error("Non-retryable error", e);
                throw e;
            }
            attempts++;
            if (attempts <= config.get().getMaxRetries()) {
                long backoff = retryStrategy.getNextDelay(attempts);
                logger.warn("Retry attempt {}/{} after {}ms", attempts, config.get().getMaxRetries(), backoff);
                Thread.sleep(backoff);
            }
        }
        if (lastException != null) {
            logger.error("All retries failed", lastException);
            circuitBreaker.recordFailure();
            failureCount.incrementAndGet();
            incrementErrorCount(lastException.getClass().getSimpleName());
            throw lastException;
        }
        throw new LibreTranslateException("Retry execution failed");
    }

    /**
     * Executes asynchronous retry logic for translation requests.
     * <p>
     * This method implements retry logic for async operations using CompletableFuture.
     * It uses delayed execution for backoff timing and recursively calls itself
     * for retry attempts. The method handles both retryable and non-retryable errors
     * appropriately.
     * </p>
     *
     * @param request the translation request to retry
     * @param attempt the current retry attempt number (0-based)
     * @return a CompletableFuture that will complete with the translation response or error
     */
    private CompletableFuture<TranslationResponse> executeWithAsyncRetry(TranslationRequest request, int attempt) {
        if (!config.get().isRetryEnabled() || attempt > config.get().getMaxRetries()) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    rateLimiter.acquire();
                    return sendRequest(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new LibreTranslateException(ASYNC_TRANSLATION_FAILED, e));
                } catch (Exception e) {
                    throw new CompletionException(new LibreTranslateException(ASYNC_TRANSLATION_FAILED, e));
                }
            }, virtualThreadExecutor).thenApply(response -> {
                circuitBreaker.recordSuccess();
                return response;
            }).exceptionallyCompose(throwable -> {
                circuitBreaker.recordFailure();
                failureCount.incrementAndGet();
                incrementErrorCount(throwable.getCause().getClass().getSimpleName());
                logger.error(ASYNC_TRANSLATION_FAILED, throwable);
                return CompletableFuture.failedFuture(new LibreTranslateException(ASYNC_TRANSLATION_FAILED, throwable.getCause()));
            });
        }

        Set<Integer> retryableStatusCodes = Set.of(429, 500, 502, 503);
        RetryStrategy retryStrategy = config.get().getRetryStrategy();

        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
                return sendRequest(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(new LibreTranslateException("Async retry execution interrupted", e));
            } catch (Exception e) {
                if (e instanceof LibreTranslateApiException apiEx && !retryableStatusCodes.contains(apiEx.getStatusCode())) {
                    logger.error("Non-retryable API error: {}", apiEx.getStatusCode());
                    throw new CompletionException(new LibreTranslateException("Non-retryable API error: " + apiEx.getStatusCode(), e));
                } else if (!(e instanceof IOException)) {
                    logger.error("Non-retryable error: {}", e);
                    throw new CompletionException(new LibreTranslateException("Non-retryable error", e));
                }
                logger.error("Retry execution failed", e);
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor).thenApply(response -> {
            circuitBreaker.recordSuccess();
            return response;
        }).exceptionallyCompose(throwable -> {
            if (attempt < config.get().getMaxRetries()) {
                long backoff = retryStrategy.getNextDelay(attempt + 1);
                logger.warn("Async retry attempt {}/{} after {}ms", attempt + 1, config.get().getMaxRetries(), backoff);
                return CompletableFuture.runAsync(() -> {
                        }, CompletableFuture.delayedExecutor(backoff, TimeUnit.MILLISECONDS, virtualThreadExecutor))
                        .thenCompose(v -> executeWithAsyncRetry(request, attempt + 1));
            }
            circuitBreaker.recordFailure();
            failureCount.incrementAndGet();
            incrementErrorCount(throwable.getCause().getClass().getSimpleName());
            logger.error(ASYNC_TRANSLATION_FAILED, throwable);
            return CompletableFuture.failedFuture(new LibreTranslateException(ASYNC_TRANSLATION_FAILED, throwable.getCause()));
        });
    }

    /**
     * Returns the translation cache instance.
     * <p>
     * The cache may be null if caching is disabled in the configuration.
     * This method provides access to the cache for external monitoring or
     * management purposes.
     * </p>
     *
     * @return the translation cache, or null if caching is disabled
     */
    public TranslationCache getCache() {
        return cache;
    }

    /**
     * Retrieves comprehensive monitoring statistics for this client instance.
     * <p>
     * The statistics include:
     * <ul>
     *     <li>Cache performance metrics (hits, misses, hit ratio)</li>
     *     <li>Rate limiter statistics</li>
     *     <li>Success and failure counts</li>
     *     <li>Average latency measurements</li>
     *     <li>Error type breakdown</li>
     * </ul>
     * </p>
     *
     * @return monitoring statistics snapshot
     */
    public MonitoringStats getMonitoringStats() {
        CacheMonitoringStats cacheStats = cache != null ? new CacheMonitoringStats(
                cache.getCacheHits(),
                cache.getCacheMisses(),
                cache.getCacheSize(),
                cache.getCacheHitRatio(),
                cache.getAllCache()
        ) : null;
        long totalRequests = successCount.get() + failureCount.get();
        double avgLatencyMillis = totalRequests == 0 ? 0 : totalLatencyNanos.get() / (totalRequests * 1_000_000.0);
        Map<String, Long> errorCountsSnapshot = new ConcurrentHashMap<>();
        errorTypeCounts.forEach((key, value) -> errorCountsSnapshot.put(key, value.get()));
        return new MonitoringStats(
                cacheStats,
                rateLimiter.getStats(),
                successCount.get(),
                failureCount.get(),
                avgLatencyMillis,
                errorCountsSnapshot
        );
    }

    /**
     * Closes this client and releases all resources.
     * <p>
     * This method implements the Closeable interface and performs graceful shutdown
     * of all resources including the virtual thread executor. It also removes this
     * instance from the tracked instances set to prevent memory leaks.
     * </p>
     * <p>
     * After calling this method, the client should not be used for further operations.
     * </p>
     */
    @Override
    public void close() {
        shutdown();
        INSTANCES.remove(this);
    }

    /**
     * Shuts down the client's resources gracefully.
     * <p>
     * This method attempts to shutdown the virtual thread executor gracefully
     * by waiting up to 60 seconds for running tasks to complete. If tasks don't
     * complete within this timeout, it forces shutdown. This method handles
     * InterruptedException appropriately by preserving the interrupt status.
     * </p>
     */
    public void shutdown() {
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
                logger.warn("Executor did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted", e);
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Registers a JVM shutdown hook to ensure proper cleanup of all client instances.
     * <p>
     * This method is called automatically when instance tracking is enabled.
     * The shutdown hook ensures that all tracked instances are properly shut down
     * when the JVM terminates, preventing resource leaks.
     * </p>
     * <p>
     * The shutdown hook is registered only once, regardless of how many client
     * instances are created with tracking enabled.
     * </p>
     */
    private void registerShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                INSTANCES.forEach(LibreTranslateClientImpl::shutdown);
                INSTANCES.clear();
            }, "LibreTranslateClientImpl-Shutdown-Hook"));
        }
    }

    /**
     * Increments the error count for a specific error type.
     * <p>
     * This method is used internally to track different types of errors
     * for monitoring and debugging purposes. It uses atomic operations
     * to ensure thread-safety when updating error counts.
     * </p>
     *
     * @param errorType the type of error to increment (e.g., "IOException", "HTTP_500")
     */
    private void incrementErrorCount(String errorType) {
        errorTypeCounts.computeIfAbsent(errorType, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Functional interface for operations that may throw exceptions.
     * <p>
     * This interface is used internally for retry logic, allowing methods
     * that throw checked exceptions to be used with the retry mechanism.
     * </p>
     *
     * @param <T> the return type of the supplier
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        /**
         * Gets a result, potentially throwing an exception.
         *
         * @return the result
         * @throws Exception if the operation fails
         */
        T get() throws Exception;
    }

    /**
     * Record containing comprehensive monitoring statistics for the client.
     * <p>
     * This immutable record provides a snapshot of the client's performance
     * and operational metrics at a specific point in time. It includes
     * cache statistics, rate limiter information, success/failure counts,
     * latency measurements, and error breakdowns.
     * </p>
     *
     * @param cacheStats cache-related statistics, null if caching is disabled
     * @param rateLimiterStats rate limiter performance statistics
     * @param successCount total number of successful translation requests
     * @param failureCount total number of failed translation requests
     * @param averageLatencyMillis average request latency in milliseconds
     * @param errorTypeCounts breakdown of error types and their occurrence counts
     */
    public record MonitoringStats(
            CacheMonitoringStats cacheStats,
            TokenBucketRateLimiter.RateLimiterStats rateLimiterStats,
            long successCount,
            long failureCount,
            double averageLatencyMillis,
            Map<String, Long> errorTypeCounts
    ) {
    }

    /**
     * Record containing cache-specific monitoring statistics.
     * <p>
     * This immutable record provides detailed information about cache
     * performance including hit/miss ratios, current cache size, and
     * access to individual cache entries for debugging purposes.
     * </p>
     *
     * @param cacheHits total number of cache hits
     * @param cacheMisses total number of cache misses
     * @param cacheSize current number of entries in the cache
     * @param cacheHitRatio ratio of hits to total cache requests (0.0 to 1.0)
     * @param cacheEntries list of all current cache entries for inspection
     */
    public record CacheMonitoringStats(
            long cacheHits,
            long cacheMisses,
            int cacheSize,
            double cacheHitRatio,
            List<TranslationCache.CacheEntry> cacheEntries
    ) {
    }
}