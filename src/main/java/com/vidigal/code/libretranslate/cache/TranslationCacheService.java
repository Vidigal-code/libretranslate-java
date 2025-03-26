package com.vidigal.code.libretranslate.cache;

import java.util.Optional;

/**
 * Interface for translation caching service.
 * <p>
 * This interface defines the contract for a service that provides caching
 * functionality for translations. It follows the Interface Segregation Principle
 * by focusing only on the core caching operations.
 */
public interface TranslationCacheService extends AutoCloseable {

    /**
     * Generates a cache key for a specific translation.
     *
     * @param text           Text to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return Generated cache key
     */
    String generateCacheKey(String text, String sourceLanguage, String targetLanguage);

    /**
     * Retrieves a cached translation.
     *
     * @param cacheKey Key to look up in the cache
     * @return Optional containing the cached translation, or empty if not found or expired
     */
    Optional<String> get(String cacheKey);

    /**
     * Stores a translation in the cache.
     *
     * @param cacheKey       Key to store the translation under
     * @param translatedText The translated text to cache
     */
    void put(String cacheKey, String translatedText);

    /**
     * Clears the entire cache.
     */
    void clear();

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
     * Resets all cache metrics.
     */
    void clearMetrics();
} 