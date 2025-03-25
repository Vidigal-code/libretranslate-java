package com.vidigal.code.libretranslate.response;

import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;
import com.vidigal.code.libretranslate.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for handling HTTP requests in the LibreTranslate client.
 * Manages connection, request building, and response handling.
 */
public class RequestHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHelper.class);

    /**
     * HTTP status code for rate limit exceeded
     */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * Error message for empty server responses
     */
    private static final String ERROR_MESSAGE_EMPTY_RESPONSE = "Empty response from server";


    private final LibreTranslateConfig config;

    /**
     * Constructs a RequestHelper with the given configuration.
     *
     * @param config Configuration for HTTP requests
     */
    public RequestHelper(LibreTranslateConfig config) {
        this.config = config;
    }

    /**
     * Sends an HTTP request and returns the response.
     *
     * @param apiUrl The API URL to send the request to
     * @param method The HTTP method (e.g., "GET", "POST")
     * @param params The request parameters
     * @return The HTTP response
     * @throws IOException If the request fails
     */
    public HttpResponse sendHttpRequest(String apiUrl, String method, Map<String, String> params) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = setupConnection(apiUrl, method);

            if ("POST".equals(method)) {
                sendRequestBody(connection, params);
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);

            // Handle rate limiting (429 Too Many Requests)
            if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                return handleRateLimitExceeded(connection, apiUrl, method, params);
            } else if (responseCode >= 400) {
                LOGGER.error("HTTP request failed with code {}: {}", responseCode, responseBody);
            }

            return new HttpResponse(responseCode, responseBody, connection.getHeaderFields());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Sets up an HTTP connection with appropriate headers and timeouts.
     *
     * @param apiUrl The API URL to connect to
     * @param method The HTTP method to use
     * @return A configured HttpURLConnection
     * @throws IOException if opening the connection fails
     */
    private HttpURLConnection setupConnection(String apiUrl, String method) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(config.getConnectionTimeout());
        connection.setReadTimeout(config.getSocketTimeout());
        return connection;
    }

    /**
     * Sends the request body to the connection's output stream.
     *
     * @param connection The HTTP connection
     * @param params     The parameters to send
     * @throws IOException if writing to the output stream fails
     */
    private void sendRequestBody(HttpURLConnection connection, Map<String, String> params) throws IOException {
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(buildRequestBody(params).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads the response body from the connection.
     *
     * @param connection   The HTTP connection
     * @param responseCode The HTTP response code
     * @return The response body as a string
     * @throws IOException if reading the response fails
     */
    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        try (InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line.trim());
            }
        }
        return responseBody.toString();
    }

    /**
     * Handles the case when the rate limit is exceeded (HTTP 429).
     *
     * @param connection The HTTP connection
     * @param apiUrl     The API URL
     * @param method     The HTTP method
     * @param params     The request parameters
     * @return The HTTP response after retrying
     * @throws IOException          if the request fails
     * @throws InterruptedException if waiting is interrupted
     */
    private HttpResponse handleRateLimitExceeded(HttpURLConnection connection, String apiUrl,
                                                String method, Map<String, String> params)
            throws IOException, InterruptedException {

        String retryAfterHeader = connection.getHeaderField("Retry-After");
        int retryAfterSeconds = -1;
        
        // Parse Retry-After header if it exists
        if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
            try {
                retryAfterSeconds = Integer.parseInt(retryAfterHeader);
                LOGGER.info("Server provided Retry-After: {} seconds", retryAfterSeconds);
            } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse Retry-After header: {}", retryAfterHeader);
            }
        }

        // Notify the application of rate limit response
        if (config.getRateLimiter() != null) {
            // Let the rate limiter handle the adaptive backoff
            config.getRateLimiter().notifyRateLimitExceeded(retryAfterSeconds);
            LOGGER.info("Rate limiter notified of 429 response");
        } else {
            // Fall back to fixed cooldown if no rate limiter provided
            long retryAfterMs = retryAfterSeconds > 0
                    ? retryAfterSeconds * 1000L 
                    : config.getRateLimitCooldown();
            
            LOGGER.warn("Rate limit exceeded. Waiting for {}ms before retry", retryAfterMs);
            Thread.sleep(retryAfterMs);
        }
        
        return sendHttpRequest(apiUrl, method, params); // Retry the request
    }

    /**
     * Builds the request body from parameters.
     *
     * @param params The parameters map
     * @return The encoded request body
     * @throws UnsupportedEncodingException If encoding fails
     */
    private String buildRequestBody(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return result.toString();
    }

    /**
     * Handles the translation response from the API.
     * Parses the JSON response and extracts the translated text.
     *
     * @param responseBody The raw response body from the API
     * @return The translated text if successful
     * @throws TranslationException If the response is invalid or contains errors
     */
    public String handleTranslationResponse(String responseBody) {
        if (LibreTranslateClient.isEmpty(responseBody)) {
            throw new TranslationException(ERROR_MESSAGE_EMPTY_RESPONSE);
        }

        try {
            Map<String, Object> jsonResponse = JsonUtil.fromJson(responseBody, Map.class);

            // Check for error response
            if (jsonResponse.containsKey("error")) {
                String errorMessage = (String) jsonResponse.get("error");
                throw new TranslationException("API returned error: " + errorMessage);
            }

            // Extract translated text
            if (jsonResponse.containsKey("translatedText")) {
                String translatedText = (String) jsonResponse.get("translatedText");
                if (LibreTranslateClient.isEmpty(translatedText)) {
                    throw new TranslationException("Translated text is empty in the response");
                }
                return translatedText;
            } else {
                throw new TranslationException("Unexpected response format: 'translatedText' field missing");
            }
        } catch (ClassCastException e) {
            throw new TranslationException("Unexpected data type in JSON response", e);
        }
    }
}