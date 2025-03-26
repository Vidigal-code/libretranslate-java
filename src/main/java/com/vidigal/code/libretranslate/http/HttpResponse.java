package com.vidigal.code.libretranslate.http;

import java.util.Collections;
import java.util.Map;

/**
 * Represents an HTTP response with status code, headers, and body.
 * <p>
 * This class is immutable and thread-safe, making it safe to share between threads.
 */
public final class HttpResponse {
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final long responseTimeMs;

    /**
     * Creates a new HttpResponse with the given status code, headers, body, and response time.
     *
     * @param statusCode    The HTTP status code
     * @param headers       The HTTP response headers
     * @param body          The response body
     * @param responseTimeMs The response time in milliseconds
     */
    public HttpResponse(int statusCode, Map<String, String> headers, String body, long responseTimeMs) {
        this.statusCode = statusCode;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = body != null ? body : "";
        this.responseTimeMs = responseTimeMs;
    }

    /**
     * Gets the HTTP status code.
     *
     * @return The status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the HTTP response headers.
     *
     * @return An unmodifiable map of headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Gets a specific header value.
     *
     * @param name The header name (case-insensitive)
     * @return The header value, or null if not present
     */
    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        
        // Headers are often case-insensitive, so do a case-insensitive search
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Gets the response body.
     *
     * @return The response body
     */
    public String getBody() {
        return body;
    }

    /**
     * Gets the response time in milliseconds.
     *
     * @return The response time
     */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    /**
     * Checks if the response was successful (2xx status code).
     *
     * @return True if the status code is between 200 and 299
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Checks if the response indicates that the client is rate-limited (HTTP 429).
     *
     * @return True if the status code is 429 (Too Many Requests)
     */
    public boolean isRateLimited() {
        return statusCode == 429;
    }

    @Override
    public String toString() {
        return String.format("HttpResponse[statusCode=%d, bodyLength=%d, responseTime=%dms]",
                statusCode, body.length(), responseTimeMs);
    }
} 