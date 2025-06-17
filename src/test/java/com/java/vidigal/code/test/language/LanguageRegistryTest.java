package com.java.vidigal.code.test.language;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.vidigal.code.language.LanguageRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link LanguageRegistry} class, verifying its functionality for managing
 * supported language codes used in the LibreTranslate translation API. Tests cover default language support,
 * language addition, and API fetching logic for updating supported languages. Uses Mockito to mock
 * {@link HttpClient} and {@link HttpResponse} dependencies, and organizes tests into nested classes
 * for clarity.
 */
@ExtendWith(MockitoExtension.class)
class LanguageRegistryTest {

    /**
     * Instance of {@link LanguageRegistry} under test, configured with mocked dependencies.
     */
    private LanguageRegistry registry;

    /**
     * Mocked {@link HttpClient} used to simulate HTTP requests to the LibreTranslate API for fetching languages.
     */
    @Mock
    private HttpClient mockHttpClient;

    /**
     * Mocked {@link ObjectMapper} used to simulate JSON processing.
     */
    @Mock
    private ObjectMapper mockObjectMapper;

    /**
     * Mocked {@link HttpResponse} used to simulate HTTP responses from the LibreTranslate API.
     */
    @Mock
    private HttpResponse<String> mockHttpResponse;

    /**
     * Sets up the test environment before each test. Initializes the {@link LanguageRegistry} instance
     * with mocked dependencies using constructor injection, eliminating the need for reflection.
     */
    @BeforeEach
    void setUp() {
        registry = new LanguageRegistry(mockHttpClient, mockObjectMapper);
        registry.resetToDefaults();
    }

    /**
     * Nested test class containing tests for the default state and basic validation of the
     * {@link LanguageRegistry}.
     */
    @Nested
    @DisplayName("Default State and Basic Validation")
    class DefaultStateTests {

        /**
         * Tests that the {@link LanguageRegistry} supports default languages upon initialization.
         * Verifies that known language codes (e.g., "en", "fr") are supported and an unknown code
         * (e.g., "xx") is not.
         */
        @Test
        @DisplayName("should support default languages upon initialization")
        void shouldSupportDefaultLanguages() {
            assertTrue(registry.isSupported("en"), "Default language 'en' should be supported");
            assertTrue(registry.isSupported("fr"), "Default language 'fr' should be supported");
            assertFalse(registry.isSupported("xx"), "Unknown language 'xx' should not be supported");
        }

        /**
         * Tests that the registry correctly handles null and blank language codes.
         */
        @Test
        @DisplayName("should handle null and blank language codes")
        void shouldHandleNullAndBlankCodes() {
            assertFalse(registry.isSupported(null), "Null language code should not be supported");
            assertFalse(registry.isSupported(""), "Empty language code should not be supported");
            assertFalse(registry.isSupported("   "), "Blank language code should not be supported");
        }

        /**
         * Tests that the registry returns the correct count of supported languages.
         */
        @Test
        @DisplayName("should return correct count of supported languages")
        void shouldReturnCorrectLanguageCount() {
            int initialCount = registry.getSupportedLanguageCount();
            assertTrue(initialCount > 0, "Should have default languages loaded");

            registry.addLanguage("TEST");
            assertEquals(initialCount + 1, registry.getSupportedLanguageCount(),
                    "Count should increase after adding language");
        }
    }

    /**
     * Nested test class containing tests for language management operations in the
     * {@link LanguageRegistry}.
     */
    @Nested
    @DisplayName("Language Management")
    class ManagementTests {

        /**
         * Tests the addition of a new language code to the {@link LanguageRegistry}. Verifies that
         * an unsupported language code becomes supported after being added.
         */
        @Test
        @DisplayName("should add a new language code")
        void shouldAddLanguage() {
            assertFalse(registry.isSupported("zz"), "Language 'zz' should not be supported initially");
            registry.addLanguage("ZZ");
            assertTrue(registry.isSupported("zz"), "Language 'zz' should be supported after addition");
        }

        /**
         * Tests the removal of a language code from the {@link LanguageRegistry}.
         */
        @Test
        @DisplayName("should remove a language code")
        void shouldRemoveLanguage() {
            registry.addLanguage("TEST");
            assertTrue(registry.isSupported("test"), "Language 'test' should be supported after addition");

            registry.removeLanguage("TEST");
            assertFalse(registry.isSupported("test"), "Language 'test' should not be supported after removal");
        }

        /**
         * Tests that adding null or blank language codes has no effect.
         */
        @Test
        @DisplayName("should ignore null and blank language codes when adding")
        void shouldIgnoreNullAndBlankWhenAdding() {
            int initialCount = registry.getSupportedLanguageCount();

            registry.addLanguage(null);
            registry.addLanguage("");
            registry.addLanguage("   ");

            assertEquals(initialCount, registry.getSupportedLanguageCount(),
                    "Count should remain unchanged after adding invalid codes");
        }

        /**
         * Tests the reset functionality of the {@link LanguageRegistry}.
         */
        @Test
        @DisplayName("should reset to default languages")
        void shouldResetToDefaults() {
            registry.addLanguage("CUSTOM");
            assertTrue(registry.isSupported("custom"), "Custom language should be supported");

            registry.resetToDefaults();
            assertFalse(registry.isSupported("custom"), "Custom language should be removed after reset");
            assertFalse(registry.hasFetchedFromApi(), "API fetch flag should be reset");
        }
    }

    /**
     * Nested test class containing tests for the API fetching logic of the {@link LanguageRegistry}.
     */
    @Nested
    @DisplayName("API Fetching Logic")
    class ApiFetchingTests {

        /**
         * Tests successful fetching and addition of new languages from the LibreTranslate API.
         */
        @Test
        @DisplayName("should fetch and add new languages from API on success")
        void shouldFetchAndAddNewLanguages() throws Exception {
            // Arrange
            String apiResponse = "[{\"code\":\"et\",\"name\":\"Estonian\"}, {\"code\":\"ga\",\"name\":\"IRISH\"}]";
            doReturn(200).when(mockHttpResponse).statusCode();
            doReturn(apiResponse).when(mockHttpResponse).body();
            doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

            // Use a type reference to parse the API response (simulate the mapping)
            List<Map<String, String>> parsedLanguages = List.of(
                    Map.of("code", "et", "name", "Estonian"),
                    Map.of("code", "ga", "name", "IRISH")
            );
            when(mockObjectMapper.readValue(eq(apiResponse), any(TypeReference.class)))
                    .thenReturn(parsedLanguages);

            // Remove "et" and "ga" before testing, since they may be in the default Language enum
            registry.removeLanguage("et");
            registry.removeLanguage("ga");

            assertFalse(registry.isSupported("ga"), "Language 'ga' should not be supported initially");
            assertFalse(registry.isSupported("et"), "Language 'et' should not be supported initially");

            // Act
            boolean result = registry.fetchFromApi("http://mock.api", "mock-key").join();

            // Assert
            assertTrue(result, "API fetch should return success");
            assertTrue(registry.isSupported("ga"), "Language 'ga' should be supported after API fetch");
            assertTrue(registry.isSupported("et"), "Language 'et' should be supported after API fetch");
            assertTrue(registry.hasFetchedFromApi(), "API fetch flag should be set");
        }

        /**
         * Tests that the {@link LanguageRegistry} does not attempt to fetch languages from the API
         * again if it has already successfully fetched them.
         */
        @Test
        @DisplayName("should not fetch from API if already fetched")
        void shouldNotFetchIfAlreadyFetched() throws Exception {
            // Arrange
            String apiResponse = "[]";
            doReturn(200).when(mockHttpResponse).statusCode();
            doReturn(apiResponse).when(mockHttpResponse).body();
            doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

            when(mockObjectMapper.readValue(eq(apiResponse), any(TypeReference.class)))
                    .thenReturn(List.of());

            // Act
            registry.fetchFromApi("http://mock.api", "mock-key").join();
            registry.fetchFromApi("http://mock.api", "mock-key").join(); // Second call

            // Assert
            verify(mockHttpClient, times(1)).send(any(), any());
        }

        /**
         * Tests the ability to retry fetching languages from the API after an initial failure.
         */
        @Test
        @DisplayName("should allow retry fetching on API failure")
        void shouldAllowRetryOnApiFailure() throws Exception {
            // Arrange
            String errorApiResponse = "Error";
            String successApiResponse = "[{\"code\":\"ga\",\"name\":\"IRISH\"}]";

            HttpResponse<String> errorResponse = mock(HttpResponse.class);
            doReturn(500).when(errorResponse).statusCode();
            doReturn(errorApiResponse).when(errorResponse).body();

            HttpResponse<String> successResponse = mock(HttpResponse.class);
            doReturn(200).when(successResponse).statusCode();
            doReturn(successApiResponse).when(successResponse).body();

            // Simulate error on first call, success on second
            doReturn(errorResponse)
                    .doReturn(successResponse)
                    .when(mockHttpClient).send(any(), any());


            List<Map<String, String>> parsedLanguages = List.of(
                    Map.of("code", "ga", "name", "IRISH")
            );

            when(mockObjectMapper.readValue(eq(successApiResponse), any(TypeReference.class)))
                    .thenReturn(parsedLanguages);

            registry.removeLanguage("ga");

            // Act & Assert
            boolean firstResult = registry.fetchFromApi("http://mock.api", "mock-key").join();

            assertFalse(firstResult, "First fetch should fail");
            assertFalse(registry.isSupported("ga"), "Language 'ga' should not be supported after failed fetch");
            assertFalse(registry.hasFetchedFromApi(), "API fetch flag should be false after failure");

            boolean secondResult = registry.fetchFromApi("http://mock.api", "mock-key").join();
            assertTrue(secondResult, "Second fetch should succeed");
            assertTrue(registry.isSupported("ga"), "Language 'ga' should be supported after successful retry");
            assertTrue(registry.hasFetchedFromApi(), "API fetch flag should be true after success");

            verify(mockHttpClient, times(2)).send(any(), any());
        }

        /**
         * Tests manual reset of the API fetch flag.
         */
        @Test
        @DisplayName("should allow manual reset of API fetch flag")
        void shouldAllowManualResetOfApiFetchFlag() throws Exception {
            // Arrange
            String apiResponse = "[]";
            doReturn(200).when(mockHttpResponse).statusCode();
            doReturn(apiResponse).when(mockHttpResponse).body();
            doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

            when(mockObjectMapper.readValue(eq(apiResponse), any(TypeReference.class)))
                    .thenReturn(List.of());

            // Act
            registry.fetchFromApi("http://mock.api", "mock-key").join();
            assertTrue(registry.hasFetchedFromApi(), "API fetch flag should be set");

            registry.resetApiFetchFlag();
            assertFalse(registry.hasFetchedFromApi(), "API fetch flag should be reset");

            // Should allow fetching again
            registry.fetchFromApi("http://mock.api", "mock-key").join();

            // Assert
            verify(mockHttpClient, times(2)).send(any(), any());
        }
    }

    /**
     * Nested test class for testing factory methods and alternative constructors.
     */
    @Nested
    @DisplayName("Factory Methods and Construction")
    class FactoryMethodTests {

        /**
         * Tests the factory method for creating registry with custom HTTP client.
         */
        @Test
        @DisplayName("should create registry with custom HTTP client")
        void shouldCreateWithCustomHttpClient() {
            HttpClient customClient = HttpClient.newHttpClient();
            LanguageRegistry customRegistry = LanguageRegistry.withHttpClient(customClient);

            assertNotNull(customRegistry, "Registry should be created");
            assertTrue(customRegistry.getSupportedLanguageCount() > 0, "Should have default languages");
        }

        /**
         * Tests the factory method for creating registry with custom object mapper.
         */
        @Test
        @DisplayName("should create registry with custom object mapper")
        void shouldCreateWithCustomObjectMapper() {
            ObjectMapper customMapper = new ObjectMapper();
            LanguageRegistry customRegistry = LanguageRegistry.withObjectMapper(customMapper);

            assertNotNull(customRegistry, "Registry should be created");
            assertTrue(customRegistry.getSupportedLanguageCount() > 0, "Should have default languages");
        }

        /**
         * Tests the default constructor.
         */
        @Test
        @DisplayName("should create registry with default constructor")
        void shouldCreateWithDefaultConstructor() {
            LanguageRegistry defaultRegistry = new LanguageRegistry();

            assertNotNull(defaultRegistry, "Registry should be created");
            assertTrue(defaultRegistry.getSupportedLanguageCount() > 0, "Should have default languages");
        }
    }
}