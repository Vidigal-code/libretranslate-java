package com.java.vidigal.code.test;

import com.java.vidigal.code.LibreTranslatePlugin;
import com.java.vidigal.code.client.LibreTranslateClient;
import com.java.vidigal.code.client.LibreTranslateClientImpl;
import com.java.vidigal.code.exception.LibreTranslateException;
import com.java.vidigal.code.language.LanguageRegistry;
import com.java.vidigal.code.utilities.cache.TranslationCache;
import com.java.vidigal.code.utilities.config.LibreTranslateConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link LibreTranslatePlugin} class.
 * <p>
 * Tests the functionality of the {@link LibreTranslatePlugin}, including single and batch translations,
 * asynchronous operations, caching, rate limiting, error handling, and thread auto-closure. Uses mocked
 * dependencies to isolate the plugin's behavior.
 * </p>
 */
class LibreTranslatePluginTest {

    @Mock
    private LibreTranslateClient libreTranslateClient;

    @Mock
    private LanguageRegistry languageRegistry;

    private LibreTranslatePlugin plugin;
    private LibreTranslateConfig config;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws LibreTranslateException {
        mocks = MockitoAnnotations.openMocks(this);

        config = LibreTranslateConfig.builder()
                .apiUrl("https://translate.fedilab.app/translate")
                .apiKey("test-api-key")
                .enableCache(true)
                .cacheTtlMillis(3_600_000)
                .maxRequestsPerSecond(10)
                .enableRetry(true)
                .maxRetries(3)
                .rateLimitCooldown(5000)
                .connectionTimeout(5000)
                .socketTimeout(10000)
                .closedThreadAuto(true)
                .build();

        // Mock LanguageRegistry to support specific languages including "auto"
        when(languageRegistry.isSupported(any(String.class))).thenAnswer(invocation -> {
            String code = invocation.getArgument(0, String.class);
            if (code == null) return false;
            return switch (code.toLowerCase()) {
                case "es", "en", "pt", "auto" -> true;
                default -> false;
            };
        });

        // Initialize plugin with mocked client
        plugin = new LibreTranslatePlugin(config, languageRegistry) {
            @Override
            public LibreTranslateClient getClient() {
                return libreTranslateClient;
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        if (plugin != null && !config.isClosedThreadAuto()) {
            plugin.shutdown();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetLanguage() {
        LibreTranslateException exception = assertThrows(
                LibreTranslateException.class,
                () -> plugin.translateText("Hello", "xx"),
                "Expected LibreTranslateException for unsupported target language"
        );
        assertEquals("Unsupported target language: xx", exception.getMessage(),
                "Exception message should indicate unsupported target language");
        verify(languageRegistry).isSupported("xx");
        verifyNoInteractions(libreTranslateClient);
    }

    @Test
    void shouldThrowExceptionForUnsupportedSourceLanguage() {
        LibreTranslateException exception = assertThrows(
                LibreTranslateException.class,
                () -> plugin.translateText("Hello", "es", "xx"),
                "Expected LibreTranslateException for unsupported source language"
        );
        assertEquals("Unsupported source language: xx", exception.getMessage(),
                "Exception message should indicate unsupported source language");
        verify(languageRegistry).isSupported("es");
        verify(languageRegistry).isSupported("xx");
        verifyNoInteractions(libreTranslateClient);
    }


    @Test
    void shouldThrowExceptionForBatchExceedingMaxSize() {
        List<String> texts = Collections.nCopies(51, "Hello");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> plugin.translateBatch(texts, "es", "en"),
                "Expected IllegalArgumentException for batch size exceeding max"
        );
        assertEquals("Batch size exceeds maximum of 50", exception.getMessage(),
                "Exception message should indicate max batch size exceeded");
        verifyNoInteractions(libreTranslateClient);
    }


    @Test
    void shouldReturnEmptyCacheWhenCachingDisabled() {
        LibreTranslateConfig noCacheConfig = LibreTranslateConfig.builder()
                .apiUrl("https://test.translate.api/translate")
                .apiKey("test-api-key")
                .enableCache(false)
                .build();
        LibreTranslatePlugin pluginNoCache = new LibreTranslatePlugin(noCacheConfig, languageRegistry);
        List<TranslationCache.CacheEntry> cache = pluginNoCache.getAllCache();
        assertTrue(cache.isEmpty(), "Cache should be empty when caching is disabled");
    }

    @Test
    void shouldClearCacheWithoutException() {
        assertDoesNotThrow(() -> plugin.clearCache(), "Clear cache should not throw exception");
    }

    @Test
    void shouldHandleNullTextInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> plugin.translateText(null, "es"),
                "Should throw IllegalArgumentException for null text"
        );
        assertEquals("Text to translate must not be null or empty", exception.getMessage(),
                "Exception message should indicate invalid text input");
        verifyNoInteractions(libreTranslateClient);
    }

    @Test
    void shouldHandleEmptyTextInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> plugin.translateText("", "es"),
                "Should throw IllegalArgumentException for empty text"
        );
        assertEquals("Text to translate must not be null or empty", exception.getMessage(),
                "Exception message should indicate invalid text input");
        verifyNoInteractions(libreTranslateClient);
    }

    @Test
    void shouldHandleNullTargetLanguage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> plugin.translateText("Hello", null),
                "Should throw IllegalArgumentException for null target language"
        );
        assertEquals("Target language must not be null or empty", exception.getMessage(),
                "Exception message should indicate invalid target language");
        verifyNoInteractions(libreTranslateClient);
    }

    @Test
    void shouldHandleClientIOExceptionDuringShutdown() throws IOException {
        LibreTranslatePlugin realPlugin = new LibreTranslatePlugin(config, languageRegistry);
        LibreTranslateClientImpl clientImpl = (LibreTranslateClientImpl) realPlugin.getClient();
        LibreTranslateClient clientSpy = spy(clientImpl);

        doThrow(new IOException("Shutdown failed")).when(clientSpy).close();

        LibreTranslatePlugin pluginWithSpy = new LibreTranslatePlugin(config, languageRegistry) {
            @Override
            public LibreTranslateClient getClient() {
                return clientSpy;
            }
        };

        assertDoesNotThrow(pluginWithSpy::shutdown, "Shutdown should handle IOException gracefully");
    }


}