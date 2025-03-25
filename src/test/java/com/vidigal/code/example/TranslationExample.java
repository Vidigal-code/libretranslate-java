package com.vidigal.code.example;

import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.service.TranslatorService;
import com.vidigal.code.libretranslate.service.Translators;
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

    public static void main(String[] args) throws Exception {

        /**
         * Example 1: Basic Usage of TranslatorService
         */

        // Create a TranslatorService instance with the API URL and API Key
        TranslatorService translator = Translators.create(API, KEY);

        LibreTranslateConfig config = LibreTranslateConfig.builder().apiUrl(API).apiKey(KEY).build();

        try (LibreTranslateClient client = new LibreTranslateClient(config)) {
            String translated = client.translate("Hello", "en", "es");
            System.out.println(translated); // Hola
        }
    }
}