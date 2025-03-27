package com.vidigal.code.libretranslate.http;

import com.vidigal.code.libretranslate.exception.TranslationException;

import java.util.Map;

/**
 * Interface for HTTP request handling service.
 * <p>
 * This interface defines the contract for sending HTTP requests and handling
 * responses for API integration. It follows the Interface Segregation Principle
 * by focusing only on HTTP communication.
 */
public interface HttpRequestService {

    /**
     * Sends an HTTP request to the specified URL with the given method and parameters.
     *
     * @param url    The URL to send the request to
     * @param method The HTTP method to use (e.g. "GET", "POST")
     * @param params The parameters to include in the request
     * @return An HttpResponse object containing the response data
     * @throws TranslationException if the request fails
     */
    HttpResponse sendHttpRequest(String url, String method, Map<String, String> params) throws TranslationException;

    /**
     * Extracts translated text from an API response.
     *
     * @param responseBody The response body to parse
     * @return The translated text
     * @throws TranslationException if parsing fails
     */
    String handleTranslationResponse(String responseBody) throws TranslationException;

    /**
     * Handles a rate limit exceeded response.
     *
     * @param response The HTTP response indicating the rate limit was exceeded
     * @return True if the rate limit handling was successful
     * @throws TranslationException if handling fails or if retry is not possible
     */
    boolean handleRateLimitExceeded(HttpResponse response) throws TranslationException;
} 