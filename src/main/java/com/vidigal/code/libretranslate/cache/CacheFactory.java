package com.vidigal.code.libretranslate.cache;

/**
 * Factory for creating translation cache service instances.
 * <p>
 * This class follows the Factory pattern to provide various ways to create
 * and configure cache instances according to different requirements.
 */
public final class CacheFactory {

    /**
     * Private constructor to prevent instantiation.
     */
    private CacheFactory() {
        throw new AssertionError("Utility class, do not instantiate");
    }

    /**
     * Creates a default translation cache service.
     * <p>
     * The default cache has:
     * - 1000 maximum entries
     * - 5 minute expiration time
     * - 1 minute cleanup interval
     *
     * @return A newly created translation cache service
     */
    public static TranslationCacheService createDefault() {
        return new TranslationCache();
    }

    /**
     * Creates a translation cache service with the specified maximum size and expiration time.
     *
     * @param maxSize      The maximum number of entries in the cache
     * @param expirationMs The expiration time for cache entries in milliseconds
     * @return A newly created translation cache service
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static TranslationCacheService create(int maxSize, long expirationMs) {
        return new TranslationCache(maxSize, expirationMs);
    }

    /**
     * Creates a fully customized translation cache service.
     *
     * @param maxSize           The maximum number of entries in the cache
     * @param expirationMs      The expiration time for cache entries in milliseconds
     * @param cleanupIntervalMs The interval between cache cleanup operations
     * @return A newly created translation cache service
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static TranslationCacheService create(int maxSize, long expirationMs, long cleanupIntervalMs) {
        return new TranslationCache(maxSize, expirationMs, cleanupIntervalMs);
    }

    /**
     * Creates a small translation cache suitable for limited memory environments.
     *
     * @return A newly created small translation cache service
     */
    public static TranslationCacheService createSmall() {
        return new TranslationCache(100, 60_000, 30_000); // 100 entries, 1 minute expiration, 30s cleanup
    }

    /**
     * Creates a large translation cache suitable for high-performance environments.
     *
     * @return A newly created large translation cache service
     */
    public static TranslationCacheService createLarge() {
        return new TranslationCache(10_000, 30 * 60_000, 5 * 60_000); // 10k entries, 30 min expiration, 5min cleanup
    }
} 