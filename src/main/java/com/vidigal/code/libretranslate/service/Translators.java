package com.vidigal.code.libretranslate.service;

import com.vidigal.code.libretranslate.config.LibreTranslateConfig;

/**
 * Utility class providing easy access to translator services.
 * <p>
 * This class follows the Facade pattern, providing simpler access
 * to the translator service creation functionality.
 */
public final class Translators {

    private static final TranslatorServiceFactory DEFAULT_FACTORY = new DefaultTranslatorServiceFactory();

    /**
     * Private constructor to prevent instantiation.
     */
    private Translators() {
        throw new AssertionError("Utility class, do not instantiate");
    }

    /**
     * Gets the default TranslatorServiceFactory implementation.
     *
     * @return The default TranslatorServiceFactory
     */
    public static TranslatorServiceFactory factory() {
        return DEFAULT_FACTORY;
    }

    /**
     * Creates a new TranslatorService with the specified API URL and key.
     *
     * @param apiUrl LibreTranslate API URL
     * @param apiKey API key for the LibreTranslate service (can be null for public APIs)
     * @return TranslatorService instance
     */
    public static TranslatorService create(String apiUrl, String apiKey) {
        return DEFAULT_FACTORY.create(apiUrl, apiKey);
    }

    /**
     * Creates a new TranslatorService with the specified configuration.
     *
     * @param config LibreTranslate configuration
     * @return TranslatorService instance
     */
    public static TranslatorService create(LibreTranslateConfig config) {
        return DEFAULT_FACTORY.create(config);
    }

    /**
     * Tests the connection to the specified API URL.
     *
     * @param apiUrl The URL of the API to test
     * @return True if the connection is successful, false otherwise
     */
    public static boolean testConnection(String apiUrl) {
        return DEFAULT_FACTORY.testConnection(apiUrl);
    }
} 