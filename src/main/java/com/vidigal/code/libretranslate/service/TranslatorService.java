package com.vidigal.code.libretranslate.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core service interface for translation operations.
 * <p>
 * This interface defines the contract for interacting with a translation service.
 * It follows the Interface Segregation Principle by focusing only on translation operations.
 */
public interface TranslatorService extends AutoCloseable {

    /**
     * Translates text to the target language with auto-detection of source language.
     *
     * @param text           Text to translate
     * @param targetLanguage Target language code (e.g., "en" for English, "es" for Spanish)
     * @return Translated text
     */
    String translate(String text, String targetLanguage);

    /**
     * Translates text from the source language to the target language.
     *
     * @param text           Text to translate
     * @param sourceLanguage Source language code (e.g., "en" for English, or "auto" for detection)
     * @param targetLanguage Target language code (e.g., "es" for Spanish)
     * @return Translated text
     */
    String translate(String text, String sourceLanguage, String targetLanguage);

    /**
     * Asynchronously translates text to the specified target language using auto-detection.
     *
     * @param text           The text to translate
     * @param targetLanguage The target language code
     * @return A CompletableFuture that resolves to the translated text
     */
    CompletableFuture<String> translateAsync(String text, String targetLanguage);

    /**
     * Asynchronously translates text from a specified source language to a target language.
     *
     * @param text           The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return A CompletableFuture that resolves to the translated text
     */
    CompletableFuture<String> translateAsync(String text, String sourceLanguage, String targetLanguage);

    /**
     * Processes a list of translation commands and returns the results.
     *
     * @param commands The list of commands to process
     * @param log      Whether to log the results
     * @return A list of results corresponding to each command
     */
    List<String> processCommands(List<String> commands, boolean log);

    /**
     * Gets the number of cache hits.
     *
     * @return Total number of cache hits
     */
    int getCacheHits();

    /**
     * Gets the number of cache misses.
     *
     * @return Total number of cache misses
     */
    int getCacheMisses();

    /**
     * Clears metrics related to the translation service.
     */
    void clearMetrics();
}