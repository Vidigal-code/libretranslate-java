package com.vidigal.code.libretranslate;

import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.service.Translators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for synchronous translation functionality.
 */
public class SynchronousTranslationTest {

    // Use a public LibreTranslate instance for testing
    private static final String API_URL = "https://translate.fedilab.app/translate";
    
    private TranslatorService translatorService;
    
    @BeforeEach
    public void setUp() throws Exception {
        // Check if the API is accessible before running any tests
        if (!Translators.testConnection(API_URL)) {
            fail("API connection failed. Cannot proceed with tests.");
        }
        
        // Initialize the translator service
        translatorService = Translators.create(API_URL, null);
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (translatorService != null) {
            translatorService.close();
        }
    }

    /**
     * Test 1: Basic translation with source and target languages explicitly specified.
     * Verifies that simple text is correctly translated between two languages.
     */
    @Test
    public void testBasicTranslationWithSourceAndTarget() {
        // Translate a simple English text to Spanish
        String original = "Hello world";
        String translated = translatorService.translate(original, "en", "es");
        
        // Verify the result is not null or empty
        assertNotNull(translated, "Translation result should not be null");
        assertFalse(translated.isEmpty(), "Translation result should not be empty");
        
        // Verify the result is different from the original (has been translated)
        assertNotEquals(original, translated, "Translation should be different from original text");
        
        System.out.println("Original (en): " + original);
        System.out.println("Translated (es): " + translated);
    }
    
    /**
     * Test 2: Translation with automatic source language detection.
     * Verifies that the service correctly detects the source language and translates.
     */
    @Test
    public void testTranslationWithAutoDetection() {
        // French text to be translated to English with auto-detection
        String frenchText = "Bonjour le monde";
        String translated = translatorService.translate(frenchText, "auto", "en");
        
        // Verify the result is not null or empty
        assertNotNull(translated, "Translation result should not be null");
        assertFalse(translated.isEmpty(), "Translation result should not be empty");
        
        // Verify the result contains expected English words
        assertTrue(
            translated.toLowerCase().contains("hello") || 
            translated.toLowerCase().contains("world"),
            "Translation should contain expected English words"
        );
        
        System.out.println("Original (fr): " + frenchText);
        System.out.println("Translated (en): " + translated);
    }
    
    /**
     * Test 3: Translation with error handling for invalid language.
     * Verifies that the service correctly handles errors for invalid language codes.
     */
    @Test
    public void testTranslationWithInvalidLanguage() {
        // Try translating with an invalid target language code
        Exception exception = assertThrows(TranslationException.class, () -> {
            translatorService.translate("Hello world", "en", "invalid_language");
        });
        
        // Verify the exception message contains information about the invalid language
        String exceptionMessage = exception.getMessage();
        assertTrue(
            exceptionMessage.contains("invalid_language") || 
            exceptionMessage.contains("not supported"),
            "Exception should indicate the invalid language"
        );
        
        System.out.println("Expected exception: " + exceptionMessage);
    }
} 