# LibreTranslate Java Client

A modern, feature-rich Java client for the LibreTranslate API with advanced capabilities including rate limiting, caching, and comprehensive monitoring. Compatible with JDK-17+.

## Key Features

- **Synchronous & Asynchronous Translation** - Flexible translation modes to suit different application needs
- **Smart Adaptive Rate Limiting** - Automatically adjusts request rates based on API responses
- **Configurable Translation Cache** - Reduces API calls for frequently translated text
- **Comprehensive Metrics Monitoring** - Track performance with detailed statistics
- **Resource Management** - Full `AutoCloseable` support for proper cleanup
- **Command Processing** - Process translation commands individually with flexible operation modes
- **Modular Architecture** - Clean separation of concerns with well-defined interfaces

## Project Structure

```
com.vidigal.code.libretranslate
├── cache           - Caching implementation for translations
├── client          - Core client implementation
├── config          - Configuration classes
├── exception       - Translation-specific exceptions
├── http            - HTTP request handling
├── language        - Language code utilities
├── ratelimit       - Rate limiting implementation
├── response        - Response processing utilities
├── service         - Service interfaces and factories
└── util            - Common utility classes
```

## Examples

**Test - 1**

![Example 1](https://github.com/Vidigal-code/libretranslate-java/blob/main/example/example-1.png?raw=true)

**Test - 2**

![Example 2](https://github.com/Vidigal-code/libretranslate-java/blob/main/example/example-2.png?raw=true)

## Quick Usage

### Basic Translation

```java
// Create a translator service with API URL and optional key
try (TranslatorService translator = Translators.create("https://libretranslate.com", "your-api-key")) {
    // Translate with automatic language detection
    String result = translator.translate("Hello world", "pt");
    System.out.println("Translated text: " + result);
    
    // Translate with specified source language
    String resultWithSource = translator.translate("Hello world", "en", "es");
    System.out.println("Translated to Spanish: " + resultWithSource);
}
```

### Asynchronous Translation

```java
try (TranslatorService translator = Translators.create("https://libretranslate.com", "your-api-key")) {
    // Perform an asynchronous translation
    CompletableFuture<String> future = translator.translateAsync("Hello world", "fr");
    
    // Handle the result when it completes
    future
        .thenAccept(text -> System.out.println("Translation: " + text))
        .exceptionally(ex -> {
            System.err.println("Translation failed: " + ex.getMessage());
            return null;
        });
        
    // Wait for completion if needed
    future.join();
}
```

## Advanced Features

### Custom Configuration

```java
// Create a custom configuration
LibreTranslateConfig config = LibreTranslateConfig.builder()
    .apiUrl("https://libretranslate.com")
    .apiKey("your-api-key")
    .connectionTimeout(5000)         // Connection timeout in ms
    .socketTimeout(10000)            // Socket timeout in ms
    .maxRequestsPerSecond(10)        // Rate limit
    .maxRetries(3)                   // Retry count for failures
    .rateLimitCooldown(5000)         // Cooldown after hitting rate limits
    .enableRetry(true)               // Enable automatic retries
    .build();

// Create translator with custom config
TranslatorService translator = Translators.create(config);
```

### Command Processing

The library provides a flexible command processing system for handling translation operations:

```java
// Commands follow a specific format
List<String> commands = List.of(
    "m:s;t:Hello;en;es",    // Synchronous mode (s)
    "m:as;t:World;en;pt",   // Asynchronous mode (as)
    "t:Thank you;en;ja"     // Default mode (implicit synchronous)
);

// Process commands (commands are processed individually)
List<String> results = translator.processCommands(commands, true);
results.forEach(System.out::println);
```

### Metrics Monitoring

```java
// Access metrics programmatically
System.out.println("Cache Hits: " + translator.getCacheHits());
System.out.println("Cache Misses: " + translator.getCacheMisses());

// Calculate cache hit ratio
double hitRatio = (double) translator.getCacheHits() / 
                 (translator.getCacheHits() + translator.getCacheMisses());
System.out.printf("Cache Hit Ratio: %.2f%%\n", hitRatio * 100);
```

## Error Handling

The library uses `TranslationException` for error conditions:

```java
try {
    String result = translator.translate("Hello", "invalid-language-code", "es");
} catch (TranslationException e) {
    System.err.println("Translation failed: " + e.getMessage());
    // Handle specific error conditions
}
```

## Complete Usage Examples

### Example 1: Basic Usage of `TranslatorService`

```java
    public static void main(String[] args) throws Exception {
    // Create a TranslatorService instance with the API URL and API Key
    TranslatorService translator = Translators.create(API, KEY);

    // Define a list of commands for batch processing
    List<String> commands = Arrays.asList(
            "m:s;t:Hello;en;pt", // Synchronous translation of "Hello" from English (en) to Portuguese (pt)
            "t:World;pt",        // Translate "World" to Portuguese (pt)
            "t:Hello;en;pt",     // Translate "Hello" en (English) to Portuguese (pt)
            "m:as;t:Goodbye;en;es" // Asynchronous translation of "Goodbye" from English (en) to Spanish (es)
    );

    // Process the commands and print the results
    System.out.println(translator.processCommands(commands, false));

    // Perform a simple synchronous translation
    String result = translator.translate("Hello world", "pt");
    System.out.println("Translated text: " + result);

    // Perform a synchronous translation with a specified source language
    String resultWithSource = translator.translate("Hello world", "en", "es");
    System.out.println("Translated to Spanish: " + resultWithSource);

    // Perform an asynchronous translation
    CompletableFuture<String> translationFuture = translator.translateAsync("Hello world", "en", "fr");

    // Handle the asynchronous result
    translationFuture
            .thenAccept(text -> {
                System.out.println("Translation: " + text);
            })
            .exceptionally(ex -> {
                System.err.println("Translation failed: " + ex.getMessage());
                return null;
            });

    // Wait for the asynchronous operation to complete
    translationFuture.join();

    translator.close();
}
```

### Example 2: Advanced Usage with Custom Configuration

```java
    public static void main(String[] args) throws Exception {
    // Step 1: Build a custom configuration using LibreTranslateConfig.Builder
    LibreTranslateConfig config = LibreTranslateConfig.builder()
            .apiUrl(API)               // Set the API URL
            .apiKey(KEY)               // Set the API Key
            .rateLimitCooldown(1000)   // Rate limit cooldown (100 and 60000)
            .connectionTimeout(10000)  // Connection timeout in milliseconds
            .socketTimeout(15000)      // Socket timeout in milliseconds
            .maxRetries(5)             // Maximum number of retries for failed requests
            .build();

    // Step 2: Create a TranslatorService instance with the custom configuration
    TranslatorService customTranslator = Translators.create(config);

    // Step 3: Define a list of commands for batch processing
    List<String> commandsCustom = Arrays.asList(
            "m:s;t:Hello;en;pt", // Synchronous translation of "Hello" from English (en) to Portuguese (pt)
            "t:World;pt",        // Translate "World" to Portuguese (pt)
            "t:Hello;en;pt",     // Translate "Hello" en (English) to Portuguese (pt)
            "m:as;t:Goodbye;en;es" // Asynchronous translation of "Goodbye" from English (en) to Spanish (es)
    );

    // Step 4: Process the commands and print the results
    System.out.println("customTranslator: " + customTranslator.processCommands(commandsCustom, false));

    // Step 5: Perform a simple synchronous translation
    String resultCustom = customTranslator.translate("Hello world", "pt"); // Translate to Portuguese
    System.out.println("customTranslator - Translated text: " + resultCustom);

    // Step 6: Perform a synchronous translation with a specified source language
    String resultWithSourceCustom = customTranslator.translate("Hello world", "en", "es"); // Translate from English to Spanish
    System.out.println("customTranslator - Translated to Spanish: " + resultWithSourceCustom);

    // Step 7: Perform an asynchronous translation
    CompletableFuture<String> translationFutureCustom = customTranslator.translateAsync("Hello world", "en", "fr");

    // Handle the asynchronous result
    translationFutureCustom
            .thenAccept(text -> {
                System.out.println("customTranslator - Translation: " + text);
            })
            .exceptionally(ex -> {
                System.err.println("customTranslator - Translation failed: " + ex.getMessage());
                return null;
            });

    // Wait for the asynchronous operation to complete
    translationFutureCustom.join();

    customTranslator.close();
}
```

## Configuration Reference

| Parameter              | Description                                      | Default     | Range           |
|------------------------|--------------------------------------------------|-------------|-----------------|
| `apiUrl`               | LibreTranslate API endpoint URL                  | (Required)  | Valid URL       |
| `apiKey`               | Authentication key for the API                   | ""          | Any string      |
| `maxRequestsPerSecond` | Maximum requests per second                      | 10          | 1-1000          |
| `connectionTimeout`    | Connection establishment timeout (ms)            | 5000        | > 0             |
| `socketTimeout`        | Socket read timeout (ms)                         | 10000       | > 0             |
| `maxRetries`           | Maximum retry attempts for failed requests       | 3           | ≥ 0             |
| `rateLimitCooldown`    | Cooldown period after hitting rate limits (ms)   | 5000        | 100-60000       |
| `enableRetry`          | Whether to automatically retry failed requests   | true        | true/false      |

## Architectural Design

The library follows these architectural principles:

1. **Interface-Based Design** - All major components are defined by interfaces
2. **Factory Pattern** - Factories provide convenient instance creation 
3. **Builder Pattern** - For fluid configuration construction
4. **Strategy Pattern** - Interchangeable algorithm implementations
5. **Facade Pattern** - Simplified API via the `Translators` class

## Detailed Class Structure and Available Functions

### `TranslatorService` Interface

The core interface for translation operations:

```java
public interface TranslatorService extends AutoCloseable {
    // Core translation methods
    String translate(String text, String targetLanguage);
    String translate(String text, String sourceLanguage, String targetLanguage);
    
    // Asynchronous translation methods
    CompletableFuture<String> translateAsync(String text, String targetLanguage);
    CompletableFuture<String> translateAsync(String text, String sourceLanguage, String targetLanguage);
    
    // Command processing
    List<String> processCommands(List<String> commands, boolean log);
    
    // Metrics
    int getCacheHits();
    int getCacheMisses();
    void clearMetrics();
    
    // Resource management (from AutoCloseable)
    void close();
}
```

### `Translators` Utility Class

Factory facade for creating translator services:

```java
public final class Translators {
    // Get factory instance
    public static TranslatorServiceFactory factory();
    
    // Create translator service
    public static TranslatorService create(String apiUrl, String apiKey);
    public static TranslatorService create(LibreTranslateConfig config);
    
    // Test connection
    public static boolean testConnection(String apiUrl);
}
```

### `LibreTranslateClient` Implementation

Main implementation of the `TranslatorService` interface:

```java
public class LibreTranslateClient implements TranslatorService {
    // Constants
    public static final String DEFAULT_SOURCE_LANGUAGE = Language.AUTO.getCode();
    
    // Constructor
    public LibreTranslateClient(LibreTranslateConfig config);
    
    // Translation methods
    @Override
    public String translate(String text, String targetLanguage);
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage);
    
    // Asynchronous methods
    @Override
    public CompletableFuture<String> translateAsync(String text, String targetLanguage);
    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLanguage, String targetLanguage);
    
    // Command processing
    @Override
    public List<String> processCommands(List<String> commands, boolean log);
    
    // Metrics
    @Override
    public int getCacheHits();
    @Override
    public int getCacheMisses();
    @Override
    public void clearMetrics();
    
    // Resource management
    @Override
    public void close();
}
```

### `LibreTranslateConfig` Configuration

Builder-pattern based configuration class:

```java
public class LibreTranslateConfig {
    // Create configuration with builder
    public static Builder builder();
    
    // Properties
    public String getApiUrl();
    public String getApiKey();
    public int getConnectionTimeout();
    public int getSocketTimeout();
    public int getMaxRequestsPerSecond();
    public int getMaxRetries();
    public long getRateLimitCooldown();
    public boolean isEnableRetry();
    
    // Builder class
    public static class Builder {
        public Builder apiUrl(String apiUrl);
        public Builder apiKey(String apiKey);
        public Builder connectionTimeout(int connectionTimeout);
        public Builder socketTimeout(int socketTimeout);
        public Builder maxRequestsPerSecond(int maxRequestsPerSecond);
        public Builder maxRetries(int maxRetries);
        public Builder rateLimitCooldown(long rateLimitCooldown);
        public Builder enableRetry(boolean enableRetry);
        public LibreTranslateConfig build();
    }
}
```

### `Language` Enumeration

Enumeration of supported languages:

```java
public enum Language {
    // Language constants
    ENGLISH("en"), ALBANIAN("sq"), ARABIC("ar"), AZERBAIJANI("az"),
    RUSSIAN("ru"), CATALAN("ca"), CHINESE("zh"), CZECH("cs"),
    DANISH("da"), DUTCH("nl"), ESPERANTO("eo"), FINNISH("fi"),
    FRENCH("fr"), GERMAN("de"), GREEK("el"), HEBREW("he"),
    HINDI("hi"), HUNGARIAN("hu"), INDONESIAN("id"), IRISH("ga"),
    ITALIAN("it"), JAPANESE("ja"), KOREAN("ko"), PERSIAN("fa"),
    POLISH("pl"), PORTUGUESE("pt"), SLOVAK("sk"), SPANISH("es"),
    SWEDISH("sv"), TURKISH("tr"), UKRAINIAN("uk"), AUTO("auto");
    
    // Methods
    public String getCode();
    public static Language fromCode(String code);
    public static boolean isValidLanguageCode(String code);
    public static List<String> getAllLanguageCodes();
    public static boolean isSupportedLanguage(String code);
    public static boolean isSupportedLanguage(String... languages);
}
```

### `TranslationCacheService` Interface

Interface for translation caching:

```java
public interface TranslationCacheService extends AutoCloseable {
    // Cache operations
    String generateCacheKey(String text, String sourceLanguage, String targetLanguage);
    Optional<String> get(String cacheKey);
    void put(String cacheKey, String translatedText);
    void clear();
    
    // Metrics
    int getCacheHits();
    int getCacheMisses();
    void clearMetrics();
    
    // Resource management
    void close();
}
```

### `TranslationCache` Implementation

Implementation of the `TranslationCacheService` interface:

```java
public class TranslationCache implements TranslationCacheService {
    // Enable/disable detailed logging
    public static boolean DETAILED_LOGGING = false;
    
    // Constructors
    public TranslationCache();
    public TranslationCache(int maxCacheSize, long cacheExpirationMs);
    public TranslationCache(int maxCacheSize, long cacheExpirationMs, long cleanupIntervalMs);
    
    // Cache operations
    @Override
    public String generateCacheKey(String text, String sourceLanguage, String targetLanguage);
    @Override
    public Optional<String> get(String cacheKey);
    @Override
    public void put(String cacheKey, String translatedText);
    @Override
    public void clear();
    
    // Metrics
    @Override
    public int getCacheHits();
    @Override
    public int getCacheMisses();
    @Override
    public void clearMetrics();
    
    // Resource management
    @Override
    public void close();
}
```

### `CacheFactory` Utility Class

Factory for creating cache services:

```java
public final class CacheFactory {
    // Create cache services
    public static TranslationCacheService createDefault();
    public static TranslationCacheService create(int maxSize, long expirationMs);
    public static TranslationCacheService create(int maxSize, long expirationMs, long cleanupIntervalMs);
    public static TranslationCacheService createSmall();
    public static TranslationCacheService createLarge();
}
```

### `RateLimiterService` Interface

Interface for rate limiting:

```java
public interface RateLimiterService extends AutoCloseable {
    // Rate limiting operations
    boolean acquire() throws InterruptedException;
    void notifyRateLimitExceeded(int retryAfterSeconds);
    
    // Metrics and control
    RateLimitMetrics getMetrics();
    void resetMetrics();
    void reset();
    
    // Resource management
    void close();
}
```

### `RateLimiter` Implementation

Implementation of the `RateLimiterService` interface:

```java
public class RateLimiter implements RateLimiterService {
    // Enable/disable detailed logging
    public static boolean DETAILED_LOGGING = false;
    
    // Constructors
    public RateLimiter(int requestsPerSecond);
    public RateLimiter(int requestsPerSecond, double burstCapacityMultiplier, long maxWaitTimeNanos);
    
    // Rate limiting operations
    @Override
    public boolean acquire() throws InterruptedException;
    @Override
    public void notifyRateLimitExceeded(int retryAfterSeconds);
    
    // Metrics and control
    @Override
    public RateLimitMetrics getMetrics();
    @Override
    public void resetMetrics();
    @Override
    public void reset();
    
    // Resource management
    @Override
    public void close();
}
```

### `RateLimiterFactory` Utility Class

Factory for creating rate limiter services:

```java
public final class RateLimiterFactory {
    // Create rate limiter services
    public static RateLimiterService createDefault();
    public static RateLimiterService create(int requestsPerSecond);
    public static RateLimiterService create(int requestsPerSecond, double burstCapacityMultiplier, long maxWaitTimeMs);
    public static RateLimiterService createLowThroughput();
    public static RateLimiterService createHighThroughput();
}
```

### `LibreTranslateCommands` Command Processor

Handler for processing translation commands:

```java
public class LibreTranslateCommands {
    // Constructor
    public LibreTranslateCommands(TranslatorService translatorService);
    
    // Command processing
    public List<String> processCommands(List<String> commands, boolean log);
}
```

### `TranslationException` Error Handling

Exception for translation errors:

```java
public class TranslationException extends RuntimeException {
    // Constructors
    public TranslationException(String message);
    public TranslationException(String message, Throwable cause);
}
```

## Enabling Detailed Logging

To enable detailed logging for components:

```java
// Enable detailed cache logging
TranslationCache.DETAILED_LOGGING = true;

// Enable detailed rate limiter logging
RateLimiter.DETAILED_LOGGING = true;
```

When enabled:
- Cache will log detailed information about hits, misses, and cleanup operations
- Rate limiter will log detailed information about permits, throttling, and backoff behavior

## Best Practices

1. Always use try-with-resources for proper resource cleanup
2. Configure appropriate timeouts for your network environment
3. Adjust rate limiting based on the API service's limitations
4. Use asynchronous translations for non-blocking operations
5. Implement proper error handling for robust applications

## Installation

### Maven

```xml
<dependency>
    <groupId>com.vidigal.code</groupId>
    <artifactId>libretranslate-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.vidigal.code:libretranslate-java:1.0.0'
```

## License

This project is licensed under the **MIT License**.

See the [LICENSE](https://github.com/Vidigal-code/libretranslate-java/blob/main/License.mit) file for more details.

The LibreTranslate API is also licensed under the **MIT License**.
See the [LibreTranslate LICENSE](https://github.com/LibreTranslate/LibreTranslate/blob/main/LICENSE) for details.

## Credits

- **Creator**: Kauan Vidigal
- **Translation API**: [LibreTranslate](https://libretranslate.com/)
- **Contributions**: Contributions are welcome! Feel free to fork the repository, open an issue, or submit a pull request.

## Links
- [LibreTranslate API Documentation](https://libretranslate.com/docs)
- [LibreTranslate API GitHub](https://github.com/LibreTranslate/LibreTranslate)

