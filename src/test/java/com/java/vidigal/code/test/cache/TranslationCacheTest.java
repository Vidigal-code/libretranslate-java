package com.java.vidigal.code.test.cache;

import com.java.vidigal.code.request.Translation;
import com.java.vidigal.code.request.TranslationResponse;
import com.java.vidigal.code.utilities.cache.TranslationCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link TranslationCache} class, verifying caching, eviction, thread safety,
 * and persistent storage functionality.
 * <p>
 * Designed for JDK 21, using a fixed {@link Clock} for deterministic time-based tests.
 */
class TranslationCacheTest {

    private final String persistentCachePath = "target/test-cache.json";
    private TranslationCache cache;
    private Clock fixedClock;

    /**
     * Sets up the test environment before each test. Initializes a fixed clock for deterministic
     * time-based tests, creates a new cache instance, and ensures the persistent cache file is deleted.
     */
    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        File targetDir = new File("target");
        if (!targetDir.exists()) {
            assertTrue(targetDir.mkdirs(), "Failed to create target directory");
        }
        cache = new TranslationCache(100, 2, false, null, fixedClock);
    }

    /**
     * Cleans up after each test by clearing the cache and deleting the persistent cache file.
     */
    @AfterEach
    void tearDown() {
        cache.clear();
        File cacheFile = new File(persistentCachePath);
        if (cacheFile.exists()) {
            assertTrue(cacheFile.delete(), "Failed to delete persistent cache file");
        }
    }

    /**
     * Tests that a translation response can be cached and retrieved successfully, verifying
     * cache hit counters and size.
     */
    @Test
    void shouldCacheAndRetrieveTranslation() {

        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);

        TranslationResponse retrieved = cache.get("Hello", "es", null);

        assertNotNull(retrieved, "Retrieved response should not be null");
        assertEquals("Hola", retrieved.getTranslations().get(0).getText(), "Retrieved text should match");

        assertEquals(1, cache.getCacheHits(), "Cache hits should be 1");
        assertEquals(0, cache.getCacheMisses(), "Cache misses should be 0");
        assertEquals(1, cache.getCacheSize(), "Cache size should be 1");
    }

    /**
     * Tests that retrieving a non-cached translation returns null and increments the miss counter.
     */
    @Test
    void shouldReturnNullForNonCachedTranslation() {

        TranslationResponse retrieved = cache.get("Hello", "es", null);
        assertNull(retrieved, "Non-cached response should be null");
        assertEquals(0, cache.getCacheHits(), "Cache hits should be 0");
        assertEquals(1, cache.getCacheMisses(), "Cache misses should be 1");

    }

    /**
     * Tests that an expired cache entry is evicted when retrieved after the TTL, resulting in a miss.
     */
    @Test
    void shouldEvictExpiredEntry() {

        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);

        // Advance clock beyond TTL (100ms)
        fixedClock = Clock.offset(fixedClock, java.time.Duration.ofMillis(101));
        cache = new TranslationCache(100, 2, false, null, fixedClock);

        TranslationResponse retrieved = cache.get("Hello", "es", null);

        assertNull(retrieved, "Expired response should be null");
        assertEquals(0, cache.getCacheHits(), "Cache hits should be 0");
        assertEquals(1, cache.getCacheMisses(), "Cache misses should be 1");
        assertEquals(0, cache.getCacheSize(), "Cache size should be 0");
    }

    /**
     * Tests that the least recently used entry is evicted when the cache exceeds its maximum size.
     */
    @Test
    void shouldEvictLruWhenOverCapacity() {

        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));

        cache.put("Text1", "es", null, response);
        cache.put("Text2", "es", null, response);
        cache.put("Text3", "es", null, response);

        TranslationResponse retrieved = cache.get("Text1", "es", null);
        assertNull(retrieved, "LRU entry should be evicted");
        assertEquals(2, cache.getCacheSize(), "Cache size should be 2");
    }

    /**
     * Tests thread safety by performing concurrent put and get operations, ensuring consistent cache state.
     *
     * @throws InterruptedException if the test is interrupted while waiting for threads to complete.
     */
    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        cache = new TranslationCache(3_600_000, 1000, false, null, fixedClock);
        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                cache.put("Hello", "es", null, response);
                latch.countDown();
            });
            executor.submit(() -> {
                cache.get("Hello", "es", null);
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All tasks should complete within 5 seconds");
        assertTrue(cache.getCacheHits() > 0, "At least one cache hit should occur");
        assertEquals(1, cache.getCacheSize(), "Cache should contain exactly one entry");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");
    }

    /**
     * Tests that clearing the cache removes all entries and resets counters.
     */
    @Test
    void shouldClearCache() {

        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);

        cache.clear();

        assertEquals(0, cache.getCacheSize(), "Cache size should be 0 after clear");
        assertEquals(0, cache.getCacheHits(), "Cache hits should be 0 after clear");
        assertEquals(0, cache.getCacheMisses(), "Cache misses should be 0 after clear");
    }

    /**
     * Tests that the cache hit ratio is calculated correctly based on hits and misses.
     */
    @Test
    void shouldCalculateCacheHitRatio() {
        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);
        cache.get("Hello", "es", null); // Hit
        cache.get("World", "es", null); // Miss

        assertEquals(0.5, cache.getCacheHitRatio(), 0.01, "Cache hit ratio should be 0.5");
    }

    /**
     * Tests persistent cache functionality by saving an entry to disk, creating a new cache instance,
     * and verifying that the entry is loaded correctly.
     *
     * @throws IOException if an I/O error occurs while reading the cache file.
     */
    @Test
    void shouldPersistAndLoadCache() throws IOException {
        cache = new TranslationCache(3_600_000, 2, true, persistentCachePath, fixedClock);
        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);

        // Verify that the cache file was created
        File cacheFile = new File(persistentCachePath);
        assertTrue(cacheFile.exists(), "Cache file should exist after put");

        // Create a new cache instance to simulate restart
        TranslationCache newCache = new TranslationCache(3_600_000, 2, true, persistentCachePath, fixedClock);

        TranslationResponse retrieved = newCache.get("Hello", "es", null);
        assertNotNull(retrieved, "Retrieved response should not be null");
        assertEquals("Hola", retrieved.getTranslations().get(0).getText(), "Retrieved text should match");
        assertEquals(1, newCache.getCacheSize(), "New cache should contain one entry");
    }

    /**
     * Tests that clearing the cache deletes the persistent cache file.
     */
    @Test
    void shouldClearPersistentCacheFile() {
        cache = new TranslationCache(3_600_000, 2, true, persistentCachePath, fixedClock);
        TranslationResponse response = new TranslationResponse(List.of(new Translation("Hola", "en")));
        cache.put("Hello", "es", null, response);

        File cacheFile = new File(persistentCachePath);
        assertTrue(cacheFile.exists(), "Cache file should exist after put");
        cache.clear();
        assertFalse(cacheFile.exists(), "Cache file should be deleted after clear");
    }
}