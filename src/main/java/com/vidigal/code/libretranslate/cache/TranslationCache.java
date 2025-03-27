package com.vidigal.code.libretranslate.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe implementation of the translation cache service.
 * <p>
 * Features:
 * - Thread-safe implementation using ConcurrentHashMap
 * - Configurable cache size and expiration
 * - Automatic periodic cache cleanup
 * - Performance metrics tracking
 */
public class TranslationCache implements TranslationCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationCache.class);
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    private static final long DEFAULT_CACHE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 60 * 1000; // 1 minute
    /**
     * Flag to enable/disable detailed logging
     */
    public static boolean DETAILED_LOGGING = false;
    private final Map<String, CacheEntry> cache;
    private final int maxCacheSize;
    private final long cacheExpirationMs;
    private final long cleanupIntervalMs;

    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final ScheduledExecutorService cleanupService;

    /**
     * Constructs a new TranslationCache with default settings.
     * - Max cache size: 1000 entries
     * - Cache expiration: 5 minutes
     * - Cleanup interval: 1 minute
     */
    public TranslationCache() {
        this(DEFAULT_MAX_CACHE_SIZE, DEFAULT_CACHE_EXPIRATION_MS, DEFAULT_CLEANUP_INTERVAL_MS);
    }

    /**
     * Constructs a new TranslationCache with custom settings.
     *
     * @param maxCacheSize      Maximum number of entries in the cache
     * @param cacheExpirationMs Cache entry expiration time in milliseconds
     */
    public TranslationCache(int maxCacheSize, long cacheExpirationMs) {
        this(maxCacheSize, cacheExpirationMs, DEFAULT_CLEANUP_INTERVAL_MS);
    }

    /**
     * Constructs a new TranslationCache with fully customized settings.
     *
     * @param maxCacheSize      Maximum number of entries in the cache
     * @param cacheExpirationMs Cache entry expiration time in milliseconds
     * @param cleanupIntervalMs Interval between cache cleanup operations in milliseconds
     */
    public TranslationCache(int maxCacheSize, long cacheExpirationMs, long cleanupIntervalMs) {
        validateParameters(maxCacheSize, cacheExpirationMs, cleanupIntervalMs);

        this.maxCacheSize = maxCacheSize;
        this.cacheExpirationMs = cacheExpirationMs;
        this.cleanupIntervalMs = cleanupIntervalMs;
        this.cache = new ConcurrentHashMap<>(maxCacheSize);
        this.cleanupService = initializeCleanupService();

        LOGGER.debug("Initialized translation cache: maxSize={}, expirationTime={}ms, cleanupInterval={}ms",
                maxCacheSize, cacheExpirationMs, cleanupIntervalMs);
    }

    /**
     * Validates constructor parameters.
     */
    private void validateParameters(int maxCacheSize, long cacheExpirationMs, long cleanupIntervalMs) {
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("Max cache size must be positive, got: " + maxCacheSize);
        }
        if (cacheExpirationMs <= 0) {
            throw new IllegalArgumentException("Cache expiration must be positive, got: " + cacheExpirationMs);
        }
        if (cleanupIntervalMs <= 0) {
            throw new IllegalArgumentException("Cleanup interval must be positive, got: " + cleanupIntervalMs);
        }
    }

    /**
     * Initializes a scheduled service to clean up expired cache entries.
     *
     * @return ScheduledExecutorService for periodic cache cleanup
     */
    private ScheduledExecutorService initializeCleanupService() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("TranslationCacheCleanupThread");
            return t;
        });

        service.scheduleAtFixedRate(this::cleanExpiredEntries,
                cleanupIntervalMs,
                cleanupIntervalMs,
                TimeUnit.MILLISECONDS);

        return service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateCacheKey(String text, String sourceLanguage, String targetLanguage) {
        return String.format("%s:%s:%s",
                sourceLanguage.toLowerCase(),
                targetLanguage.toLowerCase(),
                text.length() > 100 ? text.substring(0, 100).hashCode() + ":" + text.hashCode() : text.hashCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> get(String cacheKey) {
        CacheEntry entry = cache.get(cacheKey);
        if (entry == null) {
            cacheMisses.incrementAndGet();
            return Optional.empty();
        }

        if (isEntryExpired(entry)) {
            cache.remove(cacheKey);
            cacheMisses.incrementAndGet();
            return Optional.empty();
        }

        cacheHits.incrementAndGet();
        return Optional.of(entry.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(String cacheKey, String translatedText) {
        if (translatedText == null) {
            return; // Don't cache null values
        }

        if (cache.size() >= maxCacheSize) {
            removeOldestEntry();
        }

        cache.put(cacheKey, new CacheEntry(translatedText));
    }

    /**
     * Removes the oldest entry when cache reaches maximum size.
     */
    private void removeOldestEntry() {
        long oldestTimestamp = Long.MAX_VALUE;
        String oldestKey = null;

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().getTimestamp() < oldestTimestamp) {
                oldestTimestamp = entry.getValue().getTimestamp();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            CacheEntry removed = cache.remove(oldestKey);
            LOGGER.trace("Removed oldest cache entry: key={}, age={}ms",
                    oldestKey, System.currentTimeMillis() - removed.getTimestamp());
        }
    }

    /**
     * Removes expired cache entries.
     */
    private void cleanExpiredEntries() {
        try {
            long now = System.currentTimeMillis();
            int initialSize = cache.size();

            cache.entrySet().removeIf(entry -> {
                boolean expired = (now - entry.getValue().getTimestamp()) > cacheExpirationMs;
                return expired;
            });

            int removedCount = initialSize - cache.size();
            if (removedCount > 0) {
                LOGGER.debug("Cleaned up {} expired cache entries", removedCount);
            }
        } catch (Exception e) {
            LOGGER.error("Error during cache cleanup", e);
        }
    }

    /**
     * Checks if a cache entry has expired.
     *
     * @param entry Cache entry to check
     * @return True if the entry has expired, false otherwise
     */
    private boolean isEntryExpired(CacheEntry entry) {
        return (System.currentTimeMillis() - entry.getTimestamp()) > cacheExpirationMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        int size = cache.size();
        cache.clear();
        LOGGER.debug("Cache cleared, removed {} entries", size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCacheHits() {
        return cacheHits.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        LOGGER.debug("Cache metrics reset");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (cleanupService != null && !cleanupService.isShutdown()) {
            cleanupService.shutdown();
            try {
                if (!cleanupService.awaitTermination(1, TimeUnit.SECONDS)) {
                    cleanupService.shutdownNow();
                }
                LOGGER.debug("Cache cleanup service shut down");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while shutting down cache cleanup service", e);
            }
        }
    }
}