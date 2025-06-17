package com.java.vidigal.code.builder;

import com.java.vidigal.code.request.TranslationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder for creating {@link TranslationRequest} objects using a fluent API.
 * <p>
 * This class provides a step-by-step approach to configure translation requests with text segments,
 * target language, and optional source language. It includes validation to ensure inputs meet
 * LibreTranslate API constraints, such as maximum text length.
 * </p>
 *
 * @author Vidigal
 */
public class TranslationRequestBuilder {

    private static final Logger logger = LoggerFactory.getLogger(TranslationRequestBuilder.class);
    private static final int MAX_TEXT_LENGTH = 10_000; // LibreTranslate API text length limit

    private final List<String> text = new ArrayList<>();
    private String targetLang;
    private String sourceLang;

    /**
     * Adds a text segment to the translation request.
     * <p>
     * Validates that the text is neither null, blank, nor exceeds the maximum allowed length
     * ({@value #MAX_TEXT_LENGTH} characters).
     * </p>
     *
     * @param text the text segment to translate
     * @return this builder for method chaining
     * @throws IllegalArgumentException if text is null, blank, or too long
     */
    public TranslationRequestBuilder addText(String text) {
        if (text == null || text.isBlank()) {
            logger.error("Text cannot be null or empty");
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.error("Text length {} exceeds maximum allowed: {}", text.length(), MAX_TEXT_LENGTH);
            throw new IllegalArgumentException("Text length exceeds maximum of " + MAX_TEXT_LENGTH + " characters");
        }
        this.text.add(text);
        return this;
    }

    /**
     * Sets the target language for the translation.
     *
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @return this builder for method chaining
     */
    public TranslationRequestBuilder setTargetLang(String targetLang) {
        this.targetLang = targetLang;
        return this;
    }

    /**
     * Sets the source language for the translation (optional).
     *
     * @param sourceLang the source language code (e.g., "EN", "FR"), or null for auto-detection
     * @return this builder for method chaining
     */
    public TranslationRequestBuilder setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
        return this;
    }

    /**
     * Constructs a {@link TranslationRequest} with the configured parameters.
     * <p>
     * Validates that at least one text segment has been added and the target language is specified.
     * </p>
     *
     * @return a new {@link TranslationRequest} instance
     * @throws IllegalStateException if no text segments are provided or target language is null/blank
     */
    public TranslationRequest build() {
        if (text.isEmpty()) {
            logger.error("No text provided for translation request");
            throw new IllegalStateException("Text cannot be empty");
        }
        if (targetLang == null || targetLang.isBlank()) {
            logger.error("Target language is null or empty");
            throw new IllegalStateException("Target language cannot be null or empty");
        }
        return new TranslationRequest(text, targetLang, sourceLang);
    }
}

