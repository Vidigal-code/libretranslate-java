package com.vidigal.code.libretranslate.service;

import com.vidigal.code.libretranslate.config.LibreTranslateConfig;

/**
 * Factory interface for creating TranslatorService instances.
 * <p>
 * This interface follows the Factory Method pattern, providing methods
 * to create instances of TranslatorService with various configurations.
 */
public interface TranslatorServiceFactory {

    /**
     * Creates a new TranslatorService with the specified API URL and key.
     *
     * @param apiUrl LibreTranslate API URL
     * @param apiKey API key for the LibreTranslate service (can be null for public APIs)
     * @return TranslatorService instance
     */
    TranslatorService create(String apiUrl, String apiKey);

    /**
     * Creates a new TranslatorService with the specified configuration.
     *
     * @param config LibreTranslate configuration
     * @return TranslatorService instance
     */
    TranslatorService create(LibreTranslateConfig config);

    /**
     * Tests the connection to the specified API URL.
     *
     * @param apiUrl The URL of the API to test
     * @return True if the connection is successful, false otherwise
     */
    boolean testConnection(String apiUrl);
}