package com.vidigal.code.libretranslate.client;

import com.vidigal.code.libretranslate.cache.CacheFactory;
import com.vidigal.code.libretranslate.cache.TranslationCache;
import com.vidigal.code.libretranslate.cache.TranslationCacheService;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.http.HttpRequestHandler;
import com.vidigal.code.libretranslate.http.HttpRequestService;
import com.vidigal.code.libretranslate.http.HttpResponse;
import com.vidigal.code.libretranslate.language.Language;
import com.vidigal.code.libretranslate.ratelimit.RateLimitMetrics;
import com.vidigal.code.libretranslate.ratelimit.RateLimiterFactory;
import com.vidigal.code.libretranslate.ratelimit.RateLimiterService;
import com.vidigal.code.libretranslate.service.TranslatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client implementation for the LibreTranslate API with modular components.
 * <p>
 * Integrates TranslationCacheService, RateLimiterService, and other supporting components
 * to provide a robust translation service.
 */
public class LibreTranslateClient implements TranslatorService {
    // Constants
    public static final String DEFAULT_SOURCE_LANGUAGE = Language.AUTO.getCode();
    private static final Logger LOGGER = LoggerFactory.getLogger(LibreTranslateClient.class);
    private static final String FORMAT_TEXT = "text";
    private static final long METRICS_LOGGING_INTERVAL_MINUTES = 5;

    // Configuration and Services
    private final LibreTranslateConfig config;
    private final HttpRequestService requestService;
    private final TranslationCacheService translationCache;
    private final RateLimiterService rateLimiter;

    // Monitoring
    private final AtomicInteger apiCalls = new AtomicInteger(0);
    private final AtomicInteger successfulResponses = new AtomicInteger(0);
    private final AtomicInteger errorResponses = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final Map<String, AtomicInteger> languagePairCounts = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> responseCodeCounts = new ConcurrentHashMap<>();

    // Asynchronous Processing
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;

    /**
     * Constructs a new LibreTranslate client with the specified configuration.
     *
     * @param config Configuration object containing API details
     * @throws TranslationException if the configuration is invalid
     */
    public LibreTranslateClient(LibreTranslateConfig config) {
        validateConfig(config);

        this.config = config;
        this.requestService = new HttpRequestHandler(config);
        this.translationCache = CacheFactory.createDefault();

        // Create a new rate limiter and associate it with the config
        this.rateLimiter = RateLimiterFactory.create(config.getMaxRequestsPerSecond());

        // Register the rate limiter with the config so it can be accessed by other components
        try {
            // Use reflection to access the package-private setRateLimiter method
            java.lang.reflect.Method setRateLimiterMethod =
                    LibreTranslateConfig.class.getDeclaredMethod("setRateLimiter", RateLimiterService.class);
            setRateLimiterMethod.setAccessible(true);
            setRateLimiterMethod.invoke(config, rateLimiter);
        } catch (Exception e) {
            LOGGER.warn("Failed to register rate limiter with config: {}", e.getMessage());
        }

        this.executorService = createExecutorService();
        this.schedulerService = createSchedulerService();

        // Set up periodic metrics logging if enabled
        if (TranslationCache.DETAILED_LOGGING) {
            setupMetricsLogging();
        }
    }

    /**
     * Utility method to check if a string is null or empty.
     *
     * @param str The string to check
     * @return True if the string is null or empty, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Sets up periodic logging of monitoring metrics and logs initial values.
     */
    private void setupMetricsLogging() {
        // Log metrics immediately for initial state
        logMonitoringMetrics();

        // Schedule periodic metrics logging
        schedulerService.scheduleAtFixedRate(
                this::logMonitoringMetrics,
                METRICS_LOGGING_INTERVAL_MINUTES,
                METRICS_LOGGING_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        // Schedule periodic cache cleanup to prevent memory issues
        schedulerService.scheduleAtFixedRate(
                () -> LOGGER.debug("Metrics scheduled update running"),
                10,
                30,
                TimeUnit.MINUTES
        );

        LOGGER.info("Metrics logging scheduled every {} minutes", METRICS_LOGGING_INTERVAL_MINUTES);
    }

    /**
     * Logs the current monitoring metrics to the configured logger.
     */
    private void logMonitoringMetrics() {
        try {
            LOGGER.info("=== Monitoring Metrics ===");
            LOGGER.info("Cache Hits: {}", getCacheHits());
            LOGGER.info("Cache Misses: {}", getCacheMisses());
            LOGGER.info("Total API Calls: {}", apiCalls.get());
            LOGGER.info("Successful Responses: {}", successfulResponses.get());
            LOGGER.info("Error Responses: {}", errorResponses.get());
            LOGGER.info("Average Response Time: {}ms", getAverageResponseTime());

            // Add basic rate limiter info
            LOGGER.info("Rate Limiter - Max Requests: {}/sec", config.getMaxRequestsPerSecond());

            // Add detailed rate limiter metrics if available
            try {
                RateLimitMetrics metrics = rateLimiter.getMetrics();
                LOGGER.info("Rate Limiter - Current Rate: {}/sec", metrics.getCurrentRequestRate());
                LOGGER.info("Rate Limiter - Success Rate: {}%", String.format("%.2f", metrics.getSuccessRate() * 100));
                LOGGER.info("Rate Limiter - Available Capacity: {} tokens", metrics.getAvailableTokens());

                if (metrics.isInBackoffMode()) {
                    LOGGER.info("Rate Limiter - BACKOFF MODE - Remaining: {}ms", metrics.getBackoffRemainingMs());
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to log rate limiter metrics: {}", e.getMessage());
            }

            // Add basic cache info
            LOGGER.info("Cache Hit Ratio: {}", String.format("%.2f%%", calculateCacheHitRatio() * 100));

            // Log language pair statistics if any
            if (!languagePairCounts.isEmpty()) {
                logLanguageStatistics();
            }

            // Log response code statistics if any
            if (!responseCodeCounts.isEmpty()) {
                logResponseCodeStatistics();
            }
        } catch (Exception e) {
            LOGGER.error("Error while logging metrics", e);
        }
    }

    /**
     * Logs statistics about language pairs used in translations
     */
    private void logLanguageStatistics() {
        LOGGER.info("=== Language Pair Statistics ===");
        languagePairCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().get() - e1.getValue().get())
                .limit(5)
                .forEach(e -> LOGGER.info("  {} → {} translations", e.getKey(), e.getValue().get()));
    }

    /**
     * Logs statistics about HTTP response codes
     */
    private void logResponseCodeStatistics() {
        LOGGER.info("=== Response Code Statistics ===");
        responseCodeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> LOGGER.info("  HTTP {}: {} responses", e.getKey(), e.getValue().get()));
    }

    /**
     * Calculates the cache hit ratio from available metrics.
     *
     * @return The ratio of cache hits to total cache accesses (0.0-1.0)
     */
    private double calculateCacheHitRatio() {
        int hits = getCacheHits();
        int misses = getCacheMisses();
        int total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    @Override
    public String translate(String text, String targetLanguage) {
        if (!Language.isSupportedLanguage(targetLanguage)) {
            handleError("Target language '" + targetLanguage + "' not supported.");
            return "";
        }
        return translate(text, DEFAULT_SOURCE_LANGUAGE, targetLanguage);
    }

    /**
     * Translates text from a specified source language to a target language.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return The translated text
     * @throws TranslationException if translation fails
     */
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage) {
        if (!validateInputs(text, sourceLanguage, targetLanguage)) {
            return "";
        }

        String cacheKey = translationCache.generateCacheKey(text, sourceLanguage, targetLanguage);
        Optional<String> translatedTextOpt = translationCache.get(cacheKey);

        // Found in cache, log and return
        if (translatedTextOpt.isPresent()) {
            if (TranslationCache.DETAILED_LOGGING && apiCalls.get() % 10 == 0) {
                LOGGER.debug("Cache hit for: [{}→{}] text length: {}",
                        sourceLanguage, targetLanguage, text.length());
            }
            return translatedTextOpt.get();
        }

        // Not in cache, perform translation
        return performTranslation(text, sourceLanguage, targetLanguage, cacheKey);
    }

    /**
     * Performs the actual translation operation by calling the API.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @param cacheKey       The cache key for storing the result
     * @return The translated text
     * @throws TranslationException if translation fails
     */
    private String performTranslation(String text, String sourceLanguage, String targetLanguage, String cacheKey) {
        try {
            // Track language pair statistics
            String langPair = sourceLanguage + "→" + targetLanguage;
            languagePairCounts.computeIfAbsent(langPair, k -> new AtomicInteger(0))
                    .incrementAndGet();

            // Use rate limiter to enforce API rate limits
            boolean acquired = false;
            try {
                acquired = rateLimiter.acquire();
                if (!acquired) {
                    LOGGER.warn("Rate limit exceeded for translation request, delaying execution");
                    // If we couldn't acquire a permit, wait and try again with a small delay
                    Thread.sleep(500);
                    acquired = rateLimiter.acquire();
                    if (!acquired) {
                        throw new TranslationException("Rate limit exceeded, request throttled");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranslationException("Translation interrupted while waiting for rate limit", e);
            }

            Map<String, String> params = createTranslationParams(text, sourceLanguage, targetLanguage);

            long startTime = System.currentTimeMillis();
            HttpResponse response = requestService.sendHttpRequest(config.getApiUrl(), "POST", params);
            long responseTime = System.currentTimeMillis() - startTime;

            updateMetrics(response, responseTime);

            // Handle rate limiting if needed
            if (response.isRateLimited()) {
                requestService.handleRateLimitExceeded(response);

                // Retry the request after rate limit handling
                response = requestService.sendHttpRequest(config.getApiUrl(), "POST", params);
                updateMetrics(response, System.currentTimeMillis() - startTime);
            }

            String translatedText = requestService.handleTranslationResponse(response.getBody());
            translationCache.put(cacheKey, translatedText);

            return translatedText;
        } catch (Exception e) {
            throw new TranslationException("Translation failed", e);
        }
    }

    /**
     * Updates metrics based on the API response.
     *
     * @param response     The HTTP response from the API
     * @param responseTime The time taken to get the response
     */
    private void updateMetrics(HttpResponse response, long responseTime) {
        apiCalls.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);

        // Track response code statistics
        int statusCode = response.getStatusCode();
        responseCodeCounts.computeIfAbsent(statusCode, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (response.isSuccessful()) {
            successfulResponses.incrementAndGet();
        } else {
            errorResponses.incrementAndGet();
        }
    }

    /**
     * Creates parameters map for the translation request.
     *
     * @param text           Text to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return Map of parameters for the translation request
     */
    private Map<String, String> createTranslationParams(String text, String sourceLanguage, String targetLanguage) {
        Map<String, String> params = new HashMap<>();
        params.put("q", text);
        params.put("source", sourceLanguage);
        params.put("target", targetLanguage);
        params.put("format", FORMAT_TEXT);

        if (isApiKeyValid()) {
            params.put("api_key", config.getApiKey());
        }

        return params;
    }

    /**
     * Validates the input parameters for translation.
     *
     * @param text           The text to validate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return True if inputs are valid, false otherwise
     */
    private boolean validateInputs(String text, String sourceLanguage, String targetLanguage) {
        if (isEmpty(text)) {
            handleError("Empty text provided");
            return false;
        }

        if (!Language.isSupportedLanguage(sourceLanguage)) {
            handleError("Source language '" + sourceLanguage + "' not supported.");
            return false;
        }

        if (!Language.isSupportedLanguage(targetLanguage)) {
            handleError("Target language '" + targetLanguage + "' not supported.");
            return false;
        }

        return true;
    }

    /**
     * Asynchronously translates text to the specified target language using the default source language.
     *
     * @param text           The text to translate
     * @param targetLanguage The target language code
     * @return A CompletableFuture that resolves to the translated text
     */
    @Override
    public CompletableFuture<String> translateAsync(String text, String targetLanguage) {
        return translateAsync(text, DEFAULT_SOURCE_LANGUAGE, targetLanguage);
    }

    /**
     * Asynchronously translates text from a specified source language to a target language.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return A CompletableFuture that resolves to the translated text
     */
    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, sourceLanguage, targetLanguage);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Processes a list of translation commands and returns the results.
     *
     * @param commands The list of commands to process
     * @param log      Whether to log the results
     * @return A list of results corresponding to each command
     */
    @Override
    public List<String> processCommands(List<String> commands, boolean log) {
        LibreTranslateCommands commandProcessor = new LibreTranslateCommands(this);
        return commandProcessor.processCommands(commands, log);
    }

    /**
     * Checks if the API key is valid.
     *
     * @return True if the API key is valid, false otherwise
     */
    private boolean isApiKeyValid() {
        return config.getApiKey() != null && !config.getApiKey().isEmpty();
    }

    // Implement AutoCloseable
    @Override
    public void close() {
        shutdownExecutorService(executorService);
        shutdownExecutorService(schedulerService);

        try {
            translationCache.close();
        } catch (Exception e) {
            LOGGER.warn("Error closing translation cache: {}", e.getMessage());
        }

        try {
            rateLimiter.close();
        } catch (Exception e) {
            LOGGER.warn("Error closing rate limiter: {}", e.getMessage());
        }
    }

    @Override
    public int getCacheHits() {
        return translationCache.getCacheHits();
    }

    @Override
    public int getCacheMisses() {
        return translationCache.getCacheMisses();
    }

    /**
     * Helper method for shutting down executor services.
     *
     * @param service The executor service to shut down
     */
    private void shutdownExecutorService(ExecutorService service) {
        if (service != null && !service.isShutdown()) {
            service.shutdown();
            try {
                if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                    service.shutdownNow();
                    if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("Executor service did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                service.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Creates an ExecutorService for handling asynchronous translation requests.
     *
     * @return A newly created ExecutorService
     */
    private ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TranslationWorker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a ScheduledExecutorService for periodic tasks.
     *
     * @return A newly created ScheduledExecutorService
     */
    private ScheduledExecutorService createSchedulerService() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TranslationScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Validates the provided configuration.
     *
     * @param config The configuration to validate
     * @throws TranslationException if the configuration is invalid
     */
    private void validateConfig(LibreTranslateConfig config) {
        if (config == null) {
            throw new TranslationException("Configuration cannot be null");
        }
    }

    /**
     * Gets the average response time for API calls in milliseconds.
     *
     * @return The average response time, or 0 if no calls have been made
     */
    private double getAverageResponseTime() {
        long total = totalResponseTime.get();
        int count = apiCalls.get();
        return count > 0 ? (double) total / count : 0;
    }

    /**
     * Clears all monitoring metrics.
     */
    @Override
    public void clearMetrics() {
        apiCalls.set(0);
        successfulResponses.set(0);
        errorResponses.set(0);
        totalResponseTime.set(0);

        translationCache.clearMetrics();

        rateLimiter.resetMetrics();

        languagePairCounts.clear();
        responseCodeCounts.clear();

        LOGGER.debug("All metrics cleared");
    }

    /**
     * Clears the translation cache.
     */
    public void clearCache() {
        translationCache.clear();
        LOGGER.debug("Translation cache cleared");
    }

    /**
     * Handles errors by logging them and throwing a TranslationException.
     *
     * @param message The error message
     * @param e       The original exception
     */
    protected void handleError(String message, Exception e) {
        LOGGER.error(message, e);
        throw new TranslationException(message, e);
    }

    /**
     * Handles errors by logging them and throwing a TranslationException.
     *
     * @param message The error message
     */
    protected void handleError(String message) {
        LOGGER.error(message);
        throw new TranslationException(message);
    }
}
