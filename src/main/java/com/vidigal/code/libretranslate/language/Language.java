package com.vidigal.code.libretranslate.language;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enumeration representing the supported languages for translation.
 * This enum provides methods to validate and manage language codes.
 */
public enum Language {

    ENGLISH("en"),
    ALBANIAN("sq"),
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

    private static final Map<String, Language> CODE_TO_LANGUAGE_MAP = new ConcurrentHashMap<>();

    static {
        for (Language language : values()) {
            CODE_TO_LANGUAGE_MAP.put(language.getCode().toLowerCase(), language);
        }
    }

    private final String code;

    /**
     * Constructor for Language enum.
     *
     * @param code The ISO language code
     */
    Language(String code) {
        this.code = code;
    }

    /**
     * Returns the corresponding Language enum for a given code.
     *
     * @param code The language code to look up
     * @return The corresponding Language enum, or AUTO if no match is found
     */
    public static Language fromCode(String code) {
        if (isEmpty(code)) {
            return AUTO;
        }
        return CODE_TO_LANGUAGE_MAP.getOrDefault(code.toLowerCase(), AUTO);
    }

    /**
     * Checks if the provided language code is valid.
     *
     * @param code The language code to validate
     * @return True if the code is valid, false otherwise
     */
    public static boolean isValidLanguageCode(String code) {
        if (isEmpty(code)) {
            return false;
        }
        return CODE_TO_LANGUAGE_MAP.containsKey(code.toLowerCase());
    }

    /**
     * Returns a list of all supported language codes.
     *
     * @return List of language codes
     */
    public static List<String> getAllLanguageCodes() {
        return Collections.unmodifiableList(new ArrayList<>(CODE_TO_LANGUAGE_MAP.keySet()));
    }

    /**
     * Checks if the provided language code is supported.
     *
     * @param code The language code to check
     * @return True if the language is supported, false otherwise
     */
    public static boolean isSupportedLanguage(String code) {
        return isValidLanguageCode(code);
    }

    /**
     * Checks if the provided language code is supported.
     *
     * @param languages A comma-separated string of language codes to check
     * @return True if any of the language codes are supported, false otherwise
     */
    public static boolean isSupportedLanguages(String languages) {
        // Example of supported language codes as a comma-separated string
        String supportedLanguages = languages;

        // Splits the string into an array of language codes
        String[] supportedLanguagesArray = supportedLanguages.split(",");

        // Loop through each language code and check if it's supported
        for (String language : supportedLanguagesArray) {
            // Check if the language exists in the CODE_TO_LANGUAGE_MAP (assuming it's a map of supported codes)
            if (CODE_TO_LANGUAGE_MAP.containsKey(language.trim())) {
                return true;  // Language found, return true
            }
        }

        // If none of the languages are supported, return false
        return false;
    }

    /**
     * Validates that a language code is not null or empty.
     *
     * @param code The language code to validate
     * @throws IllegalArgumentException If the code is null or empty
     */
    private static void validateCode(String code) {
        if (isEmpty(code)) {
            throw new IllegalArgumentException("Language code cannot be null or empty");
        }
    }

    /**
     * Checks if a string is null or empty.
     *
     * @param str The string to check
     * @return True if the string is null or empty, false otherwise
     */
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Adds multiple languages to the supported languages.
     *
     * @param codes Comma-separated list of language codes to add (e.g., "en,pt,es")
     * @return True if all languages were added successfully, false if any were not valid or already exist
     */
    public static void addLanguages(String codes) {
        if (isEmpty(codes)) {
            throw new IllegalArgumentException("Language codes cannot be null or empty");
        }

        String[] languageCodes = codes.split(",");

        // Add valid languages that don't already exist
        for (String code : languageCodes) {
            code = code.trim().toLowerCase();
            if (!CODE_TO_LANGUAGE_MAP.containsKey(code)) {
                // You can add custom logic for adding a new language if necessary
                CODE_TO_LANGUAGE_MAP.putIfAbsent(code, AUTO); // Example, add the new language (can be changed to something else)
            }
        }
    }

    /**
     * Removes multiple languages from the supported languages.
     *
     * @param codes Comma-separated list of language codes to remove (e.g., "en,pt,es")
     * @return True if all languages were removed successfully, false if any were not found or invalid
     */
    public static boolean removeLanguages(String codes) {
        if (isEmpty(codes)) {
            throw new IllegalArgumentException("Language codes cannot be null or empty");
        }

        String[] languageCodes = codes.split(",");
        boolean allRemoved = true;

        for (String code : languageCodes) {
            code = code.trim().toLowerCase();
            if (!isValidLanguageCode(code) || !CODE_TO_LANGUAGE_MAP.containsKey(code)) {
                allRemoved = false; // If any language code is invalid or doesn't exist, return false
            }
        }

        if (allRemoved) {
            // Remove languages that exist
            for (String code : languageCodes) {
                code = code.trim().toLowerCase();
                CODE_TO_LANGUAGE_MAP.remove(code);
            }
        }

        return allRemoved;
    }

    /**
     * Clears all custom languages.
     * For this example, we are not managing custom languages, so this function will reset the standard ones.
     */
    public static void clearAllLanguages() {
        // Resetting logic can be customized depending on how custom languages are stored
        // In this example, we're just clearing the map of supported languages
        CODE_TO_LANGUAGE_MAP.clear();

    }

    /**
     * Clears all custom languages, Re-add the default languages.
     * For this example, we are not managing custom languages, so this function will reset the standard ones.
     */
    public static void LanguagesDefault() {
        // Resetting logic can be customized depending on how custom languages are stored
        // In this example, we're just clearing the map of supported languages
        CODE_TO_LANGUAGE_MAP.clear();

        // Re-add the default languages
        for (Language language : values()) {
            CODE_TO_LANGUAGE_MAP.put(language.getCode().toLowerCase(), language);
        }
    }

    /**
     * Checks if all provided languages are allowed based on the permitted languages.
     *
     * @param permittedLanguages Comma-separated list of permitted language codes (e.g., "en,pt,es")
     * @param languagesToCheck   Comma-separated list of language codes to check (e.g., "en,es")
     * @return True if all languages in the second parameter are in the first, false otherwise
     */
    public static boolean areLanguagesAllowed(String permittedLanguages, String languagesToCheck) {
        if (isEmpty(permittedLanguages) || isEmpty(languagesToCheck)) {
            throw new IllegalArgumentException("Permitted languages and languages to check cannot be null or empty");
        }

        // Convert both inputs to sets of lowercase codes
        Set<String> permittedSet = new HashSet<>(Arrays.asList(permittedLanguages.toLowerCase().split(",")));
        Set<String> toCheckSet = new HashSet<>(Arrays.asList(languagesToCheck.toLowerCase().split(",")));

        // Check if all languages to check are within the permitted set
        return permittedSet.containsAll(toCheckSet);
    }

    /**
     * Returns the language code.
     *
     * @return The ISO language code
     */
    public String getCode() {
        return code;
    }
}
