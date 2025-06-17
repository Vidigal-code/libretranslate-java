package com.java.vidigal.code.language;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thread-safe registry for managing supported LibreTranslate translation languages.
 * Can be initialized with default languages and updated dynamically via the LibreTranslate API.
 * <p>
 * This class supports dependency injection for better testability and flexibility.
 * </p>
 *
 * @author Vidigal
 */
public class LanguageRegistry {

    private static final Logger logger = LoggerFactory.getLogger(LanguageRegistry.class);

    private final Set<String> supportedLanguages = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean hasFetchedFromApi = new AtomicBoolean(false);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new LanguageRegistry with default HTTP client and object mapper.
     * This constructor is primarily used in production environments.
     */
    public LanguageRegistry() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * Constructs a new LanguageRegistry with provided dependencies.
     * This constructor enables dependency injection for better testability.
     *
     * @param httpClient   the HTTP client to use for API requests
     * @param objectMapper the object mapper for JSON serialization/deserialization
     */
    public LanguageRegistry(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        resetToDefaults();
    }

    /**
     * Factory method to create a LanguageRegistry with a custom HttpClient.
     * Useful when specific HTTP client settings are needed.
     *
     * @param httpClient custom HTTP client
     * @return new LanguageRegistry instance
     */
    public static LanguageRegistry withHttpClient(HttpClient httpClient) {
        return new LanguageRegistry(httpClient, new ObjectMapper());
    }

    /**
     * Factory method to create a LanguageRegistry with a custom ObjectMapper.
     * Useful when specific JSON processing configurations are needed.
     *
     * @param objectMapper custom object mapper
     * @return new LanguageRegistry instance
     */
    public static LanguageRegistry withObjectMapper(ObjectMapper objectMapper) {
        return new LanguageRegistry(HttpClient.newHttpClient(), objectMapper);
    }

    /**
     * Asynchronously fetches supported languages from the LibreTranslate API.
     * Only fetches once unless reset.
     *
     * @param apiUrl the LibreTranslate API base URL (e.g., "https://translate.fedilab.app")
     * @param apiKey the LibreTranslate authentication key (optional)
     * @return a CompletableFuture indicating success (true) or failure (false)
     * @throws IllegalArgumentException if {@code apiUrl} is null or blank
     */
    public CompletableFuture<Boolean> fetchFromApi(String apiUrl, String apiKey) {
        if (apiUrl == null || apiUrl.isBlank()) {
            logger.error("API URL cannot be null or blank");
            throw new IllegalArgumentException("API URL cannot be null or blank");
        }

        if (hasFetchedFromApi.getAndSet(true)) {
            logger.debug("Languages already fetched from API, skipping");
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/languages"))
                        .GET();

                if (apiKey != null && !apiKey.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, String>> languages = objectMapper.readValue(
                            response.body(),
                            new TypeReference<List<Map<String, String>>>() {
                            }
                    );

                    Set<String> apiLanguages = languages.stream()
                            .map(lang -> lang.get("code"))
                            .filter(code -> code != null && !code.isBlank())
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet());

                    supportedLanguages.addAll(apiLanguages);
                    logger.info("Successfully fetched {} languages from API", apiLanguages.size());

                    return true;
                } else {
                    logger.error("Failed to fetch languages, status: {}, body: {}",
                            response.statusCode(), response.body());
                    hasFetchedFromApi.set(false);
                    return false;
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while fetching languages from API", e);
                Thread.currentThread().interrupt();
                hasFetchedFromApi.set(false);
                return false;
            } catch (Exception e) {
                logger.error("Exception while fetching languages from API", e);
                hasFetchedFromApi.set(false);
                return false;
            }
        });
    }

    /**
     * Checks if a language code is supported (case-insensitive).
     *
     * @param code the language code (e.g., "EN", "fr")
     * @return true if supported, false otherwise
     */
    public boolean isSupported(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return supportedLanguages.contains(code.toLowerCase());
    }

    /**
     * Adds a custom language code to the supported set.
     *
     * @param code the language code to add
     */
    public void addLanguage(String code) {
        if (code != null && !code.isBlank()) {
            supportedLanguages.add(code.toLowerCase());
            logger.debug("Added custom language: {}", code);
        }
    }

    /**
     * Removes a language code from from the supported set.
     *
     * @param code the language code to remove
     */
    public void removeLanguage(String code) {
        if (code != null && !code.isBlank()) {
            supportedLanguages.remove(code.toLowerCase());
            logger.debug("Removed language: {}", code);
        }
    }

    /**
     * Resets supported languages to defaults from {@link Language}.
     */
    public void resetToDefaults() {
        supportedLanguages.clear();
        Set<String> defaultCodes = Stream.of(Language.values())
                .map(lang -> lang.getCode().toLowerCase())
                .collect(Collectors.toSet());
        supportedLanguages.addAll(defaultCodes);
        hasFetchedFromApi.set(false);
        logger.debug("Reset to default languages: {} languages loaded", defaultCodes.size());
    }

    /**
     * Returns an unmodifiable view of supported language codes.
     *
     * @return unmodifiable set of language codes
     */
    public Set<String> getAllSupportedLanguages() {
        return Collections.unmodifiableSet(supportedLanguages);
    }

    /**
     * Returns the number of currently supported languages.
     *
     * @return the count of supported languages
     */
    public int getSupportedLanguageCount() {
        return supportedLanguages.size();
    }

    /**
     * Checks if the registry has fetched languages from the API.
     *
     * @return true if API fetch has been attempted, false otherwise
     */
    public boolean hasFetchedFromApi() {
        return hasFetchedFromApi.get();
    }

    /**
     * Resets the API fetch flag to, allowing a new fetch to be performed.
     * This method is primarily intended for testing purposes.
     */
    public void resetApiFetchFlag() {
        hasFetchedFromApi.set(false);
        logger.debug("Reset API fetch flag reset");
    }

}


