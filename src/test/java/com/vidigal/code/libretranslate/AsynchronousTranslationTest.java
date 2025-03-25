package com.vidigal.code.libretranslate;

import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.service.Translators;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Tests for asynchronous translation functionality.
 */
public class AsynchronousTranslationTest {

    // Use a public LibreTranslate instance for testing
    private static final String API_URL = "https://translate.fedilab.app/translate";
    
    private TranslatorService translatorService;
    
    @Before
    public void setUp() throws Exception {
        // Check if the API is accessible before running any tests
        if (!Translators.testConnection(API_URL)) {
            fail("API connection failed. Cannot proceed with tests.");
        }
        
        // Initialize the translator service
        translatorService = Translators.create(API_URL, null);
    }
    
    @After
    public void tearDown() throws Exception {
        if (translatorService != null) {
            translatorService.close();
        }
    }

    /**
     * Test 1: Basic asynchronous translation.
     * Verifies that asynchronous translation correctly completes and returns the expected result.
     */
    @Test
    public void testBasicAsynchronousTranslation() throws Exception {
        // Translate a simple English text to Spanish asynchronously
        String original = "Hello world";
        CompletableFuture<String> future = translatorService.translateAsync(original, "en", "es");
        
        // Wait for the result
        String translated = future.get(10, TimeUnit.SECONDS);
        
        // Verify the result is not null or empty
        assertNotNull("Translation result should not be null", translated);
        assertFalse("Translation result should not be empty", translated.isEmpty());
        
        // Verify the result is different from the original (has been translated)
        assertNotEquals("Translation should be different from original text", original, translated);
        
        System.out.println("Original (en): " + original);
        System.out.println("Translated (es): " + translated);
    }
    
    /**
     * Test 2: Multiple parallel asynchronous translations.
     * Verifies that multiple translations can be executed concurrently.
     */
    @Test
    public void testMultipleParallelTranslations() throws Exception {
        // Text to translate to multiple languages
        String original = "Good morning";
        
        // Target languages
        String[] targetLanguages = {"es", "fr", "de", "it"};
        
        // Create futures for each translation
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String language : targetLanguages) {
            CompletableFuture<String> future = translatorService.translateAsync(original, "en", language);
            futures.add(future);
        }
        
        // Wait for all translations to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.get(20, TimeUnit.SECONDS);
        
        // Verify each result
        for (int i = 0; i < targetLanguages.length; i++) {
            String translated = futures.get(i).get();
            
            // Verify the result is not null or empty
            assertNotNull("Translation to " + targetLanguages[i] + " should not be null", translated);
            assertFalse("Translation to " + targetLanguages[i] + " should not be empty", translated.isEmpty());
            
            System.out.println("Translated to " + targetLanguages[i] + ": " + translated);
        }
    }
    
    /**
     * Test 3: Asynchronous translation error handling.
     * Verifies that errors in asynchronous translations are properly propagated.
     */
    @Test
    public void testAsynchronousTranslationErrorHandling() {
        // Try translating with an invalid target language code
        CompletableFuture<String> future = translatorService.translateAsync("Hello world", "en", "invalid_language");
        
        // Verify that the future completes exceptionally
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("Should have thrown ExecutionException");
        } catch (Exception exception) {
            assertTrue("Exception should be ExecutionException", exception instanceof ExecutionException);
            
            // Verify the cause is a TranslationException
            Throwable cause = exception.getCause();
            assertTrue("Exception cause should be a TranslationException or CompletionException", 
                       cause instanceof TranslationException || cause instanceof CompletionException);
            
            // If it's a CompletionException, check its cause
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
                assertTrue("Nested cause should be a TranslationException", 
                          cause instanceof TranslationException);
            }
            
            // Verify the exception message contains information about the invalid language
            String exceptionMessage = cause.getMessage();
            assertTrue(
                "Exception should indicate the invalid language",
                exceptionMessage.contains("invalid_language") || 
                exceptionMessage.contains("not supported")
            );
            
            System.out.println("Expected exception: " + exceptionMessage);
        }
    }
} 