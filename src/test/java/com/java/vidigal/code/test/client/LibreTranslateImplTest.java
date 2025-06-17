package com.java.vidigal.code.test.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.vidigal.code.client.LibreTranslateClientImpl;
import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.request.TranslationRequest;
import com.java.vidigal.code.utilities.config.LibreTranslateConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link LibreTranslateClientImpl} class.
 */
class LibreTranslateImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private ObjectMapper objectMapper;

    private LibreTranslateClientImpl client;
    private LibreTranslateConfig config;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        config = LibreTranslateConfig.builder()
                .apiUrl("https://translate.fedilab.app/translate")
                .apiKey("test-api-key")
                .enableCache(true)
                .cacheTtlMillis(3_600_000)
                .maxRequestsPerSecond(10)
                .maxRetries(3)
                .rateLimitCooldown(5000)
                .connectionTimeout(5000)
                .socketTimeout(10000)
                .enableRetry(true)
                .build();

        client = new LibreTranslateClientImpl(config, httpClient, objectMapper);

        // Inject mock HttpClient
        Field httpClientField = LibreTranslateClientImpl.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(client, httpClient);

        // Mock JSON serialization
        when(objectMapper.writeValueAsString(any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> body = invocation.getArgument(0);
            return "{\"q\": " + (body.get("q") instanceof List ? body.get("q") : "\"" + body.get("q") + "\"") +
                    ", \"source\": \"" + body.getOrDefault("source", "auto") + "\"" +
                    ", \"target\": \"" + body.get("target") + "\"" +
                    ", \"format\": \"text\"" +
                    ", \"api_key\": \"" + body.getOrDefault("api_key", "test-api-key") + "\"}";
        });

        // Mock default HTTP response
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"translatedText\": \"Hola\", \"detected_source_language\": \"en\"}");
        when(objectMapper.readValue(eq("{\"translatedText\": \"Hola\", \"detected_source_language\": \"en\"}"), eq(new TypeReference<Map<String, Object>>() {
        })))
                .thenReturn(Map.of("translatedText", "Hola", "detected_source_language", "en"));
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        mocks.close();
        // Reset interrupted status to prevent affecting other tests
        Thread.interrupted();
    }

    @Test
    void shouldHandleInterruptedException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        TranslationRequest request = new TranslationRequest(List.of("Hello"), "es", "en");
        LibreTranslateException exception = assertThrows(
                LibreTranslateException.class,
                () -> client.translate(request),
                "Expected LibreTranslateException for interrupted translation"
        );
        assertTrue(exception.getMessage().toLowerCase().contains("interrupt"), "Exception message should indicate interruption");
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldReturnMonitoringStats() {
        LibreTranslateClientImpl.MonitoringStats stats = client.getMonitoringStats();
        assertNotNull(stats, "Monitoring stats should not be null");
        assertEquals(0, stats.successCount(), "Success count should be 0 initially");
        assertEquals(0, stats.failureCount(), "Failure count should be 0 initially");
        assertEquals(0.0, stats.averageLatencyMillis(), 0.01, "Average latency should be 0.0 initially");
        assertTrue(stats.errorTypeCounts().isEmpty(), "Error type counts should be empty initially");
    }
}