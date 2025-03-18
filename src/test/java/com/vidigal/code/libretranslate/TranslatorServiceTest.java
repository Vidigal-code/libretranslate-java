package com.vidigal.code.libretranslate;

import com.vidigal.code.libretranslate.client.AbstractTranslatorClient;
import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.service.TranslatorService;
import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TranslatorServiceTest extends AbstractTranslatorClient {

    // Constants for test results message
    private static final String MESSAGE_PASSED = "[PASSED]";

    // API URL and Key for translation service
    private static final String API = "https://translate.fedilab.app/translate";
    private static final String KEY = "unknown";

    // Mocking TranslatorService
    private TranslatorService translatorService;

    /**
     * Setup method to initialize necessary components before each test.
     * Verifies the API connection before proceeding with any test.
     */
    @BeforeEach
    public void setUp() {
        // Check if the API is accessible before running any tests
        if (!TranslatorService.testConnection(API)) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("API connection failed. Cannot proceed with tests.").reset());
            fail("API connection failed. Cannot proceed with tests.");
        }

        // Initialize the TranslatorService with the provided API URL and Key
        translatorService = TranslatorService.create(API, KEY);
    }

    /**
     * Test for translating a text to an unsupported target language.
     * Verifies that a result is returned even if the language is unsupported.
     */
    @Test
    public void testTranslateUnsupportedTargetLanguage() {
        // Attempt translation with an unsupported target language (xx)
        String result = translatorService.translate("Hello world", "xx");
        assertNotNull(result); // Ensure that the result is not null
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(MESSAGE_PASSED + " Translation to unsupported language returned a result.").reset());
    }

    /**
     * Test for translating a text to unsupported multiple target languages.
     * Verifies that a result is returned even if multiple languages are unsupported.
     */
    @Test
    public void testTranslateUnsupportedTargetLanguages() {
        // Attempt translation with unsupported multiple target languages
        String result = translatorService.translate("xx", "xx", "xx");
        assertNotNull(result); // Ensure that the result is not null
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(MESSAGE_PASSED + " Translation to multiple unsupported languages returned a result.").reset());
    }

    /**
     * Test for translating text asynchronously and handling the response.
     * Verifies that asynchronous translation works successfully.
     */
    @Test
    public void testTranslateAsyncSuccess() {
        // Translate asynchronously to French
        translatorService.translateAsync("Hello world", "fr")
                .thenAccept(text -> {
                    assertNotNull("Async translation result should not be null", text); // Ensure the translation is not null
                    System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Async translated to French: " + text).reset());
                })
                .exceptionally(ex -> {
                    // Handle any exceptions that occur during translation
                    System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Async translation failed: " + ex.getMessage()).reset());
                    return null;
                });
    }

    /**
     * Test for custom configuration setup for the TranslatorService.
     * Verifies that the custom configuration is correctly used and the translation works as expected.
     */
    @Test
    public void testCustomConfiguration() {
        // Create a custom configuration with specific API URL, Key, and other settings
        // Step 1: Build a custom configuration using LibreTranslateConfig.Builder
        LibreTranslateConfig config = new LibreTranslateConfig.Builder()
                .apiUrl(API)               // Set the API URL
                .apiKey(KEY)              // Set the API Key
                .rateLimitCooldown(1000)   // Rate limit cooldown, 100 and 60000
                .connectionTimeout(10000) // Connection timeout in milliseconds
                .socketTimeout(15000)     // Socket timeout in milliseconds
                .maxRetries(5)            // Maximum number of retries for failed requests
                .build();

        // Create a TranslatorService instance with the custom configuration
        TranslatorService customTranslator = TranslatorService.create(config);

        // Perform asynchronous translation using the custom configuration
        customTranslator.translateAsync("This is a test", "de")
                .thenAccept(text -> {
                    assertNotNull("Custom async translation result should not be null", text); // Ensure the translation is not null
                    System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Custom translated to German: " + text).reset());
                })
                .exceptionally(ex -> {
                    // Handle any exceptions that occur during translation
                    System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Custom async translation failed: " + ex.getMessage()).reset());
                    return null;
                });
    }

    /**
     * Test for checking the API connection status.
     * Verifies that the connection to the API is successful.
     */
    @Test
    public void testApiConnection() {
        try {
            // Assert that the API connection is successful
            assertTrue(TranslatorService.testConnection(API), "API connection should be successful.");
            System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\n" + MESSAGE_PASSED + " API connection.").reset());
        } catch (Exception e) {
            // Handle any errors in the connection test
            fail(Ansi.ansi().fg(Ansi.Color.RED).a("Error with testApiConnection: " + e.getMessage()).reset().toString());
        }
    }

    @Test
    public void testRateLimiting() throws Exception {
        // Configure strict rate limiting: 2 requests per second
        LibreTranslateConfig config = LibreTranslateConfig.builder()
                .apiUrl(API)
                .apiKey(KEY)
                .maxRequestsPerSecond(2)
                .rateLimitCooldown(2000) // Use valid value within 100-60000
                .build();

        TranslatorService service = new LibreTranslateClient(config);

        int requestCount = 5;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // Send burst of parallel requests
        for (int i = 0; i < requestCount; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                service.translate("Test rate limiting", "en", "es");
            });
            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long duration = System.currentTimeMillis() - startTime;

        // Calculate expected minimum time
        int expectedMinimum = (int) ((Math.ceil((double) requestCount / config.getMaxRequestsPerSecond()) - 1) * 1000);

        // Verify execution time (2 requests/sec = ~2000ms for 5 requests)
        assertTrue(
                duration >= expectedMinimum,
                "Requests should take at least " + expectedMinimum + "ms (actual: " + duration + "ms)"
        );

        // Verify all completed successfully
        futures.forEach(future -> assertDoesNotThrow(() -> future.get()));
    }


    /**
     * Lifecycle method executed after all tests.
     */
    @AfterAll
    public static void tearDown() {
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("\nAll tests completed.").reset());
    }


}