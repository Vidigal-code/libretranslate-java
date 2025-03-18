package com.vidigal.code.libretranslate.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractTranslatorClient {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractTranslatorClient.class);

    /**
     * Builds the headers for HTTP requests.
     *
     * @return Map of headers
     */
    protected Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        return headers;
    }

    /**
     * Logs and handles errors during translation.
     *
     * @param message Error message
     * @param e       Exception (optional)
     */
    protected void handleError(String message, Exception e) {
        LOGGER.error(message, e);
    }

    protected void handleError(String message) {
        LOGGER.error(message);
    }


    /**
     * Inner class to encapsulate HTTP response data.
     */
    protected static class HttpResponse {
        private final int statusCode;
        private final String body;
        private final Map<String, List<String>> headers;

        public HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }
    }


    /**
     * Inner class to CacheEntry.
     */
    protected static class CacheEntry {
        private final String value;
        private final long timestamp;

        public CacheEntry(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public String getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }


    /**
     * A caching mechanism has been added to store frequent translations,
     * reducing the number of unnecessary external API calls.
     **/

    protected final Map<String, CacheEntry> translationCache = new LinkedHashMap<String, CacheEntry>(100, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > 1000; // Limit of 1000 entries
        }
    };


}