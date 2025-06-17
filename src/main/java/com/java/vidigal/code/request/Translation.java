package com.java.vidigal.code.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a translation result from the LibreTranslate API.
 */
public class Translation {

    @JsonProperty("text")
    private final String text;

    @JsonProperty("detected_source_language")
    private final String detectedSourceLanguage;

    public Translation() {
        this.text = null;
        this.detectedSourceLanguage = null;
    }

    @JsonCreator
    public Translation(
            @JsonProperty("text") String text,
            @JsonProperty("detected_source_language") String detectedSourceLanguage
    ) {
        this.text = text;
        this.detectedSourceLanguage = detectedSourceLanguage;
    }

    /**
     * Gets the translated text.
     *
     * @return The translated text.
     */
    public String getText() {
        return text;
    }

    /**
     * Gets the detected source language.
     *
     * @return The detected source language, or null if not provided.
     */
    public String getDetectedSourceLanguage() {
        return detectedSourceLanguage;
    }
}
