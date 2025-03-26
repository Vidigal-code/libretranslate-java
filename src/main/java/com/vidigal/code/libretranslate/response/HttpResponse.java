package com.vidigal.code.libretranslate.response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enhanced HTTP response representation with additional utility methods.
 *
 * Provides:
 * - Immutable response data
 * - Safe header and status code access
 * - Convenience methods for response analysis
 */
public class HttpResponse {

    private final int statusCode;
    private final String body;
    private final Map<String, List<String>> headers;

    /**
     * Constructs an HttpResponse with validated parameters.
     *
     * @param statusCode The HTTP status code of the response
     * @param body       The response body
     * @param headers    The response headers
     */
    public HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body != null ? body : "";
        this.headers = headers != null
                ? Collections.unmodifiableMap(headers)
                : Collections.emptyMap();
    }

    /**
     * Retrieves the HTTP status code of the response.
     *
     * @return The HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Retrieves the response body as a string.
     *
     * @return The response body, never null
     */
    public String getBody() {
        return body;
    }

    /**
     * Retrieves the response headers.
     *
     * @return An unmodifiable map of response headers
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Checks if the response is successful (status code 200-299).
     *
     * @return True if the response is successful, false otherwise
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Retrieves a specific header value.
     *
     * @param headerName The name of the header
     * @return Optional containing the first header value, or empty if not found
     */
    public Optional<String> getFirstHeaderValue(String headerName) {
        return Optional.ofNullable(headers.get(headerName))
                .filter(values -> !values.isEmpty())
                .map(values -> values.get(0));
    }

    /**
     * Checks if the response body is empty.
     *
     * @return True if the body is empty or consists only of whitespace
     */
    public boolean isBodyEmpty() {
        return body == null || body.trim().isEmpty();
    }

    /**
     * Creates a string representation of the HTTP response.
     *
     * @return A descriptive string of the response
     */
    @Override
    public String toString() {
        return String.format("HttpResponse [statusCode=%d, bodyLength=%d, headers=%d]",
                statusCode, body.length(), headers.size());
    }
}