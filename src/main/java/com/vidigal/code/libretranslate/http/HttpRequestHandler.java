package com.vidigal.code.libretranslate.http;

import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.ratelimit.RateLimiterService;
import com.vidigal.code.libretranslate.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of HttpRequestService for handling HTTP requests to LibreTranslate API.
 * <p>
 * This class manages connection setup, request formatting, and response handling,
 * including specific logic for translation responses and rate limiting scenarios.
 */
public class HttpRequestHandler implements HttpRequestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestHandler.class);

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String ERROR_MESSAGE_EMPTY_RESPONSE = "Empty response from server";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String DEFAULT_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 5;

    private final LibreTranslateConfig config;

    /**
     * Creates a new HttpRequestHandler with the given configuration.
     *
     * @param config The LibreTranslate configuration
     */
    public HttpRequestHandler(LibreTranslateConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse sendHttpRequest(String url, String method, Map<String, String> params) throws TranslationException {
        HttpURLConnection connection = null;
        long startTime = System.currentTimeMillis();

        try {
            connection = setupConnection(url, method);

            if ("POST".equals(method) && params != null && !params.isEmpty()) {
                writeRequestBody(connection, params);
            }

            return readResponse(connection, startTime);
        } catch (IOException e) {
            throw new TranslationException("Failed to send HTTP request: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Sets up the HTTP connection with appropriate headers and timeouts.
     *
     * @param url    The URL to connect to
     * @param method The HTTP method to use
     * @return Configured HttpURLConnection
     * @throws IOException If an I/O error occurs
     */
    private HttpURLConnection setupConnection(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setDoOutput("POST".equals(method));
        connection.setConnectTimeout(config.getConnectionTimeout());
        connection.setReadTimeout(config.getSocketTimeout());

        // Set common headers
        connection.setRequestProperty("Content-Type", DEFAULT_CONTENT_TYPE);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "LibreTranslateJava/1.0");

        return connection;
    }

    /**
     * Writes the request parameters to the connection output stream.
     *
     * @param connection The connection to write to
     * @param params     The parameters to write
     * @throws IOException If an I/O error occurs
     */
    private void writeRequestBody(HttpURLConnection connection, Map<String, String> params) throws IOException {
        String formData = encodeFormData(params);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = formData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    /**
     * Encodes parameters as form data.
     *
     * @param params The parameters to encode
     * @return Encoded form data string
     * @throws IOException If encoding fails
     */
    private String encodeFormData(Map<String, String> params) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }

        return result.toString();
    }

    /**
     * Reads the HTTP response into an HttpResponse object.
     *
     * @param connection The connection to read from
     * @param startTime  The start time of the request (for timing)
     * @return An HttpResponse object
     * @throws IOException If an I/O error occurs
     */
    private HttpResponse readResponse(HttpURLConnection connection, long startTime) throws IOException {
        int responseCode = connection.getResponseCode();
        Map<String, String> headers = extractHeaders(connection);
        String responseBody = readResponseBody(connection, responseCode);
        long responseTime = System.currentTimeMillis() - startTime;

        return new HttpResponse(responseCode, headers, responseBody, responseTime);
    }

    /**
     * Extracts headers from the HTTP connection.
     *
     * @param connection The connection to extract headers from
     * @return A map of header names to values
     */
    private Map<String, String> extractHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new HashMap<>();

        for (int i = 0; ; i++) {
            String headerName = connection.getHeaderFieldKey(i);
            String headerValue = connection.getHeaderField(i);

            if (headerName == null && headerValue == null) {
                break;
            }

            if (headerName != null) {
                headers.put(headerName, headerValue);
            }
        }

        return headers;
    }

    /**
     * Reads the response body from the connection.
     *
     * @param connection   The connection to read from
     * @param responseCode The HTTP response code
     * @return The response body as a string
     * @throws IOException If an I/O error occurs
     */
    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400
                                ? connection.getErrorStream()
                                : connection.getInputStream(),
                        StandardCharsets.UTF_8)
        )) {
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return response.toString();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handleTranslationResponse(String responseBody) throws TranslationException {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new TranslationException(ERROR_MESSAGE_EMPTY_RESPONSE);
        }

        try {
            Map<String, Object> responseMap = JsonUtil.parseJson(responseBody);

            if (responseMap.containsKey("translatedText")) {
                return (String) responseMap.get("translatedText");
            } else if (responseMap.containsKey("error")) {
                throw new TranslationException("API error: " + responseMap.get("error"));
            } else {
                throw new TranslationException("Unexpected response format: " + responseBody);
            }
        } catch (Exception e) {
            throw new TranslationException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleRateLimitExceeded(HttpResponse response) throws TranslationException {
        if (!response.isRateLimited()) {
            return false;
        }

        LOGGER.warn("Rate limit exceeded (HTTP 429), handling backoff");

        // Get retry-after header or use default
        String retryAfterHeader = response.getHeader(RETRY_AFTER_HEADER);
        int retryAfterSeconds = DEFAULT_RETRY_AFTER_SECONDS;

        if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
            try {
                retryAfterSeconds = Integer.parseInt(retryAfterHeader);
                LOGGER.debug("Using Retry-After header value: {} seconds", retryAfterSeconds);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid Retry-After header value: {}", retryAfterHeader);
            }
        }

        // Notify rate limiter if available
        RateLimiterService rateLimiter = config.getRateLimiter();
        if (rateLimiter != null) {
            rateLimiter.notifyRateLimitExceeded(retryAfterSeconds);
            LOGGER.debug("Notified rate limiter of exceeded limit, adjusted settings");
        } else {
            // If no rate limiter is configured, implement a fallback
            try {
                LOGGER.debug("No rate limiter configured, waiting for {} seconds", retryAfterSeconds);
                Thread.sleep(retryAfterSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranslationException("Interrupted while waiting for rate limit cooldown", e);
            }
        }

        return true;
    }
} 