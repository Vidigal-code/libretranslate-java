package com.java.vidigal.code.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a response from the LibreTranslate API containing translations.
 */
public class TranslationResponse {

    @JsonProperty("translations")
    private final List<Translation> translations;

    public TranslationResponse() {
        this.translations = null;
    }

    @JsonCreator
    public TranslationResponse(
            @JsonProperty("translations") List<Translation> translations
    ) {
        this.translations = translations;
    }

    /**
     * Gets the list of translations.
     *
     * @return The translations.
     */
    public List<Translation> getTranslations() {
        return translations;
    }
}
