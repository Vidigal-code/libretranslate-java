package com.vidigal.code.example;

import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.service.Translators;

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

    public static void main(String[] args) throws Exception {
        // Create a TranslatorService instance with the API URL and API Key
        TranslatorService translator = Translators.create(API, KEY);

        // Define a list of commands for batch processing
        List<String> commands = Arrays.asList(
                "m:s;t:Hello;en;pt", // Synchronous translation of "Hello" from English (en) to Portuguese (pt)
                "t:World;pt",        // Translate "World" to Portuguese (pt)
                "t:Hello;en;pt",     // Translate "Hello" en (English) to Portuguese (pt)
                "m:as;t:Goodbye;en;es" // Asynchronous translation of "Goodbye" from English (en) to Spanish (es)
        );

        // Process the commands and print the results
        System.out.println(translator.processCommands(commands, false));

        // Perform a simple synchronous translation
        String result = translator.translate("Hello world", "pt");
        System.out.println("Translated text: " + result);

        // Perform a synchronous translation with a specified source language
        String resultWithSource = translator.translate("Hello world", "en", "es");
        System.out.println("Translated to Spanish: " + resultWithSource);

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

        translator.close();
    }
}