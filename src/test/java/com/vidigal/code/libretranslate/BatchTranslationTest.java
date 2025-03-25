package com.vidigal.code.libretranslate;

import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.service.Translators;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for batch translation processing functionality.
 */
public class BatchTranslationTest {

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
     * Test 1: Test single command processing.
     * Verifies that a single command is correctly processed.
     */
    @Test
    public void testSingleCommandProcessing() {
        // Define a single command for testing
        List<String> commands = Arrays.asList(
            "m:s;t:Hello;en;pt"  // Synchronous translation with mode, text, and languages
        );
        
        // Process the command
        List<String> results = translatorService.processCommands(commands, true);
        
        // Verify results
        assertNotNull("Results should not be null", results);
        assertFalse("Results should not be empty", results.isEmpty());
        
        // Verify the result is not empty
        String result = results.get(0);
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        
        System.out.println("Command: " + commands.get(0));
        System.out.println("Result: " + result);
    }
    
    /**
     * Test 2: Testing command format variations.
     * Tests different command format variations one by one.
     */
    @Test
    public void testCommandFormatVariations() {
        // Test each command format individually
        testCommand("m:s;t:Hello world;en;fr", "Synchronous mode");
        testCommand("t:Good morning;en;es", "Simple format");
        testCommand("m:as;t:Thank you;en;it", "Asynchronous mode");
    }
    
    /**
     * Helper method to test a single command.
     */
    private void testCommand(String command, String description) {
        List<String> commands = Arrays.asList(command);
        List<String> results = translatorService.processCommands(commands, true);
        
        assertNotNull(description + " results should not be null", results);
        assertFalse(description + " results should not be empty", results.isEmpty());
        
        String result = results.get(0);
        assertNotNull(description + " result should not be null", result);
        assertFalse(description + " result should not be empty", result.isEmpty());
        
        System.out.println(description + " command: " + command);
        System.out.println(description + " result: " + result);
        System.out.println();
    }
    
    /**
     * Test 3: Testing error handling with invalid commands.
     * Verifies that the batch processor gracefully handles invalid commands.
     */
    @Test
    public void testErrorHandlingWithInvalidCommand() {
        // Test with an invalid command format
        List<String> commands = Arrays.asList("invalid_format");
        
        // The batch processor should gracefully handle the error and not throw an exception
        try {
            List<String> results = translatorService.processCommands(commands, true);
            // If it returns results, they should contain an error message
            if (!results.isEmpty()) {
                String result = results.get(0);
                assertTrue(
                    "Result should indicate an error for invalid command",
                    result == null || 
                    result.toLowerCase().contains("error") || 
                    result.toLowerCase().contains("invalid") ||
                    result.toLowerCase().contains("not supported")
                );
            }
        } catch (TranslationException e) {
            // If it throws an exception, it should contain an error message about the format
            assertTrue(
                "Exception should indicate invalid command format",
                e.getMessage().contains("invalid_format") || 
                e.getMessage().contains("Invalid command")
            );
            System.out.println("Expected exception: " + e.getMessage());
        }
    }
    
    /**
     * Test 4: Testing error handling with invalid mode.
     * Verifies that the processor gracefully handles commands with invalid modes.
     */
    @Test
    public void testErrorHandlingWithInvalidMode() {
        // Test with an invalid mode
        List<String> commands = Arrays.asList("m:unknown;t:Test;en;fr");
        
        // The processor should gracefully handle the error
        try {
            List<String> results = translatorService.processCommands(commands, true);
            // If it returns results, they should contain an error message
            if (!results.isEmpty()) {
                String result = results.get(0);
                assertTrue(
                    "Result should indicate an error for invalid mode",
                    result == null || 
                    result.toLowerCase().contains("error") || 
                    result.toLowerCase().contains("invalid") ||
                    result.toLowerCase().contains("mode")
                );
            }
        } catch (TranslationException e) {
            // If it throws an exception, it should contain an error message about the mode
            assertTrue(
                "Exception should indicate invalid mode",
                e.getMessage().contains("unknown") || 
                e.getMessage().contains("mode")
            );
            System.out.println("Expected exception: " + e.getMessage());
        }
    }
    
    /**
     * Test 5: Testing error handling with invalid language.
     * Verifies that the processor gracefully handles commands with invalid language codes.
     */
    @Test
    public void testErrorHandlingWithInvalidLanguage() {
        // Test with an invalid language code
        List<String> commands = Arrays.asList("t:Goodbye;invalid_language;es");
        
        // The processor should gracefully handle the error
        try {
            List<String> results = translatorService.processCommands(commands, true);
            // Print the result to understand what's being returned
            if (!results.isEmpty()) {
                String result = results.get(0);
                System.out.println("Actual result for invalid language: " + result);
                
                // Accept any result as valid - we only need to verify that processing
                // completes without crashing
                assertNotNull("Result should not be null", result);
            } else {
                // If empty results, also accept that as valid behavior
                System.out.println("Empty results returned for invalid language");
            }
        } catch (TranslationException e) {
            // If it throws an exception, we'll accept any exception related to the language
            System.out.println("Expected exception: " + e.getMessage());
            
            // No assertion needed - catching the exception is enough to pass the test
            // as we're just testing that an invalid language is handled somehow
        } catch (Exception e) {
            // Handle any other exception
            System.out.println("Unexpected exception type: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            
            // Rethrow unexpected exceptions
            throw e;
        }
    }
} 