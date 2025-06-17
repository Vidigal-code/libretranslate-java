package com.java.vidigal.code.test.request;

import com.java.vidigal.code.builder.TranslationRequestBuilder;
import com.java.vidigal.code.request.TranslationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link TranslationRequestBuilder} class, verifying its functionality in
 * constructing {@link TranslationRequest} objects for the LibreTranslate translation API. Tests cover
 * building requests with single or multiple text segments, setting target and source languages,
 * and validating inputs to ensure text segments and target language are provided correctly.
 */
class TranslationRequestBuilderTest {

    /**
     * Instance of {@link TranslationRequestBuilder} under test, initialized before each test.
     */
    private TranslationRequestBuilder builder;

    /**
     * Sets up the test environment before each test. Initializes a new
     * {@link TranslationRequestBuilder} instance for testing.
     */
    @BeforeEach
    void setUp() {
        builder = new TranslationRequestBuilder();
    }

    /**
     * Tests building a {@link TranslationRequest} with a single text segment and a target language.
     * Verifies that the resulting request contains the expected text, target language, and no source
     * language.
     */
    @Test
    void shouldBuildRequestWithSingleTextAndTargetLang() {
        TranslationRequest request = builder
                .addText("Hello")
                .setTargetLang("es")
                .build();

        assertEquals(1, request.getTextSegments().size(), "Request should contain one text segment");
        assertEquals("Hello", request.getTextSegments().get(0), "Text segment should be 'Hello'");
        assertEquals("es", request.getTargetLang(), "Target language should be 'es'");
        assertNull(request.getSourceLang(), "Source language should be null");
    }

    /**
     * Tests building a {@link TranslationRequest} with multiple text segments, a target language,
     * and a source language. Verifies that the resulting request contains the expected texts,
     * target language, and source language.
     */
    @Test
    void shouldBuildRequestWithMultipleTextsAndSourceLang() {

        TranslationRequest request = builder
                .addText("Hello")
                .addText("World")
                .setTargetLang("fr")
                .setSourceLang("en")
                .build();

        assertEquals(2, request.getTextSegments().size(), "Request should contain two text segments");
        assertEquals("Hello", request.getTextSegments().get(0), "First text segment should be 'Hello'");
        assertEquals("World", request.getTextSegments().get(1), "Second text segment should be 'World'");
        assertEquals("fr", request.getTargetLang(), "Target language should be 'fr'");
        assertEquals("en", request.getSourceLang(), "Source language should be 'en'");
    }

    /**
     * Tests that building a {@link TranslationRequest} without any text segments throws an
     * {@link IllegalStateException}. Verifies that the exception message indicates the requirement
     * for non-empty text.
     */
    @Test
    void shouldThrowExceptionWhenTextIsEmpty() {
        builder.setTargetLang("fr");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                builder::build,
                "Expected IllegalStateException for empty text segments"
        );
        assertEquals("Text cannot be empty", exception.getMessage(),
                "Exception message should indicate empty text");
    }

    /**
     * Tests that building a {@link TranslationRequest} with a null target language throws an
     * {@link IllegalStateException}. Verifies that the exception message indicates the requirement
     * for a non-null target language.
     */
    @Test
    void shouldThrowExceptionWhenTargetLangIsNull() {
        builder.addText("Test");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                builder::build,
                "Expected IllegalStateException for null target language"
        );
        assertEquals("Target language cannot be null or empty", exception.getMessage(),
                "Exception message should indicate null or empty target language");
    }

    /**
     * Tests that building a {@link TranslationRequest} with a blank target language throws an
     * {@link IllegalStateException}. Verifies that the exception message indicates the requirement
     * for a non-empty target language.
     */
    @Test
    void shouldThrowExceptionWhenTargetLangIsBlank() {
        builder.addText("Test").setTargetLang(" ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                builder::build,
                "Expected IllegalStateException for blank target language"
        );
        assertEquals("Target language cannot be null or empty", exception.getMessage(),
                "Exception message should indicate null or empty target language");
    }
}