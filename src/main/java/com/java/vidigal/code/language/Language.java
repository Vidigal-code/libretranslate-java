package com.java.vidigal.code.language;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of languages supported by LibreTranslate, with ISO language codes.
 */
public enum Language {
    ENGLISH("en"),
    ALBANESE("sq"),
    ARABIC("ar"),
    AZERBAIJANI("az"),
    RUSSIAN("ru"),
    CATALAN("ca"),
    CHINESE("zh"),
    CZECH("cs"),
    DANISH("da"),
    DUTCH("nl"),
    ESPERANTO("eo"),
    FINNISH("fi"),
    FRENCH("fr"),
    GERMAN("de"),
    GREEK("el"),
    HEBREW("he"),
    HINDI("hi"),
    HUNGARIAN("hu"),
    INDONESIAN("id"),
    IRISH("ga"),
    ITALIAN("it"),
    JAPANESE("ja"),
    KOREAN("ko"),
    PERSIAN("fa"),
    POLISH("pl"),
    PORTUGUESE("pt"),
    SLOVAK("sk"),
    SPANISH("es"),
    SWEDISH("sv"),
    TURKISH("tr"),
    UKRAINIAN("uk"),
    AUTO("auto");

    private static final Map<String, Language> CODE_TO_LANGUAGE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(lang -> lang.getCode().toLowerCase(), Function.identity()));
    private final String code;

    Language(String code) {
        this.code = code;
    }

    /**
     * Finds a Language by its code, case-insensitively.
     *
     * @param code The language code (e.g., "EN", "fr").
     * @return An Optional containing the Language, or empty if not found.
     */
    public static java.util.Optional<Language> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(CODE_TO_LANGUAGE_MAP.get(code.toLowerCase()));
    }

    /**
     * Returns the ISO language code.
     *
     * @return The language code string.
     */
    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return this.code;
    }
}