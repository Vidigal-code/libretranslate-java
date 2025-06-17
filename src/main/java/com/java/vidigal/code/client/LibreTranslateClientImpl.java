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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
 */
public class LibreTranslateClientImpl implements LibreTranslateClient, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(LibreTranslateClientImpl.class);
    private static final Set<LibreTranslateClientImpl> INSTANCES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenBucketRateLimiter rateLimiter;
    private final ExecutorService virtualThreadExecutor;
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final Map<String, AtomicLong> errorTypeCounts = new ConcurrentHashMap<>();
    private final CircuitBreaker circuitBreaker;
    private volatile LibreTranslateConfig config;
    private TranslationCache cache;

    /**
     * Constructs a client with the specified configuration.
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
        this.config = config;
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
     * or clears and removes it if disabled.
     * </p>
     *
     * @param newConfig the new configuration, must not be null
     * @throws IllegalArgumentException if newConfig is null
     */
    public void reloadConfig(LibreTranslateConfig newConfig) {
        if (newConfig == null) {
            logger.error("New configuration cannot be null");
            throw new IllegalArgumentException("New configuration cannot be null");
        }
        synchronized (this) {
            this.config = newConfig;
            this.rateLimiter.update(newConfig.getMaxRequestsPerSecond(), newConfig.getRateLimitCooldown());
            if (newConfig.isCacheEnabled() && this.cache == null) {
                this.cache = new TranslationCache(
                        newConfig.getCacheTtlMillis(),
                        1000,
                        newConfig.isPersistentCacheEnabled(),
                        newConfig.getPersistentCachePath()
                );
            } else if (!newConfig.isCacheEnabled() && this.cache != null) {
                this.cache.clear();
                this.cache = null;
            }
        }
    }

    @Override
    public TranslationResponse translate(TranslationRequest request) throws LibreTranslateException {
        if (circuitBreaker.isOpen()) {
            logger.error("Circuit breaker is open, rejecting request");
            throw new LibreTranslateException("Service temporarily unavailable");
        }
        try {
            if (config.isCacheEnabled()) {
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

    @Override
    public CompletableFuture<TranslationResponse> translateAsync(TranslationRequest request) {
        if (circuitBreaker.isOpen()) {
            logger.error("Circuit breaker is open, rejecting async request");
            return CompletableFuture.failedFuture(new LibreTranslateException("Service temporarily unavailable"));
        }
        if (config.isCacheEnabled()) {
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
     * Sends an HTTP request to the LibreTranslate API and processes the response.
     * <p>
     * This method handles the complete request lifecycle including:
     * <ul>
     *     <li>Building the HTTP request with proper headers and JSON payload</li>
     *     <li>Sending the request and handling HTTP status codes</li>
     *     <li>Parsing the JSON response with flexible format support</li>
     *     <li>Extracting translated text and detected language information</li>
     *     <li>Caching successful translations if enabled</li>
     *     <li>Recording performance metrics and error statistics</li>
     * </ul>
     * </p>
     * <p>
     * The method is designed to handle various LibreTranslate API response formats,
     * including different structures for the {@code detectedLanguage} field which
     * may be returned as a Map, List, or String depending on the API version.
     * </p>
     *
     * @param request the translation request containing source text, target language,
     *                and optional source language specification
     * @return a {@link TranslationResponse} containing the translated text and
     * detected source language information
     * @throws LibreTranslateApiException if the API returns an error status code
     * @throws IOException                if network communication fails
     * @throws Exception                  if JSON parsing fails or other unexpected errors occur
     * @see TranslationRequest
     * @see TranslationResponse
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
                    .uri(URI.create(config.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "LibreTranslateClient/1.0")
                    .timeout(Duration.ofMillis(config.getSocketTimeout()))
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

            // Enhanced null-safe detection language parsing with robust format handling
            String detectedSourceLang = extractDetectedLanguage(responseBody.get("detectedLanguage"));

            logger.debug("Response body structure: {}", responseBody);
            logger.debug("DetectedLanguage extracted: {}", detectedSourceLang);

            // Process translated text with type safety and fallback handling
            List<Translation> translations;
            if (translatedTextObj instanceof List<?> translatedList) {
                // Handle list of translations with safe casting and filtering
                translations = translatedList.stream()
                        .filter(String.class::isInstance)
                        .map(text -> new Translation((String) text, detectedSourceLang))
                        .collect(Collectors.toList());
            } else if (translatedTextObj instanceof String translatedText) {
                // Handle single translation string
                translations = List.of(new Translation(translatedText, detectedSourceLang));
            } else {
                // Fallback for unexpected response format
                logger.warn("Unexpected translatedText type: {}", translatedTextObj != null ? translatedTextObj.getClass() : "null");
                translations = List.of(new Translation("", detectedSourceLang));
            }

            TranslationResponse translationResponse = new TranslationResponse(translations);

            // Cache successful translation if caching is enabled
            if (config.isCacheEnabled()) {
                cache.put(
                        String.join("", request.getTextSegments()),
                        request.getTargetLang(),
                        request.getSourceLang(),
                        translationResponse
                );
            }

            // Record success metrics
            successCount.incrementAndGet();
            totalLatencyNanos.addAndGet(System.nanoTime() - startTime);
            return translationResponse;
        } catch (Exception e) {
            // Record error metrics and re-throw with context
            incrementErrorCount(e.getClass().getSimpleName());
            logger.error("Failed to send translation request", e);
            totalLatencyNanos.addAndGet(System.nanoTime() - startTime);
            throw e;
        }
    }

    /**
     * Safely extracts the detected language from various possible API response formats.
     * <p>
     * This method handles the flexible nature of LibreTranslate API responses where the
     * {@code detectedLanguage} field can be returned in different formats depending on
     * the API version, configuration, or endpoint implementation:
     * </p>
     * <ul>
     *     <li><strong>Map format:</strong> {@code {"language": "en", "confidence": 0.99}}</li>
     *     <li><strong>List with maps:</strong> {@code [{"language": "en", "confidence": 0.99}]}</li>
     *     <li><strong>List with strings:</strong> {@code ["en", "es"]}</li>
     *     <li><strong>Direct string:</strong> {@code "en"}</li>
     *     <li><strong>Null/missing:</strong> {@code null} or field not present</li>
     * </ul>
     * <p>
     * The method uses defensive programming techniques to handle unexpected formats
     * gracefully and provides detailed logging for debugging purposes.
     * </p>
     *
     * @param detectedLanguageObj the detected language object from the API response,
     *                            can be null, Map, List, or String
     * @return the detected language code as a String (e.g., "en", "es", "fr"),
     * or {@code null} if the language could not be determined or parsed
     * @since 1.0
     */
    private String extractDetectedLanguage(Object detectedLanguageObj) {
        // Early return for null input
        if (detectedLanguageObj == null) {
            return null;
        }

        try {
            // Case 1: Map format - {"language": "en", "confidence": 0.99}
            // This is the most common format for detailed language detection results
            if (detectedLanguageObj instanceof Map<?, ?> detectedLanguageMap) {
                Object languageValue = detectedLanguageMap.get("language");
                return languageValue instanceof String ? (String) languageValue : null;
            }

            // Case 2: List format - [{"language": "en"}] or ["en"]
            // Some API implementations return arrays for multiple detection results
            if (detectedLanguageObj instanceof List<?> detectedLanguageList && !detectedLanguageList.isEmpty()) {
                Object firstElement = detectedLanguageList.get(0);

                // Sub-case 2a: List of maps [{"language": "en", "confidence": 0.99}]
                if (firstElement instanceof Map<?, ?> firstLanguageMap) {
                    Object languageValue = firstLanguageMap.get("language");
                    return languageValue instanceof String ? (String) languageValue : null;
                }
                // Sub-case 2b: List of strings ["en", "es"]
                else if (firstElement instanceof String) {
                    return (String) firstElement;
                }
            }

            // Case 3: Direct string format - "en"
            // Simple format for basic language detection
            if (detectedLanguageObj instanceof String) {
                return (String) detectedLanguageObj;
            }

            // Log unsupported format for debugging and future enhancement
            logger.warn("Unsupported detectedLanguage format: {} with value: {}",
                    detectedLanguageObj.getClass().getSimpleName(), detectedLanguageObj);

        } catch (Exception e) {
            // Catch any unexpected parsing errors and log for debugging
            logger.error("Error parsing detected language: {}", e.getMessage(), e);
            throw new RuntimeException("Error parsing detected language: " + e.getMessage(), e);
        }

        // Return null if no supported format was found or parsing failed
        return null;
    }

    /**
     * Executes a supplier with retry logic for transient failures.
     *
     * @param supplier the operation to execute
     * @param <T>      the return type
     * @return the result of the operation
     * @throws Exception if all retries fail or a non-retryable error occurs
     */
    private <T> T executeWithRetry(SupplierWithException<T> supplier) throws Exception {
        if (!config.isRetryEnabled()) {
            return supplier.get();
        }
        int attempts = 0;
        Exception lastException = null;
        Set<Integer> retryableStatusCodes = Set.of(429, 500, 502, 503);
        RetryStrategy retryStrategy = config.getRetryStrategy();
        while (attempts <= config.getMaxRetries()) {
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
            } catch (Exception e) {
                logger.error("Non-retryable error", e);
                throw e;
            }
            attempts++;
            if (attempts <= config.getMaxRetries()) {
                long backoff = retryStrategy.getNextDelay(attempts);
                logger.warn("Retry attempt {}/{} after {}ms", attempts, config.getMaxRetries(), backoff);
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
     * Executes an asynchronous translation with retry logic for transient failures.
     * <p>
     * Retries up to the configured maximum for HTTP status codes (429, 500, 502, 503) or IO exceptions,
     * using exponential backoff with delayed execution.
     * </p>
     *
     * @param request the translation request
     * @param attempt the current attempt number (0-based)
     * @return a {@link CompletableFuture} containing the translation response
     */
    private CompletableFuture<TranslationResponse> executeWithAsyncRetry(TranslationRequest request, int attempt) {
        if (!config.isRetryEnabled() || attempt > config.getMaxRetries()) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    rateLimiter.acquire();
                    return sendRequest(request);
                } catch (Exception e) {
                    throw new CompletionException(new LibreTranslateException("Async translation failed", e));
                }
            }, virtualThreadExecutor).thenApply(response -> {
                circuitBreaker.recordSuccess();
                return response;
            }).exceptionallyCompose(throwable -> {
                circuitBreaker.recordFailure();
                failureCount.incrementAndGet();
                incrementErrorCount(throwable.getCause().getClass().getSimpleName());
                logger.error("Async translation failed", throwable);
                return CompletableFuture.failedFuture(new LibreTranslateException("Async translation failed", throwable.getCause()));
            });
        }

        Set<Integer> retryableStatusCodes = Set.of(429, 500, 502, 503);
        RetryStrategy retryStrategy = config.getRetryStrategy();

        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
                return sendRequest(request);
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
            if (attempt < config.getMaxRetries()) {
                long backoff = retryStrategy.getNextDelay(attempt + 1);
                logger.warn("Async retry attempt {}/{} after {}ms", attempt + 1, config.getMaxRetries(), backoff);
                return CompletableFuture.runAsync(() -> {
                        }, CompletableFuture.delayedExecutor(backoff, TimeUnit.MILLISECONDS, virtualThreadExecutor))
                        .thenCompose(v -> executeWithAsyncRetry(request, attempt + 1));
            }
            circuitBreaker.recordFailure();
            failureCount.incrementAndGet();
            incrementErrorCount(throwable.getCause().getClass().getSimpleName());
            logger.error("All async retries failed", throwable);
            return CompletableFuture.failedFuture(new LibreTranslateException("All async retries failed", throwable.getCause()));
        });
    }

    /**
     * Returns the translation cache, if enabled.
     *
     * @return the {@link TranslationCache}, or null if caching is disabled
     */
    public TranslationCache getCache() {
        return cache;
    }

    /**
     * Retrieves monitoring statistics for the client.
     *
     * @return a {@link MonitoringStats} object with cache, rate limiter, and request statistics
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

    @Override
    public void close() {
        shutdown();
        INSTANCES.remove(this);
    }

    /**
     * Shuts down the virtual thread executor gracefully.
     * <p>
     * Attempts to terminate within 60 seconds, otherwise forces shutdown.
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
     * Registers a JVM shutdown hook for automatic cleanup.
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
     * Increments the count for a specific error type.
     *
     * @param errorType the error type (e.g., HTTP status or exception name)
     */
    private void incrementErrorCount(String errorType) {
        errorTypeCounts.computeIfAbsent(errorType, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Functional interface for suppliers that may throw exceptions.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    /**
     * Record for client monitoring statistics.
     *
     * @param cacheStats           cache performance metrics, or null if caching is disabled
     * @param rateLimiterStats     rate limiter statistics
     * @param successCount         number of successful translations
     * @param failureCount         number of failed translations
     * @param averageLatencyMillis average request latency in milliseconds
     * @param errorTypeCounts      map of error types to their counts
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
     * Record for cache performance metrics.
     *
     * @param cacheHits     number of cache hits
     * @param cacheMisses   number of cache misses
     * @param cacheSize     current number of cache entries
     * @param cacheHitRatio ratio of hits to total requests
     * @param cacheEntries  list of all cache entries
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