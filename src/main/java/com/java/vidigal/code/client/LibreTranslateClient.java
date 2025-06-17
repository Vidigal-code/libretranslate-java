package com.java.vidigal.code.client;

import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.request.TranslationRequest;
import com.java.vidigal.code.request.TranslationResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for interacting with the LibreTranslate translation API.
 * <p>
 * This interface provides a comprehensive client for performing text translations
 * using the LibreTranslate API. It supports both synchronous and asynchronous
 * translation operations, with the asynchronous methods utilizing virtual threads
 * for improved performance and resource utilization.
 * </p>
 * <p>
 * LibreTranslate is a free and open-source machine translation API that supports
 * multiple language pairs. This client abstracts the HTTP communication details
 * and provides a clean, type-safe interface for translation operations.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * LibreTranslateClient client = // ... obtain client implementation
 *
 * // Synchronous translation
 * TranslationRequest request = new TranslationRequest("Hello", "en", "es");
 * TranslationResponse response = client.translate(request);
 *
 * // Asynchronous translation
 * CompletableFuture<TranslationResponse> futureResponse = client.translateAsync(request);
 * futureResponse.thenAccept(result -> System.out.println(result.getTranslatedText()));
 *
 * // Always close the client when done
 * client.close();
 * }</pre>
 *
 * @author Vidigal
 * @since 1.0
 * @see TranslationRequest
 * @see TranslationResponse
 * @see LibreTranslateException
 */
public interface LibreTranslateClient {

    /**
     * Translates text synchronously using the LibreTranslate API.
     * <p>
     * This method performs a blocking call to the LibreTranslate API and waits
     * for the translation to complete before returning the result. Use this method
     * when you need the translation result immediately and can afford to block
     * the current thread.
     * </p>
     *
     * @param request the translation request containing the source text, source language,
     *                target language, and other translation parameters. Must not be {@code null}.
     * @return the translation response containing the translated text and metadata
     *         from the API. Never returns {@code null}.
     * @throws LibreTranslateException if the translation fails due to API errors,
     *                                network connectivity issues, invalid language codes,
     *                                rate limiting, or server unavailability
     * @throws IllegalArgumentException if the request parameter is {@code null}
     * @see #translateAsync(TranslationRequest)
     */
    TranslationResponse translate(TranslationRequest request) throws LibreTranslateException;

    /**
     * Translates text asynchronously using the LibreTranslate API with virtual threads.
     * <p>
     * This method performs a non-blocking call to the LibreTranslate API and returns
     * a {@link CompletableFuture} that will be completed when the translation is finished.
     * The implementation uses virtual threads for efficient resource utilization,
     * making it suitable for high-concurrency scenarios.
     * </p>
     * <p>
     * The returned {@link CompletableFuture} can be used to chain additional operations,
     * handle exceptions, or combine multiple translation requests.
     * </p>
     *
     * @param request the translation request containing the source text, source language,
     *                target language, and other translation parameters. Must not be {@code null}.
     * @return a {@link CompletableFuture} that will complete with the translation response
     *         containing the translated text and metadata. The future will complete
     *         exceptionally if the translation fails. Never returns {@code null}.
     * @throws IllegalArgumentException if the request parameter is {@code null}
     * @see #translate(TranslationRequest)
     * @see CompletableFuture
     */
    CompletableFuture<TranslationResponse> translateAsync(TranslationRequest request);

    /**
     * Closes the client and releases all associated resources.
     * <p>
     * This method should be called when the client is no longer needed to ensure
     * proper cleanup of resources such as HTTP connections, thread pools, and
     * other system resources. After calling this method, the client should not
     * be used for further translation operations.
     * </p>
     * <p>
     * This method is idempotent - calling it multiple times has the same effect
     * as calling it once. It does not throw exceptions and will complete silently
     * even if resources have already been released.
     * </p>
     * <p>
     * <strong>Note:</strong> Implementations should ensure that any pending
     * asynchronous operations are allowed to complete before releasing resources,
     * or provide a reasonable timeout mechanism.
     * </p>
     *
     * @apiNote Consider implementing {@link AutoCloseable} in concrete implementations
     *          to support try-with-resources statements for automatic resource management.
     */
    void close();
}