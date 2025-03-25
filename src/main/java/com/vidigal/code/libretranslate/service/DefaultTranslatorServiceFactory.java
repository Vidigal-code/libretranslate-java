package com.vidigal.code.libretranslate.service;

import com.vidigal.code.libretranslate.client.LibreTranslateClient;
import com.vidigal.code.libretranslate.config.LibreTranslateConfig;
import com.vidigal.code.libretranslate.exception.TranslationException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Default implementation of TranslatorServiceFactory.
 * <p>
 * Creates LibreTranslateClient instances and provides connection testing functionality.
 */
public class DefaultTranslatorServiceFactory implements TranslatorServiceFactory {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String ERROR_CONNECTION_FAILED = "Failed to establish connection with the API: {}";

    /**
     * {@inheritDoc}
     */
    @Override
    public TranslatorService create(String apiUrl, String apiKey) {
        return create(LibreTranslateConfig.builder()
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TranslatorService create(LibreTranslateConfig config) {
        return new LibreTranslateClient(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean testConnection(String apiUrl) {
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
     * Creates a LibreTranslateConfig object with the specified API URL and API key.
     *
     * @param apiUrl URL of the LibreTranslate API
     * @param apiKey API key for the LibreTranslate service
     * @return Configured LibreTranslateConfig object
     */
    public LibreTranslateConfig createConfig(String apiUrl, String apiKey) {
        return LibreTranslateConfig.builder()
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .build();
    }

    /**
     * Opens an HTTP connection to the specified URL.
     *
     * @param apiUrl The URL to connect to
     * @return An open HttpURLConnection instance
     * @throws IOException If an I/O error occurs while opening the connection
     */
    private HttpURLConnection openConnection(String apiUrl) throws IOException {
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
    private boolean isSuccessfulResponse(int responseCode) {
        return (responseCode >= 200 && responseCode < 300) || responseCode == 405;
    }

    /**
     * Closes the given HTTP connection to free up resources.
     *
     * @param connection The HttpURLConnection to close
     */
    private void closeConnection(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }
} 