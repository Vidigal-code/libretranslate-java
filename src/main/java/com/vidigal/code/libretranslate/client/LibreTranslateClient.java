package com.vidigal.code.libretranslate.client;

import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.language.Language;
import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.util.JsonUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client implementation for the LibreTranslate API providing text translation services.
 * Features include rate limiting, caching, asynchronous translation, and performance monitoring.
 *
 * <p>This client implements the {@link TranslatorService} interface and manages resources
 * automatically when used with try-with-resources (implements {@link AutoCloseable}).</p>
 *
 * @author Refactored version Kauan Vidigal
 */
public class LibreTranslateClient extends AbstractTranslatorClient implements TranslatorService, AutoCloseable {

    // ==================== Constants ====================

    /**
     * Default source language code when none is specified
     */
    public static final String DEFAULT_SOURCE_LANGUAGE = Language.AUTO.getCode();

    /**
     * Cache entry expiration time in milliseconds (5 minutes)
     */
    private static final long CACHE_EXPIRATION_TIME_MS = 5 * 60 * 1000;

    /**
     * Plain text format for API requests
     */
    private static final String FORMAT_TEXT = "text";

    /**
     * Error message for empty server responses
     */
    private static final String ERROR_MESSAGE_EMPTY_RESPONSE = "Empty response from server";

    /**
     * Error message for invalid command format
     */
    private static final String ERROR_MESSAGE_INVALID_COMMAND = "Invalid command format";

    /**
     * HTTP status code for rate limit exceeded
     */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    // ==================== Configuration Options ====================

    /**
     * Flag to enable/disable cache logging
     */
    public static boolean CACHE_LOG = false;

    // ==================== Cache Monitoring ====================

    /**
     * Counter for cache hits
     */
    private final AtomicInteger cacheHits = new AtomicInteger(0);

    /**
     * Counter for cache misses
     */
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    // ==================== API Usage Monitoring ====================

    /**
     * Counter for total API calls
     */
    private final AtomicInteger apiCalls = new AtomicInteger(0);

    /**
     * Counter for successful API responses
     */
    private final AtomicInteger successfulResponses = new AtomicInteger(0);

    /**
     * Counter for error API responses
     */
    private final AtomicInteger errorResponses = new AtomicInteger(0);

    /**
     * Accumulator for total API response time
     */
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    // ==================== Rate Limiting ====================

    /**
     * Scheduler for rate limit window reset
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Lock object for rate limiting synchronization
     */
    private final Object rateLimitLock = new Object();

    /**
     * Counter for requests in the current rate limit window
     */
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    /**
     * Maximum requests allowed per time window
     */
    private final int maxRequestsPerWindow;

    /**
     * Time window size in milliseconds
     */
    private final long windowSizeMs;

    /**
     * Minimum interval between requests in milliseconds
     */
    private final long minRequestIntervalMs;

    /**
     * Start time of the current rate limit window
     */
    private volatile long windowStartTime = System.currentTimeMillis();

    /**
     * Timestamp of the last request
     */
    private long lastRequestTime = 0;

    // ==================== Resources ====================

    /**
     * Configuration object containing API settings
     */
    private final LibreTranslateConfig config;

    /**
     * Thread pool for asynchronous operations
     */
    private final ExecutorService executorService;

    /**
     * Constructs a new LibreTranslate client with the specified configuration.
     * Initializes rate limiting, caching, and monitoring components.
     *
     * @param config Configuration object containing API details and rate limits
     * @throws TranslationException if the configuration is null
     */
    public LibreTranslateClient(LibreTranslateConfig config) {
        validateConfig(config);
        this.config = config;
        this.executorService = createExecutorService();
        this.scheduler = createSchedulerService();

        // Initialize rate limiting parameters
        this.maxRequestsPerWindow = config.getMaxRequestsPerSecond();
        this.windowSizeMs = 1000; // 1 second window
        this.minRequestIntervalMs = calculateMinRequestInterval(config.getMaxRequestsPerSecond());

        scheduleWindowReset();
        scheduleCacheCleanup();
    }

    /**
     * Validates that the configuration object is not null.
     *
     * @param config Configuration object to validate
     * @throws TranslationException if the configuration is null
     */
    private void validateConfig(LibreTranslateConfig config) {
        if (config == null) {
            throw new TranslationException("Configuration cannot be null");
        }
    }

    /**
     * Creates a cached thread pool for executing asynchronous translation requests.
     *
     * @return An ExecutorService configured for translation operations
     */
    private ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool();
    }

    /**
     * Creates a scheduled executor service for periodic tasks.
     *
     * @return A ScheduledExecutorService configured with daemon threads
     */
    private ScheduledExecutorService createSchedulerService() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("RateLimitWindowReset");
            return t;
        });
    }

    /**
     * Calculates the minimum interval between requests based on the maximum requests per second.
     *
     * @param maxRequestsPerSecond Maximum number of requests allowed per second
     * @return Minimum interval between requests in milliseconds
     */
    private long calculateMinRequestInterval(int maxRequestsPerSecond) {
        return Math.max(10, 1000 / Math.max(1, maxRequestsPerSecond));
    }

    /**
     * Schedules the periodic reset of the rate limit window.
     * Also logs monitoring metrics if cache logging is enabled.
     */
    private void scheduleWindowReset() {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (rateLimitLock) {
                windowStartTime = System.currentTimeMillis();
                requestCounter.set(0);
                rateLimitLock.notifyAll(); // Wake up any waiting threads
            }

            logMetricsIfEnabled();
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Logs monitoring metrics if cache logging is enabled.
     */
    private void logMetricsIfEnabled() {
        if (CACHE_LOG) {
            LOGGER.info("=== Monitoring Metrics ===");
            LOGGER.info("Cache Hits: {}", getCacheHits());
            LOGGER.info("Cache Misses: {}", getCacheMisses());
            LOGGER.info("Total API Calls: {}", getTotalApiCalls());
            LOGGER.info("Successful Responses: {}", getSuccessfulResponses());
            LOGGER.info("Error Responses: {}", getErrorResponses());
            LOGGER.info("Average Response Time: {}ms", getAverageResponseTime());
        }
    }

    /**
     * Schedules periodic cleanup of expired cache entries.
     */
    private void scheduleCacheCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            translationCache.entrySet().removeIf(entry -> {
                CacheEntry cacheEntry = entry.getValue();
                return (currentTime - cacheEntry.getTimestamp()) > CACHE_EXPIRATION_TIME_MS;
            });
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Releases resources used by this client.
     * This method should be called when the client is no longer needed to prevent resource leaks.
     */
    @Override
    public void close() {
        shutdown();
    }

    /**
     * Shuts down the ExecutorService and ScheduledExecutorService to release resources.
     */
    private void shutdown() {
        shutdownExecutorService();
        shutdownSchedulerService();
    }

    /**
     * Shuts down the main executor service.
     */
    private void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                LOGGER.warn("ExecutorService shutdown was interrupted", e);
            }
        }
    }

    /**
     * Shuts down the scheduler service.
     */
    private void shutdownSchedulerService() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                LOGGER.warn("ScheduledExecutorService shutdown was interrupted", e);
            }
        }
    }

    /**
     * Translates text to the specified target language using the default source language.
     *
     * @param text           The text to translate
     * @param targetLanguage The target language code
     * @return The translated text
     * @throws TranslationException if translation fails
     */
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

        String cacheKey = generateCacheKey(text, sourceLanguage, targetLanguage);
        String translatedText = checkCache(cacheKey);

        if (translatedText != null) {
            return translatedText;
        }

        return performTranslation(text, sourceLanguage, targetLanguage, cacheKey);
    }

    /**
     * Checks the cache for an existing translation.
     *
     * @param cacheKey The cache key to check
     * @return The cached translation or null if not found
     */
    private String checkCache(String cacheKey) {
        if (translationCache.containsKey(cacheKey)) {
            CacheEntry cacheEntry = translationCache.get(cacheKey);

            if (CACHE_LOG) {
                LOGGER.debug("Cache hit for key: {}", cacheKey);
            }

            cacheHits.incrementAndGet();
            return cacheEntry.getValue();
        } else {
            cacheMisses.incrementAndGet();
            return null;
        }
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
            applyRateLimit();
            Map<String, String> params = createTranslationParams(text, sourceLanguage, targetLanguage);

            long startTime = System.currentTimeMillis();
            HttpResponse response = sendHttpRequest(config.getApiUrl(), "POST", params);
            long responseTime = System.currentTimeMillis() - startTime;

            updateMetrics(response, responseTime);

            String translatedText = handleTranslationResponse(response.getBody());
            translationCache.putIfAbsent(cacheKey, new CacheEntry(translatedText));

            return translatedText;
        } catch (Exception e) {
            throw new TranslationException("Translation failed", e);
        }
    }

    /**
     * Creates the parameters map for a translation request.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return A map of request parameters
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
     * Updates metrics based on the API response.
     *
     * @param response     The HTTP response from the API
     * @param responseTime The time taken to get the response
     */
    private void updateMetrics(HttpResponse response, long responseTime) {
        totalResponseTime.addAndGet(responseTime);
        apiCalls.incrementAndGet();

        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            successfulResponses.incrementAndGet();
        } else {
            errorResponses.incrementAndGet();
        }
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
     * Applies rate limiting using a token bucket algorithm with a sliding window.
     * This implementation handles both per-second limits and ensures minimum intervals
     * between requests to prevent API overload.
     *
     * @throws RuntimeException if interrupted while waiting
     */
    private void applyRateLimit() {
        synchronized (rateLimitLock) {
            try {
                enforceMinimumRequestInterval();
                checkAndResetWindow();
                waitIfRateLimitExceeded();

                // Increment counter and update last request time
                requestCounter.incrementAndGet();
                lastRequestTime = System.currentTimeMillis();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while applying rate limit", e);
            }
        }
    }

    /**
     * Enforces the minimum interval between requests.
     *
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    private void enforceMinimumRequestInterval() throws InterruptedException {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;

        if (timeSinceLastRequest < minRequestIntervalMs) {
            long waitTime = minRequestIntervalMs - timeSinceLastRequest;
            Thread.sleep(waitTime);
        }
    }

    /**
     * Checks if the current rate limit window has expired and resets it if necessary.
     */
    private void checkAndResetWindow() {
        long now = System.currentTimeMillis();
        if (now - windowStartTime >= windowSizeMs) {
            windowStartTime = now;
            requestCounter.set(0);
        }
    }

    /**
     * Waits if the rate limit has been exceeded for the current window.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    private void waitIfRateLimitExceeded() throws InterruptedException {
        long now = System.currentTimeMillis();
        while (requestCounter.get() >= maxRequestsPerWindow) {
            long timeToNextWindow = windowSizeMs - (now - windowStartTime);
            if (timeToNextWindow > 0) {
                LOGGER.debug("Rate limit reached. Waiting for {}ms", timeToNextWindow);
                rateLimitLock.wait(timeToNextWindow + 10); // Add small buffer
                now = System.currentTimeMillis();
            } else {
                // Window should have reset, but manually reset if needed
                windowStartTime = now;
                requestCounter.set(0);
            }
        }
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
     * Sends an HTTP request and returns the response.
     *
     * @param apiUrl The API URL to send the request to
     * @param method The HTTP method (e.g., "GET", "POST")
     * @param params The request parameters
     * @return The HTTP response
     * @throws IOException If the request fails
     */
    private HttpResponse sendHttpRequest(String apiUrl, String method, Map<String, String> params) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = setupConnection(apiUrl, method);

            if ("POST".equals(method)) {
                sendRequestBody(connection, params);
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);

            // Handle rate limiting (429 Too Many Requests)
            if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                return handleRateLimitExceeded(connection, apiUrl, method, params);
            } else if (responseCode >= 400) {
                LOGGER.error("HTTP request failed with code {}: {}", responseCode, responseBody);
            }

            return new HttpResponse(responseCode, responseBody, connection.getHeaderFields());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Sets up an HTTP connection with appropriate headers and timeouts.
     *
     * @param apiUrl The API URL to connect to
     * @param method The HTTP method to use
     * @return A configured HttpURLConnection
     * @throws IOException if opening the connection fails
     */
    private HttpURLConnection setupConnection(String apiUrl, String method) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(config.getConnectionTimeout());
        connection.setReadTimeout(config.getSocketTimeout());
        return connection;
    }

    /**
     * Sends the request body to the connection's output stream.
     *
     * @param connection The HTTP connection
     * @param params     The parameters to send
     * @throws IOException if writing to the output stream fails
     */
    private void sendRequestBody(HttpURLConnection connection, Map<String, String> params) throws IOException {
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(buildRequestBody(params).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads the response body from the connection.
     *
     * @param connection   The HTTP connection
     * @param responseCode The HTTP response code
     * @return The response body as a string
     * @throws IOException if reading the response fails
     */
    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        try (InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line.trim());
            }
        }
        return responseBody.toString();
    }

    /**
     * Handles the case when the rate limit is exceeded (HTTP 429).
     *
     * @param connection The HTTP connection
     * @param apiUrl     The API URL
     * @param method     The HTTP method
     * @param params     The request parameters
     * @return The HTTP response after retrying
     * @throws IOException          if the request fails
     * @throws InterruptedException if waiting is interrupted
     */
    private HttpResponse handleRateLimitExceeded(HttpURLConnection connection, String apiUrl,
                                                 String method, Map<String, String> params)
            throws IOException, InterruptedException {

        String retryAfter = connection.getHeaderField("Retry-After");
        long retryAfterMs = retryAfter != null
                ? Long.parseLong(retryAfter) * 1000
                : config.getRateLimitCooldown();

        LOGGER.warn("Rate limit exceeded. Server suggests retry after {}ms", retryAfterMs);
        Thread.sleep(retryAfterMs);
        return sendHttpRequest(apiUrl, method, params); // Retry the request
    }

    /**
     * Builds the request body from parameters.
     *
     * @param params The parameters map
     * @return The encoded request body
     * @throws UnsupportedEncodingException If encoding fails
     */
    private String buildRequestBody(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return result.toString();
    }

    /**
     * Handles the translation response from the API.
     * Parses the JSON response and extracts the translated text.
     *
     * @param responseBody The raw response body from the API
     * @return The translated text if successful
     * @throws TranslationException If the response is invalid or contains errors
     */
    private String handleTranslationResponse(String responseBody) {
        if (isEmpty(responseBody)) {
            throw new TranslationException(ERROR_MESSAGE_EMPTY_RESPONSE);
        }

        try {
            Map<String, Object> jsonResponse = JsonUtil.fromJson(responseBody, Map.class);

            // Check for error response
            if (jsonResponse.containsKey("error")) {
                String errorMessage = (String) jsonResponse.get("error");
                throw new TranslationException("API returned error: " + errorMessage);
            }

            // Extract translated text
            if (jsonResponse.containsKey("translatedText")) {
                String translatedText = (String) jsonResponse.get("translatedText");
                if (isEmpty(translatedText)) {
                    throw new TranslationException("Translated text is empty in the response");
                }
                return translatedText;
            } else {
                throw new TranslationException("Unexpected response format: 'translatedText' field missing");
            }
        } catch (ClassCastException e) {
            throw new TranslationException("Unexpected data type in JSON response", e);
        }
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
        return Collections.singletonList(String.join("\n", commands.parallelStream()
                .map(command -> {
                    try {
                        return processSingleCommand(command, log);
                    } catch (Exception e) {
                        LOGGER.error("Command processing failed for command: " + command, e);
                        return "Error: " + e.getMessage();
                    }
                })
                .toList()));
    }

    /**
     * Processes a single translation command.
     *
     * @param command The command to process
     * @param log     Whether to log the result
     * @return The result of processing the command
     */
    private String processSingleCommand(String command, boolean log) {
        try {
            String[] parts = command.split(";");
            if (parts.length < 2) {
                throw new TranslationException("Invalid command format: " + command);
            }

            String mode = parts[0];
            if (mode.startsWith("m:")) {
                return handleModeSpecificCommand(parts, log);
            } else if (mode.startsWith("t:")) {
                return handleNormalCommand(parts, log);
            } else {
                throw new TranslationException(ERROR_MESSAGE_INVALID_COMMAND + ": " + command);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process command: {}", command, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Extracts translation parameters from command parts.
     *
     * @param parts The command parts
     * @return A map containing the extracted parameters (text, sourceLang, targetLang)
     */
    private Map<String, String> extractTranslationParams(String[] parts) {
        Map<String, String> params = new HashMap<>();
        params.put("text", extractText(parts[0]));
        params.put("sourceLang", extractSourceLanguage(parts));
        params.put("targetLang", extractTargetLanguage(parts));
        return params;
    }

    /**
     * Handles mode-specific translation commands (e.g., synchronous or asynchronous).
     *
     * @param parts The command parts
     * @param log   Whether to log the result
     * @return The result of the translation
     * @throws ExecutionException   If asynchronous execution fails
     * @throws InterruptedException If the thread is interrupted
     */
    private String handleModeSpecificCommand(String[] parts, boolean log) throws ExecutionException, InterruptedException {
        String operationMode = extractOperationMode(parts[0]);
        Map<String, String> params = extractTranslationParams(Arrays.copyOfRange(parts, 1, parts.length));
        String text = params.get("text");
        String sourceLang = params.get("sourceLang");
        String targetLang = params.get("targetLang");

        switch (operationMode) {
            case "s":
                String result = translate(text, sourceLang, targetLang);
                return log ? "Synchronous Translation: " + result : result;

            case "as":
                CompletableFuture<String> future = translateAsync(text, sourceLang, targetLang);
                return log ? "Asynchronous Translation: " + future.get() : future.get();

            default:
                throw new TranslationException("Invalid operation mode: " + operationMode);
        }
    }

    /**
     * Handles normal translation commands without specific modes.
     *
     * @param parts The command parts
     * @param log   Whether to log the result
     * @return The result of the translation
     */
    private String handleNormalCommand(String[] parts, boolean log) {
        Map<String, String> params = extractTranslationParams(parts);
        String text = params.get("text");
        String sourceLang = params.get("sourceLang");
        String targetLang = params.get("targetLang");

        String result = translate(text, sourceLang, targetLang);
        return log ? "Default Translation: " + result : result;
    }

    /**
     * Checks if the API key is valid.
     *
     * @return True if the API key is valid, false otherwise
     */
    private boolean isApiKeyValid() {
        return config.getApiKey() != null && !config.getApiKey().isEmpty();
    }

    /**
     * Extracts the operation mode from a command part.
     *
     * @param part The command part
     * @return The operation mode
     */
    private String extractOperationMode(String part) {
        return part.substring(2);
    }

    /**
     * Extracts the text from a command part.
     *
     * @param part The command part.
     * @return The extracted text.
     */
    private String extractText(String part) {
        return part.substring(2);
    }

    /**
     * Extracts the source language from the command parts.
     *
     * @param parts The command parts.
     * @return The source language code.
     */
    private String extractSourceLanguage(String[] parts) {
        return parts.length > 3 ? parts[2] : DEFAULT_SOURCE_LANGUAGE;
    }

    /**
     * Extracts the target language from the command parts.
     *
     * @param parts The command parts.
     * @return The target language code.
     */
    private String extractTargetLanguage(String[] parts) {
        return parts[parts.length - 1];
    }


    /**
     * Utility method to check if a string is null or empty.
     *
     * @param str The string to check
     * @return True if the string is null or empty, false otherwise
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Generates a cache key for storing and retrieving translations.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return A unique cache key
     */
    private String generateCacheKey(String text, String sourceLanguage, String targetLanguage) {
        return String.format("%s:%s:%s",
                sourceLanguage,
                targetLanguage,
                text.length() > 100 ? text.substring(0, 100).hashCode() + ":" + text.hashCode() : text.hashCode());
    }

    // ==================== Metrics Methods ====================

    /**
     * Gets the number of cache hits.
     *
     * @return The cache hit count
     */
    @Override
    public int getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Gets the number of cache misses.
     *
     * @return The cache miss count
     */
    @Override
    public int getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Gets the total number of API calls.
     *
     * @return The total API call count
     */
    @Override
    public int getTotalApiCalls() {
        return apiCalls.get();
    }

    /**
     * Gets the number of successful API responses.
     *
     * @return The successful response count
     */
    @Override
    public int getSuccessfulResponses() {
        return successfulResponses.get();
    }

    /**
     * Gets the number of error API responses.
     *
     * @return The error response count
     */
    @Override
    public int getErrorResponses() {
        return errorResponses.get();
    }

    /**
     * Gets the average API response time in milliseconds.
     *
     * @return The average response time or 0 if no calls have been made
     */
    @Override
    public double getAverageResponseTime() {
        int calls = apiCalls.get();
        return calls > 0 ? (double) totalResponseTime.get() / calls : 0;
    }

    /**
     * Clears all monitoring metrics.
     */
    @Override
    public void clearMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        apiCalls.set(0);
        successfulResponses.set(0);
        errorResponses.set(0);
        totalResponseTime.set(0);
    }

    /**
     * Clears the translation cache.
     */
    @Override
    public void clearCache() {
        translationCache.clear();
    }


}
