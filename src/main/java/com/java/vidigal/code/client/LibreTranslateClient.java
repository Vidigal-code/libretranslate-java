package com.java.vidigal.code.client;

import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.request.TranslationRequest;
import com.java.vidigal.code.request.TranslationResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for interacting with the LibreTranslate translation API.
 * <p>
 * Provides methods for performing synchronous and asynchronous translation requests
 * using the LibreTranslate API.
 * </p>
 *
 * @author Vidigal
 */
public interface LibreTranslateClient {

    /**
     * Translates text synchronously using the LibreTranslate API.
     *
     * @param request the translation request containing text and language details
     * @return the translation response from the API
     * @throws LibreTranslateException if the translation fails due to API or network issues
     */
    TranslationResponse translate(TranslationRequest request) throws LibreTranslateException;

    /**
     * Translates text asynchronously using the LibreTranslate API with virtual threads.
     *
     * @param request the translation request containing text and language details
     * @return a {@link CompletableFuture} containing the translation response
     */
    CompletableFuture<TranslationResponse> translateAsync(TranslationRequest request);

    void close();
}
