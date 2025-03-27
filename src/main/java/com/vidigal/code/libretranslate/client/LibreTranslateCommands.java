package com.vidigal.code.libretranslate.client;


import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.language.Language;
import com.vidigal.code.libretranslate.service.TranslatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles command processing for translations with multiple parsing and execution modes.
 * <p>
 * This class provides flexible translation command processing with support for:
 * - Different operation modes (synchronous, asynchronous)
 * - Multiple language configurations
 * - Logging and error handling
 */
public class LibreTranslateCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibreTranslateCommands.class);
    private static final String DEFAULT_SOURCE_LANGUAGE = Language.AUTO.getCode();
    private static final String ERROR_MESSAGE_INVALID_COMMAND = "Invalid command format";

    private final TranslatorService translatorService;

    /**
     * Constructs a LibreTranslateCommands with a specified translator service.
     *
     * @param translatorService The translation service to use for processing commands
     */
    public LibreTranslateCommands(TranslatorService translatorService) {
        this.translatorService = translatorService;
    }

    /**
     * Processes a list of translation commands with optional logging.
     *
     * @param commands List of translation commands to process
     * @param log      Whether to log the results of each command
     * @return A list of processed command results
     */
    public List<String> processCommands(List<String> commands, boolean log) {
        return Collections.singletonList(String.join("\n", commands.parallelStream()
                .map(command -> {
                    try {
                        return processSingleCommand(command, log);
                    } catch (Exception e) {
                        LOGGER.error("Command processing failed for command: " + command, e);
                        return "Error: " + e.getMessage();
                    }
                })
                .collect(Collectors.toList())));
    }

    /**
     * Processes a single translation command.
     *
     * @param command The command to process
     * @param log     Whether to log the result
     * @return The result of processing the command
     */
    private String processSingleCommand(String command, boolean log) {
        try {
            String[] parts = command.split(";");
            if (parts.length < 2) {
                throw new TranslationException("Invalid command format: " + command);
            }

            String mode = parts[0];
            if (mode.startsWith("m:")) {
                return handleModeSpecificCommand(parts, log);
            } else if (mode.startsWith("t:")) {
                return handleNormalCommand(parts, log);
            } else {
                throw new TranslationException(ERROR_MESSAGE_INVALID_COMMAND + ": " + command);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process command: {}", command, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Extracts translation parameters from command parts.
     *
     * @param parts The command parts
     * @return A map containing the extracted parameters (text, sourceLang, targetLang)
     */
    private Map<String, String> extractTranslationParams(String[] parts) {
        Map<String, String> params = new HashMap<>();
        params.put("text", extractText(parts[0]));
        params.put("sourceLang", extractSourceLanguage(parts));
        params.put("targetLang", extractTargetLanguage(parts));
        return params;
    }

    /**
     * Handles mode-specific translation commands (e.g., synchronous or asynchronous).
     *
     * @param parts The command parts
     * @param log   Whether to log the result
     * @return The result of the translation
     * @throws Exception If translation fails
     */
    private String handleModeSpecificCommand(String[] parts, boolean log) throws Exception {
        String operationMode = extractOperationMode(parts[0]);
        Map<String, String> params = extractTranslationParams(Arrays.copyOfRange(parts, 1, parts.length));
        String text = params.get("text");
        String sourceLang = params.get("sourceLang");
        String targetLang = params.get("targetLang");

        switch (operationMode) {
            case "s":
                String result = translatorService.translate(text, sourceLang, targetLang);
                return log ? "Synchronous Translation: " + result : result;

            case "as":
                CompletableFuture<String> future = translatorService.translateAsync(text, sourceLang, targetLang);
                return log ? "Asynchronous Translation: " + future.get() : future.get();

            default:
                throw new TranslationException("Invalid operation mode: " + operationMode);
        }
    }

    /**
     * Handles normal translation commands without specific modes.
     *
     * @param parts The command parts
     * @param log   Whether to log the result
     * @return The result of the translation
     */
    private String handleNormalCommand(String[] parts, boolean log) {
        Map<String, String> params = extractTranslationParams(parts);
        String text = params.get("text");
        String sourceLang = params.get("sourceLang");
        String targetLang = params.get("targetLang");

        String result = translatorService.translate(text, sourceLang, targetLang);
        return log ? "Default Translation: " + result : result;
    }

    /**
     * Extracts the operation mode from a command part.
     *
     * @param part The command part
     * @return The operation mode
     */
    private String extractOperationMode(String part) {
        return part.substring(2);
    }

    /**
     * Extracts the text from a command part.
     *
     * @param part The command part.
     * @return The extracted text.
     */
    private String extractText(String part) {
        return part.substring(2);
    }

    /**
     * Extracts the source language from the command parts.
     *
     * @param parts The command parts.
     * @return The source language code.
     */
    private String extractSourceLanguage(String[] parts) {
        return parts.length > 3 ? parts[2] : DEFAULT_SOURCE_LANGUAGE;
    }

    /**
     * Extracts the target language from the command parts.
     *
     * @param parts The command parts.
     * @return The target language code.
     */
    private String extractTargetLanguage(String[] parts) {
        return parts[parts.length - 1];
    }
}