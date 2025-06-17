package com.java.vidigal.code.example;

import com.java.vidigal.code.LibreTranslatePlugin;
import com.java.vidigal.code.client.LibreTranslateClientImpl;
import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.utilities.config.LibreTranslateConfig;
import com.java.vidigal.code.utilities.ratelimit.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Example class demonstrating the usage of {@link LibreTranslatePlugin} for synchronous and asynchronous translations
 * with the LibreTranslate API, including configuration, cache monitoring, and resource management.
 */
public class LibreTranslateExample {

    private static final Logger logger = LoggerFactory.getLogger(LibreTranslateExample.class);

    /**
     * Main method to demonstrate the functionality of {@link LibreTranslatePlugin}.
     * Shows how to:
     * <ul>
     *     <li>Create plugins with basic and advanced configurations</li>
     *     <li>Perform synchronous and asynchronous translations</li>
     *     <li>Monitor cache statistics using advanced monitoring</li>
     *     <li>Use automatic thread closure for resource cleanup</li>
     * </ul>
     *
     * @param args Command-line arguments (not used).
     * @throws Exception If an error occurs during translation or API communication.
     */
    public static void main(String[] args) throws LibreTranslateException, InterruptedException, ExecutionException {

        // Initialize a LibreTranslatePlugin with basic configuration (API URL and auth key)
        LibreTranslatePlugin plugin = new LibreTranslatePlugin("https://translate.fedilab.app/translate",
                "unknown");


        List<String> texts = new ArrayList<>();
        texts.add("Hello World");
        texts.add("hi");
        // Perform synchronous translation with specified source and target languages
        String translatedOne = String.valueOf(plugin.translateBatch(texts, "es", "en"));
        System.out.println("Translated text One: " + translatedOne);

        // Perform synchronous translation with auto-detected source language
        String translatedTwo = plugin.translateText("Hello, world!", "es", "pt");
        System.out.println("Translated text Two: " + translatedTwo);


        // Initialize a LibreTranslatePlugin with advanced configuration using LibreTranslateConfig builder
        LibreTranslatePlugin pluginConfig = new LibreTranslatePlugin(LibreTranslateConfig.builder()
                .apiUrl("https://translate.fedilab.app/translate")
                .apiKey("unknown")
                .connectionTimeout(5000)    // Set connection timeout to 5 seconds
                .socketTimeout(10000)       // Set socket timeout to 10 seconds
                .maxRequestsPerSecond(10)   // Limit to 10 requests per second
                .maxRetries(3)              // Retry failed requests up to 3 times
                .rateLimitCooldown(5000)    // 5-second cooldown if rate limit is hit
                .enableRetry(true)          // Enable automatic retries
                .enableCache(true)          // Enable translation caching
                .closedThreadAuto(true)     // Enable automatic thread closure
                .build());


        // Perform synchronous translation to French
        String resultConfig = pluginConfig.translateText("Hello", "pt");
        System.out.println("Translated text Config One: " + resultConfig);

        // Perform asynchronous translation to French with uppercase transformation
        CompletableFuture<String> futureConfig = pluginConfig.translateTextAsync("Hello", "pt")
                .thenApply(String::toUpperCase);
        System.out.println("Translated text Config Two (Async): " + futureConfig.get());

        // Monitor cache statistics for the configured plugin
        LibreTranslateClientImpl.CacheMonitoringStats stats = pluginConfig.getMonitoringStats().cacheStats();

        if (stats != null) {
            System.out.println("\nCache Monitoring Statistics:");
            System.out.println("  Hits: " + stats.cacheHits());
            System.out.println("  Misses: " + stats.cacheMisses());
            System.out.println("  Size: " + stats.cacheSize());
            System.out.println("  Entries: " + stats.cacheEntries().size());

        }

        TokenBucketRateLimiter.RateLimiterStats rateLimiterStats = pluginConfig.getMonitoringStats().rateLimiterStats();

        if (rateLimiterStats != null) {
            System.out.println("\nRateLimit  Statistics:");
            System.out.println("  currentTokens: " + rateLimiterStats.currentTokens());
            System.out.println("  capacity: " + rateLimiterStats.capacity());
            System.out.println("refillCount: " + rateLimiterStats.refillCount() + "\n");
        }

        pluginConfig.clearCache();



        /* Clean up resources (optional if closedThreadAuto is enabled)
        pluginConfig.shutdown();
        plugin.shutdown(); */
    }
}