package com.java.vidigal.code.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a translation request to the LibreTranslate API.
 */
public class TranslationRequest {

    @JsonProperty("text")
    private final List<String> text;

    @JsonProperty("target_lang")
    private final String targetLang;

    @JsonProperty("source_lang")
    private final String sourceLang;

    public TranslationRequest(List<String> text, String targetLang, String sourceLang) {
        this.text = text;
        this.targetLang = targetLang;
        this.sourceLang = sourceLang;
    }

    /**
     * Gets the text segments to translate.
     *
     * @return The text segments.
     */
    public List<String> getTextSegments() {
        return text;
    }

    /**
     * Gets the target language code.
     *
     * @return The target language code.
     */
    public String getTargetLang() {
        return targetLang;
    }

    /**
     * Gets the source language code.
     *
     * @return The source language code, or null if not specified.
     */
    public String getSourceLang() {
        return sourceLang;
    }
}
