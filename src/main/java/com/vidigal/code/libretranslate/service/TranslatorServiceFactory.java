package com.vidigal.code.libretranslate.service;

import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Factory for creating TranslatorService instances and testing API connections.
 */
public class TranslatorServiceFactory {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String ERROR_CONNECTION_FAILED = "Failed to establish connection with the API: {}";

    /**
     * Creates a new TranslatorService with the specified API URL and key.
     *
     * @param apiUrl LibreTranslate API URL
     * @param apiKey API key for the LibreTranslate service
     * @return TranslatorService instance
     */
    public static TranslatorService create(String apiUrl, String apiKey) {
        return create(new LibreTranslateConfig.Builder()
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .build());
    }

    /**
     * Creates a new TranslatorService with the specified configuration.
     *
     * @param config LibreTranslate configuration
     * @return TranslatorService instance
     */
    public static TranslatorService create(LibreTranslateConfig config) {
        return new LibreTranslateClient(config);
    }

    /**
     * Creates and returns a LibreTranslateConfig object with the specified API URL and API key.
     *
     * @param apiUrl URL of the LibreTranslate API
     * @param apiKey API key for the LibreTranslate service
     * @return Configured LibreTranslateConfig object
     */
    public static LibreTranslateConfig createConfig(String apiUrl, String apiKey) {
        return new LibreTranslateConfig.Builder()
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .build();
    }

    /**
     * Tests the connection to the specified API URL by sending a GET request and checking the response code.
     * <p>
     * This method evaluates the response code:
     * - Returns `true` if the response code is in the 2xx range (success) or 405 (Method Not Allowed).
     * - Returns `false` otherwise.
     *
     * @param apiUrl The URL of the API to test the connection to.
     * @return `true` if the connection is successful, otherwise `false`.
     */
    public static boolean testConnection(String apiUrl) {
        HttpURLConnection connection = null;

        try {
            connection = openConnection(apiUrl);
            int responseCode = connection.getResponseCode();
            return isSuccessfulResponse(responseCode);
        } catch (IOException e) {
            throw new TranslationException(ERROR_CONNECTION_FAILED.replace("{}", e.getMessage()));
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Opens an HTTP connection to the specified URL.
     *
     * @param apiUrl The URL to connect to
     * @return An open HttpURLConnection instance
     * @throws IOException If an I/O error occurs while opening the connection
     */
    private static HttpURLConnection openConnection(String apiUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        return connection;
    }

    /**
     * Checks if the response code indicates a successful connection.
     *
     * @param responseCode The HTTP response code
     * @return `true` if the response code is in the 2xx range or is 405, otherwise `false`
     */
    private static boolean isSuccessfulResponse(int responseCode) {
        return (responseCode >= 200 && responseCode < 300) || responseCode == 405;
    }

    /**
     * Closes the given HTTP connection to free up resources.
     *
     * @param connection The HttpURLConnection to close
     */
    private static void closeConnection(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }
}