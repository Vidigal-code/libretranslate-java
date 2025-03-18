package com.vidigal.code.example;

import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example of how to use the LibreTranslate client.
 * This class demonstrates synchronous and asynchronous translations,
 * batch processing of commands, and custom configuration options.
 */
public class TranslationExample {

    // Constants for API URL and API Key
    public static final String API = "https://translate.fedilab.app/translate";
    public static final String KEY = "unknown";

    // ANSI escape codes for colored console output
    public static final String GREEN = "\u001B[32m"; // Green text
    public static final String RESET = "\u001B[0m";  // Reset text color

    public static void main(String[] args) {

        /**
         * Example 1: Basic Usage of TranslatorService
         */

        // Create a TranslatorService instance with the API URL and API Key
        TranslatorService translator = TranslatorService.create(API, KEY);

        // Define a list of commands for batch processing
        List<String> commands = Arrays.asList(
                "m:s;t:Hello;en;pt", // Synchronous translation of "Hello" from English (en) to Portuguese (pt)
                "t:World;pt",       // Translate "World" to Portuguese (pt)
                "t:Hello;en;pt",       // Translate "Hello" en (English) to Portuguese (pt)
                "m:as;t:Goodbye;en;es" // Asynchronous translation of "Goodbye" from English (en) to Spanish (es)
        );

        // Process the commands and print the results
        System.out.println(translator.processCommands(commands, false));

        // Perform a simple synchronous translation
        String result = translator.translate("Hello world", "pt");
        System.out.println(GREEN + "Translated text: " + result + RESET);

        // Perform a synchronous translation with a specified source language
        String resultWithSource = translator.translate("Hello world", "en", "es");
        System.out.println(GREEN + "Translated to Spanish: " + resultWithSource + RESET);

        // Perform an asynchronous translation
        CompletableFuture<String> translationFuture = translator.translateAsync("Hello world", "en", "fr");

        // Handle the asynchronous result
        translationFuture
                .thenAccept(text -> {
                    System.out.println("Translation: " + text);
                })
                .exceptionally(ex -> {
                    System.err.println("Translation failed: " + ex.getMessage());
                    return null;
                });

        // Wait for the asynchronous operation to complete
        translationFuture.join();

        /**
         * Example 2: Advanced Usage with Custom Configuration
         */

        // Step 1: Build a custom configuration using LibreTranslateConfig.Builder
        LibreTranslateConfig config = new LibreTranslateConfig.Builder()
                .apiUrl(API)               // Set the API URL
                .apiKey(KEY)              // Set the API Key
                .rateLimitCooldown(1000)   // Rate limit cooldown, 100 and 60000
                .connectionTimeout(10000) // Connection timeout in milliseconds
                .socketTimeout(15000)     // Socket timeout in milliseconds
                .maxRetries(5)            // Maximum number of retries for failed requests
                .build();


        // Step 2: Create a TranslatorService instance with the custom configuration
        TranslatorService customTranslator = TranslatorService.create(config);

        // Step 3: Define a list of commands for batch processing
        List<String> commandsCustom = Arrays.asList(
                "m:s;t:Hello;en;pt", // Synchronous translation of "Hello" from English (en) to Portuguese (pt)
                "t:World;pt",       // Translate "World" to Portuguese (pt)
                "t:Hello;en;pt",       // Translate "Hello" en (English) to Portuguese (pt)
                "m:as;t:Goodbye;en;es" // Asynchronous translation of "Goodbye" from English (en) to Spanish (es)
        );

        // Step 4: Process the commands and print the results
        System.out.println("customTranslator" + " " + customTranslator.processCommands(commandsCustom, false));

        // Step 5: Perform a simple synchronous translation
        String resultCustom = customTranslator.translate("Hello world", "pt"); // Translate to Portuguese
        System.out.println(GREEN + "customTranslator" + " " + "Translated text: " + resultCustom + RESET);

        // Step 6: Perform a synchronous translation with a specified source language
        String resultWithSourceCustom = customTranslator.translate("Hello world", "en", "es"); // Translate from English to Spanish
        System.out.println(GREEN + "customTranslator" + " " + "Translated to Spanish: " + resultWithSourceCustom + RESET);

        // Step 7: Perform an asynchronous translation
        CompletableFuture<String> translationFutureCustom = customTranslator.translateAsync("Hello world", "en", "fr");

        // Handle the asynchronous result
        translationFutureCustom
                .thenAccept(text -> {
                    System.out.println("customTranslator" + " " + "Translation: " + text);
                })
                .exceptionally(ex -> {
                    System.err.println("customTranslator" + " " + "Translation failed: " + ex.getMessage());
                    return null;
                });

        // Wait for the asynchronous operation to complete
        translationFutureCustom.join();

        translator.close();
        customTranslator.close();
    }
}