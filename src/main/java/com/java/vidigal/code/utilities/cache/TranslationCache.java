package com.java.vidigal.code.utilities.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.vidigal.code.request.TranslationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe cache for storing {@link TranslationResponse} objects.
 * <p>
 * Supports time-to-live (TTL) expiration, least-recently-used (LRU) eviction with O(1) complexity,
 * and optional persistent storage to disk using JSON. Uses a synchronized {@link LinkedHashMap}
 * with access-order enabled for efficient LRU eviction and thread safety, with periodic cleanup
 * of expired entries via a {@link ScheduledExecutorService}.
 * </p>
 *
 * @author Vidigal
 */
public class TranslationCache {

    private static final Logger logger = LoggerFactory.getLogger(TranslationCache.class);
    private static final long LOG_FREQUENCY = 100;
    private final Map<String, CacheEntry> cache;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong accessCount = new AtomicLong(0);
    private final long ttlMillis;
    private final int maxCacheSize;
    private final ScheduledExecutorService cleanupScheduler;
    private final boolean persistentCacheEnabled;
    private final String persistentCachePath;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructs a cache with the specified parameters.
     *
     * @param ttlMillis              time-to-live in milliseconds; 0 disables TTL
     * @param maxCacheSize           maximum number of entries, must be positive
     * @param persistentCacheEnabled whether to enable disk persistence
     * @param persistentCachePath    file path for persistence, required if enabled
     * @throws IllegalArgumentException if parameters are invalid
     */
    public TranslationCache(long ttlMillis, int maxCacheSize, boolean persistentCacheEnabled, String persistentCachePath) {
        this(ttlMillis, maxCacheSize, persistentCacheEnabled, persistentCachePath, Clock.systemUTC());
    }

    /**
     * Constructs a cache with a custom clock for testing.
     *
     * @param ttlMillis              time-to-live in milliseconds; 0 disables TTL
     * @param maxCacheSize           maximum number of entries, must be positive
     * @param persistentCacheEnabled whether to enable disk persistence
     * @param persistentCachePath    file path for persistence, required if enabled
     * @param clock                  clock for timestamp calculations
     * @throws IllegalArgumentException if parameters or clock are invalid
     */
    public TranslationCache(long ttlMillis, int maxCacheSize, boolean persistentCacheEnabled, String persistentCachePath, Clock clock) {
        if (ttlMillis < 0) {
            throw new IllegalArgumentException("TTL must be non-negative");
        }
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("Maximum cache size must be positive");
        }
        if (persistentCacheEnabled && (persistentCachePath == null || persistentCachePath.isBlank())) {
            throw new IllegalArgumentException("Persistent cache path must not be null or blank when persistence is enabled");
        }
        this.ttlMillis = ttlMillis;
        this.maxCacheSize = maxCacheSize;
        this.persistentCacheEnabled = persistentCacheEnabled;
        this.persistentCachePath = persistentCachePath;
        this.objectMapper = new ObjectMapper();
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxCacheSize;
            }
        });
        if (persistentCacheEnabled) {
            loadCacheFromDisk();
        }
        this.cleanupScheduler = ttlMillis > 0 ? Executors.newScheduledThreadPool(1) : null;
        if (cleanupScheduler != null) {
            long cleanupInterval = Math.max(ttlMillis / 2, 1000);
            cleanupScheduler.scheduleAtFixedRate(this::evictExpiredEntries, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
            logger.debug("Started cache cleanup scheduler with interval {}ms", cleanupInterval);
        }
    }

    /**
     * Retrieves a translation response from the cache.
     *
     * @param text       the input text
     * @param targetLang the target language code
     * @param sourceLang the source language code, or null
     * @return the cached response, or null if not found or expired
     */
    public TranslationResponse get(String text, String targetLang, String sourceLang) {
        return get(text, targetLang, sourceLang, false);
    }

    /**
     * Retrieves a translation response, optionally ignoring TTL.
     *
     * @param text       the input text
     * @param targetLang the target language code
     * @param sourceLang the source language code, or null
     * @param ignoreTtl  whether to bypass TTL checks
     * @return the cached response, or null if not found or expired (unless ignoreTtl is true)
     */
    public TranslationResponse get(String text, String targetLang, String sourceLang, boolean ignoreTtl) {
        CacheKey key = new CacheKey(text, targetLang, sourceLang);
        String keyStr = key.toString();
        TranslationResponse response = null;
        CacheEntry entry;
        synchronized (cache) {
            entry = cache.get(keyStr);
        }
        if (entry != null) {
            if (ttlMillis > 0 && !ignoreTtl && (Instant.now(clock).toEpochMilli() - entry.timestamp()) > ttlMillis) {
                synchronized (cache) {
                    cache.remove(keyStr);
                }
                cacheMisses.incrementAndGet();
                if (shouldLog()) logger.debug("Cache miss (expired) for key: {}", keyStr);
            } else {
                response = entry.response();
                cacheHits.incrementAndGet();
                if (shouldLog()) logger.debug("Cache hit for key: {}", keyStr);
            }
        } else {
            cacheMisses.incrementAndGet();
            if (shouldLog()) logger.debug("Cache miss for key: {}", keyStr);
        }
        return response;
    }

    /**
     * Stores a translation response in the cache.
     * <p>
     * Automatically evicts the least-recently-used entry if the cache is full, using O(1) LRU policy.
     * Saves to disk if persistence is enabled.
     * </p>
     *
     * @param text       the input text
     * @param targetLang the target language code
     * @param sourceLang the source language code, or null
     * @param response   the translation response to cache
     */
    public void put(String text, String targetLang, String sourceLang, TranslationResponse response) {
        CacheKey key = new CacheKey(text, targetLang, sourceLang);
        String keyStr = key.toString();
        synchronized (cache) {
            cache.put(keyStr, new CacheEntry(response, Instant.now(clock).toEpochMilli()));
        }
        if (persistentCacheEnabled) {
            saveCacheToDisk();
        }
        if (shouldLog()) logger.debug("Cached translation for key: {}", keyStr);
    }

    /**
     * Clears the cache and shuts down the cleanup scheduler.
     */
    public void clear() {
        try {
            synchronized (cache) {
                cache.clear();
            }
            cacheHits.set(0);
            cacheMisses.set(0);
            if (persistentCacheEnabled) {
                clearPersistentCache();
            }
        } finally {
            shutdownCleanupScheduler();
        }
    }

    /**
     * Retrieves all cache entries.
     *
     * @return an unmodifiable list of cache entries
     */
    public List<CacheEntry> getAllCache() {
        List<CacheEntry> entries;
        synchronized (cache) {
            entries = new ArrayList<>(cache.values());
        }
        logger.debug("Retrieved {} cache entries", entries.size());
        return List.copyOf(entries);
    }

    /**
     * Calculates the cache hit ratio.
     *
     * @return the hit ratio, or 0.0 if no accesses
     */
    public double getCacheHitRatio() {
        long total = cacheHits.get() + cacheMisses.get();
        return total == 0 ? 0.0 : (double) cacheHits.get() / total;
    }

    /**
     * Returns the number of cache hits.
     *
     * @return the total cache hits
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Returns the number of cache misses.
     *
     * @return the total cache misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Returns the current cache size.
     *
     * @return the number of entries in the cache
     */
    public int getCacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }

    /**
     * Returns the maximum cache size.
     *
     * @return the maximum number of entries
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Evicts expired cache entries based on TTL.
     */
    private void evictExpiredEntries() {
        if (ttlMillis <= 0) return;
        long currentTime = Instant.now(clock).toEpochMilli();
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp() > ttlMillis);
        }
        if (persistentCacheEnabled) {
            saveCacheToDisk();
        }
        logger.debug("Evicted expired cache entries, current size: {}", getCacheSize());
    }

    /**
     * Shuts down the cleanup scheduler gracefully.
     */
    private void shutdownCleanupScheduler() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Cleanup scheduler did not terminate in 5s, forcing shutdown");
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted during scheduler shutdown", e);
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Loads the persistent cache from disk.
     */
    private void loadCacheFromDisk() {
        if (persistentCachePath == null || persistentCachePath.isBlank()) {
            logger.warn("Persistent cache path is null or blank, skipping load");
            return;
        }
        File cacheFile = new File(persistentCachePath);
        if (!cacheFile.exists()) {
            logger.debug("No persistent cache file at {}", persistentCachePath);
            return;
        }
        try {
            TypeReference<Map<String, CacheEntry>> typeRef = new TypeReference<>() {};
            Map<String, CacheEntry> loadedCache = objectMapper.readValue(cacheFile, typeRef);
            synchronized (cache) {
                cache.putAll(loadedCache);
            }
            logger.info("Loaded {} entries from persistent cache at {}", loadedCache.size(), persistentCachePath);
        } catch (IOException e) {
            logger.error("Failed to load persistent cache from {}: {}", persistentCachePath, e.getMessage(), e);
        }
    }

    /**
     * Saves the cache to disk.
     */
    private void saveCacheToDisk() {
        if (persistentCachePath == null || persistentCachePath.isBlank()) {
            logger.warn("Persistent cache path is null or blank, skipping save");
            return;
        }
        File cacheFile = new File(persistentCachePath);
        try {
            File parentDir = cacheFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            synchronized (cache) {
                objectMapper.writeValue(cacheFile, cache);
            }
            logger.debug("Saved cache to {}", persistentCachePath);
        } catch (IOException e) {
            logger.error("Failed to save persistent cache to {}: {}", persistentCachePath, e.getMessage(), e);
        }
    }

    /**
     * Deletes the persistent cache file.
     */
    private void clearPersistentCache() {
        if (persistentCachePath == null || persistentCachePath.isBlank()) {
            logger.warn("Persistent cache path is null or blank, skipping clear");
            return;
        }
        File cacheFile = new File(persistentCachePath);
        if (cacheFile.exists()) {
            try {
                Files.delete(cacheFile.toPath());
                logger.debug("Deleted persistent cache file at {}", persistentCachePath);
            } catch (IOException e) {
                logger.error("Failed to delete persistent cache file at {}: {}", persistentCachePath, e.getMessage(), e);
            }
        }
    }

    /**
     * Determines if logging is needed based on access frequency.
     *
     * @return true if logging is required
     */
    private boolean shouldLog() {
        return accessCount.incrementAndGet() % LOG_FREQUENCY == 0;
    }

    /**
     * Record for cache key based on text and languages.
     *
     * @param textHash   hash code of the input text
     * @param targetLang target language code
     * @param sourceLang source language code, or null if unspecified
     */
    private record CacheKey(long textHash, String targetLang, String sourceLang) {
        /**
         * Creates a cache key from text and languages.
         *
         * @param text       the input text
         * @param targetLang the target language code
         * @param sourceLang the source language code, or null
         * @throws NullPointerException if text or targetLang is null
         */
        public CacheKey(String text, String targetLang, String sourceLang) {
            this(Objects.hashCode(Objects.requireNonNull(text)), Objects.requireNonNull(targetLang), sourceLang);
        }

        @Override
        public String toString() {
            return textHash + ":" + targetLang + ":" + (sourceLang != null ? sourceLang : "null");
        }
    }

    /**
     * Record for a cache entry.
     *
     * @param response  the cached translation response
     * @param timestamp the entry creation time in milliseconds
     */
    public record CacheEntry(TranslationResponse response, long timestamp) {
    }
}