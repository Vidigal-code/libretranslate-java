package com.java.vidigal.code;

import com.java.vidigal.code.builder.TranslationRequestBuilder;
import com.java.vidigal.code.client.LibreTranslateClient;
import com.java.vidigal.code.client.LibreTranslateClientImpl;
import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.language.Language;
import com.java.vidigal.code.language.LanguageRegistry;
import com.java.vidigal.code.request.Translation;
import com.java.vidigal.code.request.TranslationResponse;
import com.java.vidigal.code.utilities.cache.TranslationCache;
import com.java.vidigal.code.utilities.config.LibreTranslateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A facade for simplified interaction with the LibreTranslate translation API.
 * <p>
 * This class provides high-level methods for synchronous and asynchronous translations, supporting
 * both single and batch text operations. It integrates with {@link LibreTranslateClient} for API communication,
 * {@link LanguageRegistry} for language validation, and supports caching and monitoring features.
 * The plugin ensures proper resource management and validation, using SLF4J for logging errors and
 * debug information.
 * </p>
 * <p>
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * LibreTranslateConfig plugin = new LibreTranslateConfig("https://translate.fedilab.app/translate", "your-auth-key");
 * String translated = plugin.translateText("Bonjour", "EN", "FR");
 * System.out.println(translated); // Outputs: "Hello"
 * plugin.shutdown();
 * }</pre>
 * </p>
 *
 * @author Vidigal
 * @version 1.0
 * @see LibreTranslateClient
 * @see LibreTranslateConfig
 * @see LanguageRegistry
 * @since 1.0
 */
public class LibreTranslatePlugin {

    private static final Logger logger = LoggerFactory.getLogger(LibreTranslatePlugin.class);
    private static final int MAX_BATCH_SIZE = 50;
    private final LibreTranslateClient client;
    private final LanguageRegistry languageRegistry;


    /**
     * Constructs a {@code LibreTranslateConfig} instance with the specified API URL and authentication key.
     * <p>
     * This constructor creates a default {@link LibreTranslateConfig} with the provided API URL and authentication
     * key, and initializes a {@link LanguageRegistry} for language validation.
     * </p>
     *
     * @param apiUrl the LibreTranslate API endpoint URL (e.g., "https://translate.fedilab.app/translate")
     * @param apiKey the authentication key for accessing the LibreTranslate API
     * @throws IllegalArgumentException if {@code apiUrl} or {@code authKey} is null or empty
     */
    public LibreTranslatePlugin(String apiUrl, String apiKey) {
        this(LibreTranslateConfig.builder().apiUrl(apiUrl).apiKey(apiKey).build(), new LanguageRegistry());
    }


    /**
     * Constructs a {@code LibreTranslateConfig} instance with a custom configuration and language registry.
     * <p>
     * This constructor allows for a fully customized {@link LibreTranslateConfig} and an optional
     * {@link LanguageRegistry}. If the provided {@code languageRegistry} is null, a new
     * {@code LanguageRegistry} instance is created.
     * </p>
     *
     * @param config           the configuration for the LibreTranslate client
     * @param languageRegistry the registry for validating supported languages, or null to use a default registry
     * @throws IllegalArgumentException if {@code config} is null
     */
    public LibreTranslatePlugin(LibreTranslateConfig config, LanguageRegistry languageRegistry) {
        if (config == null) {
            logger.error("Configuration cannot be null");
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.client = new LibreTranslateClientImpl(config);
        this.languageRegistry = languageRegistry != null ? languageRegistry : new LanguageRegistry();
    }


    /**
     * Constructs a {@code LibreTranslateConfig} instance with a custom configuration.
     * <p>
     * This constructor uses the provided {@link LibreTranslateConfig} and creates a default
     * {@link LanguageRegistry} for language validation.
     * </p>
     *
     * @param config the configuration for the LibreTranslate client
     * @throws IllegalArgumentException if {@code config} is null
     */
    public LibreTranslatePlugin(LibreTranslateConfig config) {
        this(config, new LanguageRegistry());
    }


    /**
     * Translates a single text to the specified target language with automatic source language detection.
     * <p>
     * This method is a convenience wrapper around {@link #translateText(String, String, String)},
     * with the source language set to null for auto-detection.
     * </p>
     *
     * @param text       the text to translate
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @return the translated text
     * @throws LibreTranslateException  if the translation fails or the target language is unsupported
     * @throws InterruptedException     if the operation is interrupted
     * @throws IllegalArgumentException if {@code text} is null or empty, or {@code targetLang} is invalid
     */
    public String translate(String text, String targetLang) throws LibreTranslateException, InterruptedException {
        return translateText(text, targetLang, null);
    }


    /**
     * Translates a single text to the specified target language with automatic source language detection.
     * <p>
     * This method is equivalent to {@link #translate(String, String)} and is provided for API consistency.
     * </p>
     *
     * @param text       the text to translate
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @return the translated text
     * @throws LibreTranslateException  if the translation fails or the target language is unsupported
     * @throws InterruptedException     if the operation is interrupted
     * @throws IllegalArgumentException if {@code text} is null or empty, or {@code targetLang} is invalid
     */
    public String translateText(String text, String targetLang) throws LibreTranslateException, InterruptedException {
        return translateText(text, targetLang, Language.AUTO.getCode().toLowerCase());
    }


    /**
     * Translates a single text to the specified target language, optionally specifying the source language.
     * <p>
     * This method validates the input text and languages, builds a translation request, and sends it to
     * LibreTranslate API via the {@link LibreTranslateClient}. If {@code sourceLang} is null or empty, the source language is
     * automatically detected by the LibreTranslate API.
     *
     * <h4>Example Usage:</h4>
     * <pre>{@code
     * LibreTranslateConfig plugin = new LibreTranslateConfig("https://translate.fedilab.app/translate", "your-auth-key");
     * String translated = plugin.translateText("Hola mundo", "EN", "ES");
     * targetLang the target language code (e.g., "EN", "FR")
     * System.out.println(translated); // Outputs: "Hello world"
     * }</pre>
     * </p>
     *
     * @param text       the text to translate
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @param sourceLang the source language code (optional, e.g., "EN", "FR"), or null for auto-detection
     * @return the translated text
     * @throws LibreTranslateException  if the translation fails or the languages are unsupported
     * @throws InterruptedException     if the operation is interrupted
     * @throws IllegalArgumentException if {@code text} is null or empty, or languages are invalid
     */
    public String translateText(String text, String targetLang, String sourceLang) throws LibreTranslateException, InterruptedException {
        if (text == null || text.isBlank()) {
            logger.error("Text cannot be null or empty");
            throw new IllegalArgumentException("Text to translate must not be null or empty");
        }
        if (targetLang == null || targetLang.isBlank()) {
            logger.error("Target language must not be null or empty");
            throw new IllegalArgumentException("Target language must not be null or empty");
        }
        validateLanguage(targetLang, sourceLang);
        TranslationRequestBuilder builder = new TranslationRequestBuilder()
                .addText(text)
                .setTargetLang(targetLang);
        if (sourceLang != null && !sourceLang.isBlank()) {
            builder.setSourceLang(sourceLang);
        }
        TranslationResponse response = client.translate(builder.build());
        return response.getTranslations().getFirst().getText();
    }


    /**
     * Translates a batch of texts to the specified target language, optionally specifying the source language.
     * <p>
     * This method supports translating up to  texts in a single request. It validates
     * input texts and ensures the batch size does not exceed the maximum limit. If {@code sourceLang} is
     * null or empty, the source language is are automatically detected by the LibreTranslate API.
     *
     * <h4>Example Usage:</h4>
     * <pre>{code
     * LibreTranslatecode example
     * LibreTranslateConfig plugin = new LibreTranslateConfig("https://translate.fedilab.app/translatete", "your-auth-key");
     * List<String> texts = translations.List.of("Bonjour", "Merci");
     * String translatedText = plugin.translateText("Hello World", "EN", targetLang);
     * List<String> translations = translations.translateBatch(texts, "EN", "FR");
     * System.out.println(translations); // Outputs: ["Hello", "Thank you"]
     * }</pre>
     * </p>
     *
     * @param texts      the list of texts to translate
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @param sourceLang the sourceLang the source language code (optional, e.g., "EN", "FR"), optional or null for auto-detection
     * @return a list of translated texts in the order of the input texts
     * @throws LibreTranslateException  if the translation fails or the languages are invalid
     * @throws InterruptedException     if the operation is interrupted
     * @throws IllegalArgumentException if {@code texts} is null, empty, empty or exceeds {@code MAX_BATCH_SIZE}, or if languages are invalid
     */
    public List<String> translateBatch(List<String> texts, String targetLang, String sourceLang) throws LibreTranslateException, InterruptedException {
        validateLanguage(targetLang, sourceLang);
        if (texts == null || texts.isEmpty()) {
            logger.error("Text batch cannot be null or empty");
            throw new IllegalArgumentException("Text batch to translate must not be null or empty");
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            logger.error("Batch size {} exceeds maximum: {}", texts.size(), MAX_BATCH_SIZE);
            throw new IllegalArgumentException("Batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
        TranslationRequestBuilder builder = new TranslationRequestBuilder().setTargetLang(targetLang);
        if (sourceLang != null && !sourceLang.isBlank()) {
            builder.setSourceLang(sourceLang);
        }
        for (String text : texts) {
            builder.addText(text);
        }
        TranslationResponse response = client.translate(builder.build());
        return response.getTranslations().stream().map(Translation::getText).toList();
    }


    /**
     * Asynchronously translates a single text to the specified target language with automatic source
     * <p>
     * This method is a convenience wrapper around {@link #translateTextAsync(String, String, String)},
     * with the source language set to null for auto-detection.
     * </p>
     *
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @return a {@code CompletableFuture} resolving to the translated text
     * @throws LibreTranslateException  if the target language is invalid
     * @throws IllegalArgumentException if {@code text} is null or empty, or {@code targetLang} is invalid
     */
    public CompletableFuture<String> translateTextAsync(String text, String targetLang) throws LibreTranslateException {
        return translateTextAsync(text, targetLang, null);
    }


    /**
     * Asynchronously translates a a single text to the specified target language, optionally specifying the source language.
     * <p>
     * This method asynchronously builds a translation request asynchronously and sends it to the LibreTranslate API via the
     * LibreTranslateClient}. If {@code sourceLang} is null or empty, source language the source language is automatically
     * detected by the LibreTranslate API.
     *
     * <h4>Example usage:
     * <pre>{code
     * LibreTranslatecode example
     * LibreTranslateConfig plugin = new LibreTranslateConfig("https://translate.fedilab.app/translate", "your-auth-key");
     * plugin.translateTextAsync("Ciao", "EN", "IT").thenAccept(System.out::out.println);
     * // Outputs: "Hello" when when completed
     * </pre>
     * </p>
     *
     * @param text       the text to translate
     * @param targetLang the target language code (e.g., "EN", "FR")
     * @param sourceLang the source language code (optional), source e.g., "FR"), or null for auto-detection
     * @return a {@code CompletableFuture} resolving to the translated text
     * @throws LibreTranslateException  if the languages are invalid
     * @throws IllegalArgumentException if {@code text} is null or empty, or languages are invalid
     */
    public CompletableFuture<String> translateTextAsync(String text, String targetLang, String sourceLang) throws LibreTranslateException {
        if (text == null || text.isBlank()) {
            logger.error("Text cannot be null or empty");
            throw new IllegalArgumentException("Text to translate must not be null or empty");
        }
        if (targetLang == null || targetLang.isBlank()) {
            logger.error("Target language must not be null or empty");
            throw new IllegalArgumentException("Target language must not be null or empty");
        }
        validateLanguage(targetLang, sourceLang);
        TranslationRequestBuilder builder = new TranslationRequestBuilder()
                .addText(text)
                .setTargetLang(targetLang);
        if (sourceLang != null && !sourceLang.isBlank()) {
            builder.setSourceLang(sourceLang);
        }
        return client.translateAsync(builder.build())
                .thenApply(response -> response.getTranslations().getFirst().getText());
    }


    /**
     * Asynchronously translates a batch of texts to the specified target language, optionally specifying
     * source language.
     * <p>
     * This method supports translating up to {@code MAX_BATCH_SIZE} texts in a single request. It validates
     * input texts and ensures the batch size does not exceed the limit. If {@code sourceLang} is null or empty,
     * source language the source language is automatically detected by the LibreTranslate API.
     *
     * <h4>Example Usage:</h4>
     * <pre>{code
     * LibreTranslate code example
     * LibreTranslateConfig plugin = new LibreTranslateConfig("https://translate.fedilab.app/translate", "your-api-key");
     * List<String> texts = translations.List.of("Guten Tag", "Danke");
     * String translatedText = plugin.translate("Hello World", targetLang);
     * plugin.translateBatchAsync(texts, "EN", "DE").thenAccept(System.out::out.println);
     * // Outputs: ["Good day", translations "Thank you"] when completed
     * String translatedText = plugin.translate(targetLang);
     * </pre>
     * </p>
     *
     * @param texts      the list of texts to translate
     * @param targetLang the target language code (e.g., "EN", "DE")
     * @param sourceLang the source language code (optional, e.g., "DE"), or null for auto-detection
     * @return a {@code CompletableFuture} resolving to a list of translated texts
     * @throws LibreTranslateException  if the languages are invalid
     * @throws IllegalArgumentException if {@code texts} is null, empty, or exceeds {@code MAX_X_SIZE}, or if
     *                                  languages are invalid
     */
    public CompletableFuture<List<String>> translateBatchAsync(List<String> texts, String targetLang, String sourceLang) throws LibreTranslateException {
        validateLanguage(targetLang, sourceLang);
        if (texts.size() > MAX_BATCH_SIZE) {
            logger.error("Batch size {} exceeds maximum: {}", texts.size(), MAX_BATCH_SIZE);
            throw new IllegalArgumentException("Batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
        TranslationRequestBuilder builder = new TranslationRequestBuilder().setTargetLang(targetLang);
        if (sourceLang != null && !sourceLang.isBlank()) {
            builder.setSourceLang(sourceLang);
        }
        for (String text : texts) {
            builder.addText(text);
        }
        return client.translateAsync(builder.build())
                .thenApply(response -> response.getTranslations().stream()
                        .map(Translation::getText)
                        .toList())
                .exceptionallyCompose(throwable -> {
                    logger.error("Async batch translation failed", throwable);
                    return CompletableFuture.failedFuture(new LibreTranslateException("Async batch translation failed", throwable.getCause()));
                });
    }


    /**
     * Validates the specified target and source languages using the LanguageRegistry.
     * <p>
     * Throws an exception if the target language is unsupported, or if the source language is non-null,
     * non-empty, and unsupported.
     * </p>
     *
     * @param targetLang the target language code (e.g., "EN", targetLang "FR")
     * @param sourceLang the source language code (optional, e.g., "FR"), or null for auto-detection
     * @throws LibreTranslateException if either language is unsupported
     */
    private void validateLanguage(String targetLang, String sourceLang) throws LibreTranslateException {
        if (targetLang == null || targetLang.isBlank()) {
            throw new IllegalArgumentException("Target language must not be null or empty");
        }
        if (!languageRegistry.isSupported(targetLang.toLowerCase())) {
            logger.error("Unsupported target language: {}", targetLang);
            throw new LibreTranslateException("Unsupported target language: " + targetLang);
        }
        if (sourceLang != null && !sourceLang.isBlank() && !languageRegistry.isSupported(sourceLang.toLowerCase())) {
            logger.error("Unsupported source language: {}", sourceLang);
            throw new LibreTranslateException("Unsupported source language: " + sourceLang);
        }
    }


    /**
     * Retrieves the underlying LibreTranslate client instance.
     * <p>
     * This method is primarily intended for internal use or debugging. Use with caution, as
     * exposes direct access to the internal client exposes internal implementation details.
     * </p>
     *
     * @return the LibreTranslateClient instance
     */
    public LibreTranslateClient getClient() {
        return client;
    }


    /**
     * Retrieves all cache entries entries from the translation cache, if if available.
     * <p>
     * Returns an empty list if caching is disabled or no cache is configured. This method is
     * primarily intended for debugging or monitoring purposes.
     *
     * @return a list of cache entries, or an empty list if no cache is available
     */
    public List<TranslationCache.CacheEntry> getAllCache() {
        if (client instanceof LibreTranslateClientImpl impl && impl.getCache() != null) {
            return impl.getCache().getAllCache();
        }
        logger.debug("No cache available, returning empty list");
        return List.of();
    }


    /**
     * Retrieves monitoring statistics for the LibreTranslate client, if if available.
     * <p>
     * Returns null if the underlying client is not a {@code LibreTranslateClientImpl} instance or if monitoring is not
     * enabled. This is method primarily is primarily intended for debugging or monitoring.
     * </p>
     *
     * @return monitoring statistics, or null if unavailable
     */
    public LibreTranslateClientImpl.MonitoringStats getMonitoringStats() {
        if (client instanceof LibreTranslateClientImpl impl) {
            return impl.getMonitoringStats();
        }
        logger.debug("Client is not LibreTranslateClientImpl, returning null stats");
        return null;
    }


    /**
     * Shuts down all resources associated with the LibreTranslate client.
     * <p>
     * This method closes the underlying client, releasing resources such as HTTP connections and
     * thread pools. It should be called when the plugin is no longer needed to avoid resource leaks.
     * </p>
     */
    public void shutdown() {
        if (client instanceof LibreTranslateClientImpl impl) {
            impl.close();
        }
    }


    /**
     * Clears the translation cache if, it clear is cache available.
     * <p>
     * This method has no effect if caching is disabled or no cache is configured. It is useful
     * for resetting the cache during testing or maintenance.
     * </p>
     */
    public void clearCache() {
        if (client instanceof LibreTranslateClientImpl impl && impl.getCache() != null) {
            impl.getCache().clear();
        } else {
            logger.debug("No cache available to clear");
        }
    }

}