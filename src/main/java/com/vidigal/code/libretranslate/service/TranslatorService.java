package com.vidigal.code.libretranslate.service;

import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for translation operations.
 * <p>
 * This interface defines the contract for interacting with a translation service, such as LibreTranslate.
 * It provides methods for translating text synchronously and asynchronously, testing the connection to the service,
 * and processing commands in bulk.
 */
public interface TranslatorService {

    /**
     * Creates a new TranslatorService instance with the specified configuration.
     * <p>
     * This method allows the creation of a {@link TranslatorService} instance using a pre-configured
     * {@link LibreTranslateConfig} object. The configuration includes settings such as the API URL and API key.
     *
     * @param config Configuration object for LibreTranslate
     * @return A new instance of {@link TranslatorService}
     * @throws IllegalArgumentException If the provided configuration is null or invalid
     */
    static TranslatorService create(LibreTranslateConfig config) {
        return new LibreTranslateClient(config);
    }

    /**
     * Creates a new TranslatorService instance with the specified API URL and key.
     * <p>
     * This method simplifies the creation of a {@link TranslatorService} instance by directly providing the
     * API URL and API key. Internally, it uses the {@link TranslatorServiceFactory} to create the service.
     *
     * @param apiUrl LibreTranslate API URL
     * @param apiKey API key for the LibreTranslate service (optional, can be null)
     * @return A new instance of {@link TranslatorService}
     * @throws IllegalArgumentException If the provided API URL is null or invalid
     */
    static TranslatorService create(String apiUrl, String apiKey) {
        return TranslatorServiceFactory.create(apiUrl, apiKey);
    }


    /**
     * Creates and returns a LibreTranslateConfig object with the specified API URL and API key.
     *
     * @param apiUrl URL of the LibreTranslate API
     * @param apiKey API key for the LibreTranslate service
     * @return Configured LibreTranslateConfig object
     */
    static LibreTranslateConfig createConfig(String apiUrl, String apiKey) {
        return TranslatorServiceFactory.createConfig(apiUrl, apiKey);

    }

    /**
     * Tests the connection to the translation service provided by the given API URL.
     * <p>
     * This method checks whether the translation service is reachable at the specified URL. It internally
     * delegates the task to the {@link TranslatorServiceFactory#testConnection(String)} method.
     *
     * @param apiUrl The URL of the translation service API to be tested
     * @return {@code true} if the connection is successful, {@code false} otherwise
     * @throws IllegalArgumentException If the provided API URL is null or invalid
     */
    static boolean testConnection(String apiUrl) {
        return TranslatorServiceFactory.testConnection(apiUrl);
    }

    /**
     * Translates text to the target language with auto-detection of source language.
     * <p>
     * This method translates the provided text into the specified target language. The source language is
     * automatically detected by the translation service.
     *
     * @param text           Text to translate
     * @param targetLanguage Target language code (e.g., "en" for English, "es" for Spanish, "fr" for French)
     * @return Translated text, or {@code null} if the translation fails
     * @throws IllegalArgumentException If the provided text or target language is null or empty
     */
    String translate(String text, String targetLanguage);

    /**
     * Translates text from the source language to the target language.
     * <p>
     * This method translates the provided text from the specified source language to the target language.
     * If the source language is set to "auto", the translation service will attempt to detect the source language.
     *
     * @param text           Text to translate
     * @param sourceLanguage Source language code (e.g., "en" for English, "es" for Spanish, or "auto" for auto-detection)
     * @param targetLanguage Target language code (e.g., "en" for English, "es" for Spanish, "fr" for French)
     * @return Translated text, or {@code null} if the translation fails
     * @throws IllegalArgumentException If the provided text, source language, or target language is null or empty
     */
    String translate(String text, String sourceLanguage, String targetLanguage);

    /**
     * Translates text asynchronously to the target language with auto-detection of source language.
     * <p>
     * This method performs the translation operation asynchronously, returning a {@link CompletableFuture} that
     * will eventually contain the translated text. The source language is automatically detected.
     *
     * @param text           Text to translate
     * @param targetLanguage Target language code (e.g., "en" for English, "es" for Spanish, "fr" for French)
     * @return A {@link CompletableFuture} containing the translated text
     * @throws IllegalArgumentException If the provided text or target language is null or empty
     */
    CompletableFuture<String> translateAsync(String text, String targetLanguage);

    /**
     * Translates text asynchronously from the source language to the target language.
     * <p>
     * This method performs the translation operation asynchronously, returning a {@link CompletableFuture} that
     * will eventually contain the translated text. If the source language is set to "auto", the translation service
     * will attempt to detect the source language.
     *
     * @param text           Text to translate
     * @param sourceLanguage Source language code (e.g., "en" for English, "es" for Spanish, or "auto" for auto-detection)
     * @param targetLanguage Target language code (e.g., "en" for English, "es" for Spanish, "fr" for French)
     * @return A {@link CompletableFuture} containing the translated text
     * @throws IllegalArgumentException If the provided text, source language, or target language is null or empty
     */
    CompletableFuture<String> translateAsync(String text, String sourceLanguage, String targetLanguage);

    /**
     * Processes a list of translation commands in bulk.
     * <p>
     * Each command in the list specifies a translation operation, including the mode (synchronous or asynchronous),
     * the text to translate, and the source/target languages. The method processes all commands and returns a list
     * of results corresponding to each command.
     *
     * @param commands List of commands to process, where each command is a string formatted according to the expected syntax
     * @return A list of results, where each result corresponds to the outcome of processing a single command
     * @throws IllegalArgumentException If the provided list of commands is null or empty
     */
    List<String> processCommands(List<String> commands, boolean log);


    /**
     * Shuts down the ExecutorService and ScheduledExecutorService to release resources.
     * This method should be called when the client is no longer needed to prevent resource leaks.
     */
    void close();

    // ==================== Metrics Methods ====================


    /**
     * Gets the number of cache hits.
     *
     * @return The cache hit count
     */
    int getCacheHits();

    /**
     * Gets the number of cache misses.
     *
     * @return The cache miss count
     */
    int getCacheMisses();

    /**
     * Gets the total number of API calls.
     *
     * @return The total API call count
     */
    int getTotalApiCalls();

    /**
     * Gets the number of successful API responses.
     *
     * @return The successful response count
     */
    int getSuccessfulResponses();

    /**
     * Gets the number of error API responses.
     *
     * @return The error response count
     */
    int getErrorResponses();

    /**
     * Gets the average API response time in milliseconds.
     *
     * @return The average response time or 0 if no calls have been made
     */
    double getAverageResponseTime();


    /**
     * Clears all monitoring metrics.
     */
    void clearMetrics();

    /**
     * Clears the translation cache.
     */
    void clearCache();


}