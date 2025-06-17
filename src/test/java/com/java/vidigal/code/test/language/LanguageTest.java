package com.java.vidigal.code.test.language;

import com.java.vidigal.code.language.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Language} enum, verifying the correctness of predefined language constants
 * and their associated methods.
 */
class LanguageTest {

    @ParameterizedTest
    @CsvSource({
            "en, ENGLISH",
            "EN, ENGLISH",
            "fr, FRENCH",
            "FR, FRENCH",
            "de, GERMAN",
            "DE, GERMAN",
            "auto, AUTO",
            "AUTO, AUTO",
            "sq, ALBANESE",
            "SQ, ALBANESE"
    })
    @DisplayName("fromCode should find correct enum constant for valid codes (case-insensitive)")
    void fromCode_shouldFindCorrectEnum_forValidCodes(String code, Language expectedLanguage) {
        Optional<Language> result = Language.fromCode(code);
        assertTrue(result.isPresent(), "Expected language to be found for code: " + code);
        assertEquals(expectedLanguage, result.get(), "Expected language to match for code: " + code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xx", "INVALID", "123", "zz"})
    @DisplayName("fromCode should return empty Optional for invalid codes")
    void fromCode_shouldReturnEmpty_forInvalidCodes(String invalidCode) {
        Optional<Language> result = Language.fromCode(invalidCode);
        assertTrue(result.isEmpty(), "Expected Optional to be empty for invalid code: " + invalidCode);
    }

    @Test
    @DisplayName("fromCode should return empty Optional for null or blank input")
    void fromCode_shouldReturnEmpty_forNullOrBlankInput() {
        assertTrue(Language.fromCode(null).isEmpty(), "Expected Optional to be empty for null input");
        assertTrue(Language.fromCode("").isEmpty(), "Expected Optional to be empty for empty string");
        assertTrue(Language.fromCode("  ").isEmpty(), "Expected Optional to be empty for blank string");
    }

    @Test
    @DisplayName("getCode should return the correct string code")
    void getCode_shouldReturnCorrectCode() {
        assertEquals("en", Language.ENGLISH.getCode(), "Expected code 'en' for ENGLISH");
        assertEquals("fr", Language.FRENCH.getCode(), "Expected code 'fr' for FRENCH");
        assertEquals("auto", Language.AUTO.getCode(), "Expected code 'auto' for AUTO");
        assertEquals("sq", Language.ALBANESE.getCode(), "Expected code 'sq' for ALBANESE");
    }

    @Test
    @DisplayName("toString should return the language code")
    void toString_shouldReturnLanguageCode() {
        assertEquals("en", Language.ENGLISH.toString(), "toString should return the language code");
        assertEquals("fr", Language.FRENCH.toString(), "toString should return the language code");
        assertEquals("auto", Language.AUTO.toString(), "toString should return the language code");
    }

    @Test
    @DisplayName("should have all expected languages defined")
    void shouldHaveAllExpectedLanguages() {
        // Test that all expected languages are present
        assertNotNull(Language.ENGLISH);
        assertNotNull(Language.FRENCH);
        assertNotNull(Language.GERMAN);
        assertNotNull(Language.SPANISH);
        assertNotNull(Language.PORTUGUESE);
        assertNotNull(Language.ITALIAN);
        assertNotNull(Language.CHINESE);
        assertNotNull(Language.JAPANESE);
        assertNotNull(Language.KOREAN);
        assertNotNull(Language.RUSSIAN);
        assertNotNull(Language.AUTO);

        // Verify we have a reasonable number of languages
        assertTrue(Language.values().length >= 30, "Should have at least 30 languages defined");
    }

    @Test
    @DisplayName("should have unique language codes")
    void shouldHaveUniqueLanguageCodes() {
        Language[] languages = Language.values();
        java.util.Set<String> codes = new java.util.HashSet<>();

        for (Language language : languages) {
            String code = language.getCode();
            assertFalse(codes.contains(code), "Duplicate language code found: " + code);
            codes.add(code);
        }

        assertEquals(languages.length, codes.size(), "All language codes should be unique");
    }

    @Test
    @DisplayName("should have case-insensitive lookup working for all languages")
    void shouldHaveCaseInsensitiveLookupForAllLanguages() {
        for (Language language : Language.values()) {
            String code = language.getCode();

            // Test lowercase
            Optional<Language> lowerResult = Language.fromCode(code.toLowerCase());
            assertTrue(lowerResult.isPresent(), "Should find language for lowercase code: " + code.toLowerCase());
            assertEquals(language, lowerResult.get(), "Should return correct language for lowercase code");

            // Test uppercase
            Optional<Language> upperResult = Language.fromCode(code.toUpperCase());
            assertTrue(upperResult.isPresent(), "Should find language for uppercase code: " + code.toUpperCase());
            assertEquals(language, upperResult.get(), "Should return correct language for uppercase code");
        }
    }
}