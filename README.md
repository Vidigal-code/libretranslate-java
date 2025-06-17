# LibreTranslate API Java Library

## Overview

This Java library provides a high-performance, robust interface for interacting with the [LibreTranslate translation API](https://libretranslate.com/). It supports synchronous and asynchronous translations, batch processing, language validation, caching with persistent storage, rate limiting, retry mechanisms with exponential backoff, a circuit breaker pattern, and detailed monitoring. Designed for scalability, reliability, and ease of use, it is ideal for applications requiring efficient text translations (JDK21).

The library is organized into modular packages, each handling specific functionalities:

- `com.java.vidigal.code`: Core plugin and main entry point.
- `com.java.vidigal.code.builder`: Fluent builder for creating translation requests.
- `com.java.vidigal.code.client`: Client interface and implementation for API communication.
- `com.java.vidigal.code.exception`: Custom exception classes for clear error handling.
- `com.java.vidigal.code.language`: Language management and validation.
- `com.java.vidigal.code.request`: Data models for translation requests and responses.
- `com.java.vidigal.code.utilities.cache`: Thread-safe caching with LRU eviction and persistent storage.
- `com.java.vidigal.code.utilities.config`: Flexible configuration management.
- `com.java.vidigal.code.utilities.ratelimit`: Token bucket-based rate limiting.
- `com.java.vidigal.code.test`: Comprehensive unit and integration tests.

## Key Features

- **Synchronous and Asynchronous Translations**: Supports blocking and non-blocking operations using `CompletableFuture` with virtual threads for superior concurrency.
- **Batch Processing**: Translates multiple texts in a single API call for maximum efficiency.
- **Language Validation**: Validates source and target languages against a dynamic registry that can be updated from the LibreTranslate API.
- **High-Performance Caching**: Thread-safe cache with configurable TTL, highly efficient **O(1) LRU eviction**, and optional persistent storage using JSON.
- **Rate Limiting**: A robust token bucket algorithm to enforce API request limits and prevent `429` errors.
- **Retry Mechanism**: Automatic retries for transient errors (HTTP 429, 500, 502, 503) with configurable exponential backoff.
- **Circuit Breaker**: Protects your application from repeated failures by temporarily halting requests to an unhealthy API.
- **Detailed Monitoring**: Tracks request success/failure counts, average latency, cache hits/misses, and error types for operational insights.
- **Graceful Resource Management**: Ensures proper cleanup of HTTP connections, thread pools, and cache cleanup threads via `shutdown()`.
- **Flexible Configuration**: Extensive options for timeouts, retries, caching, and rate limiting, configurable via a fluent builder or external properties.
- **Clear Exception Handling**: A modular exception hierarchy with `LibreTranslateException` and `LibreTranslateApiException` for client and API errors.
- **Comprehensive Documentation**: Javadoc for all public methods in `LibreTranslateConfig` to enhance IDE usability and developer experience.

## Components

### LibreTranslateConfig

- **Purpose**: The main entry point and facade for interacting with the LibreTranslate API.
- **Key Methods**:
  - `translateText(String text, String targetLang)`: Translates a single text synchronously.
  - `translateTextAsync(String text, String targetLang)`: Translates a single text asynchronously.
  - `translateBatch(List<String> texts, String targetLang, String sourceLang)`: Translates a batch of texts synchronously.
  - `translateBatchAsync(List<String> texts, String targetLang, String sourceLang)`: Translates a batch of texts asynchronously.
  - `shutdown()`: Closes all underlying resources (HTTP client, thread pools, cache cleanup threads).
  - **Javadoc**: Fully documented for developer convenience.

### LibreTranslateClient and LibreTranslateClientImpl

- **Purpose**: Manages all HTTP communication with the LibreTranslate API.
- **Features**:
  - Uses `java.net.http.HttpClient` with HTTP/2 support.
  - Orchestrates retry logic, rate limiting, the circuit breaker, and caching.
  - Provides detailed monitoring statistics (success/failure counts, latency, cache hit ratio).

### LanguageRegistry

- **Purpose**: Manages and validates language codes.
- **Features**:
  - Pre-populated with common languages from the `Language` enum.
  - Supports dynamic fetching of supported languages directly from the LibreTranslate API.
  - Allows adding/removing custom languages and resetting to defaults.

### TranslationRequestBuilder

- **Purpose**: Constructs `TranslationRequest` objects safely.
- **Features**:
  - Fluent API for setting texts and source/target languages.
  - Validates inputs to prevent invalid requests before they are sent.

### TranslationCache

- **Purpose**: Caches translation results to reduce API calls, latency, and costs.
- **Features**:
  - Thread-safe with configurable TTL and highly efficient **O(1) LRU eviction**, backed by a `LinkedHashMap`.
  - Optional persistent storage to disk using JSON serialization.
  - Tracks cache hits and misses for monitoring purposes.

### LibreTranslateConfig and LibreTranslateConfigBuilder

- **Purpose**: Manages all client configuration settings.
- **Features**:
  - Configurable API URL, auth key, timeouts, retries, rate limits, caching, and persistent storage.
  - Fluent builder pattern with strict input validation.
  - Supports loading configuration from system properties or environment variables.

### TokenBucketRateLimiter

- **Purpose**: Controls the rate of API requests to respect usage limits.
- **Features**:
  - A thread-safe, high-performance token bucket algorithm.
  - Configurable bucket capacity (requests per second) and cooldown period.

### Exception Handling

- **LibreTranslateException**: Base exception for client-side errors (e.g., configuration issues, interruptions).
- **LibreTranslateApiException**: Handles API-specific errors and includes the HTTP status code.
- **Package**: `com.java.vidigal.code.exception` for modularity.

## Setup

### Prerequisites

- **Java Version**: **Java 21 or later** (due to the use of virtual threads and other modern features).
- **Dependencies**:
  - Jackson (`com.fasterxml.jackson.core`) for JSON processing.
  - SLF4J (`org.slf4j`) for logging.
  - JUnit 5 and Mockito for testing.
- **Environment Variable**: Set `LIBRETRANSLATE_API_KEY` to run the integration tests.

### Maven Dependencies

Add the following to your `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.java.vidigal.code</groupId>
  <artifactId>libretranslate-java</artifactId>
  <version>1.0</version>

  <properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
        <configuration>
          <argLine>-XX:+EnableDynamicAgentLoading</argLine>
          <excludes>
          </excludes>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.12</version>
        <executions>
          <execution>
            <goals>
              <goal>prepare-agent</goal>
            </goals>
          </execution>
          <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
              <goal>report</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <profile>
      <id>integration-tests</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
              <excludes>
                <!-- No excludes for integration tests -->
              </excludes>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>

  <dependencies>
    <!-- Jackson for JSON -->
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.2</version>
    </dependency>
    <!-- SLF4J API -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.9</version>
    </dependency>
    <!-- SLF4J Simple Binding (for test execution) -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
      <scope>test</scope>
    </dependency>
    <!-- JUnit 5 -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
    <!-- Mockito for mocking -->
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.12.0</version>
      <scope>test</scope>
    </dependency>
    <!-- Mockito JUnit 5 extension -->
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-junit-jupiter</artifactId>
      <version>5.12.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

### LibreTranslate API Key

Obtain an API key from [LibreTranslate](https://www.libretranslate.com/). The library supports both free and pro API endpoints:

- **API**: `https://translate.fedilab.app/translate`
- **API KEY**: `unknown`

## Usage

### Basic Translation

```java
// Remember to call shutdown() when you are done.
LibreTranslatePlugin plugin = new LibreTranslatePlugin("https://translate.fedilab.app/translate", "unknown");
try {
    String result = plugin.translateText("Hello, world!", "fr");
    System.out.println(result); // Output: Bonjour, le monde !
} catch (LibreTranslateException | InterruptedException e) {
    e.printStackTrace();
} finally {
    plugin.shutdown();
}
```

### Asynchronous Translation

```java
LibreTranslatePlugin plugin = new LibreTranslatePlugin("https://translate.fedilab.app/translate", "unknown");
try {
    plugin.translateTextAsync("Hello, world!", "fr")
        .thenAccept(result -> System.out.println(result)) // Output: Bonjour, le monde !
        .join(); // Wait for the async task to complete
} finally {
    plugin.shutdown();
}
```

### Batch Translation

```java
LibreTranslatePlugin plugin = new LibreTranslatePlugin("https://translate.fedilab.app/translate", "unknown");
try {
    List<String> texts = List.of("Hello", "World");
    List<String> results = plugin.translateBatch(texts, "es", "en");
    System.out.println(results); // Output: [Hola, Mundo]
} catch (LibreTranslateException | InterruptedException e) {
    e.printStackTrace();
} finally {
    plugin.shutdown();
}
```

### Custom Configuration with Persistent Cache

```java
LibreTranslateConfig config = LibreTranslateConfig.builder()
    .apiUrl("https://translate.fedilab.app/translate")
    .authKey("unknown")
    .connectionTimeout(5000)
    .socketTimeout(10000)
    .maxRequestsPerSecond(10)
    .maxRetries(3)
    .enableRetry(true)
    .enableCache(true)
    .enablePersistentCache(true, "/path/to/your/cache.json")
    .cacheTtlMillis(3_600_000) // 1 hour
    .build();

LibreTranslateConfig plugin = new LibreTranslateConfig(config);
// ... use the plugin ...
plugin.shutdown();
```

### Configuration via Properties/Environment Variables

Load configuration automatically from system properties or environment variables.

```java
// Requires LIBRETRANSLATE_API_URL and LIBRETRANSLATE_AUTH_KEY to be set
LibreTranslateConfig config = LibreTranslateConfig.fromProperties();
LibreTranslateConfig plugin = new LibreTranslateConfig(config);
// ... use the plugin ...
plugin.shutdown();
```

Example environment variables:

```bash
export LIBRETRANSLATE_API_URL="https://translate.fedilab.app/translate"
export LIBRETRANSLATE_AUTH_KEY="unknown"
export LIBRETRANSLATE_CACHE_ENABLED="true"
export LIBRETRANSLATE_PERSISTENT_CACHE_ENABLED="true"
export LIBRETRANSLATE_PERSISTENT_CACHE_PATH="/var/data/LIBRETRANSLATE_cache.json"
```

System properties use the prefix `libretranslate.` (e.g., `libretranslate.api.url`). Environment variables use uppercase with underscores (e.g., `LIBRETRANSLATE_API_URL`).

## Testing

The library includes a comprehensive test suite under `src/test/java/com/java/vidigal/code/test`.

### Running Tests

- **Unit Tests** (fast, no network required):
  ```bash
  mvn test
  ```

- **Integration Tests** (requires a live API key):
  ```bash
  # Set your API key first
  export LIBRETRANSLATE_API_KEY="unknown"
  mvn test -Pintegration-tests
  ```

## Configuration Options

| Option                  | Description                                            | Default                |
|-------------------------|--------------------------------------------------------|------------------------|
| `apiUrl`                | LibreTranslate API endpoint URL                        | None (required)        |
| `apiKey`                | API authentication key                                 | None (required)        |
| `connectionTimeout`     | HTTP connection timeout (ms)                           | 5000                   |
| `socketTimeout`         | HTTP socket timeout (ms)                               | 10000                  |
| `maxRequestsPerSecond`  | Maximum API requests per second for rate limiting      | 10                     |
| `maxRetries`            | Maximum retry attempts for transient errors            | 3                      |
| `rateLimitCooldown`     | Rate limiter cooldown period (ms)                      | 5000                   |
| `enableRetry`           | Enable/disable retries for transient errors            | true                   |
| `enableCache`           | Enable/disable in-memory caching                       | false                  |
| `enablePersistentCache` | Enable/disable persistent cache storage                | false                  |
| `persistentCachePath`   | File path for persistent cache                         | None (if enabled)      |
| `cacheTtlMillis`        | Cache entry Time-To-Live (ms)                          | 3,600,000 (1 hour)     |
| `cleanupIntervalMillis` | Cache cleanup interval (ms)                            | 1,800,000 (30 mins)    |
| `trackInstances`        | Auto-shutdown via JVM hook                             | false                  |

**Warning**: Use `trackInstances` with caution in managed environments (e.g., Spring Boot, Jakarta EE), as it may conflict with the container's lifecycle. Prefer manual `plugin.shutdown()` in such cases.

## Monitoring and Statistics

Access detailed metrics for operational insight:

```java
LibreTranslateClientImpl.MonitoringStats stats = plugin.getMonitoringStats();
if (stats != null) {
    System.out.println("Success Count: " + stats.successCount());
    System.out.println("Failure Count: " + stats.failureCount());
    System.out.println("Average Latency: " + stats.averageLatencyMillis() + "ms");
    if (stats.cacheStats() != null) {
        System.out.println("Cache Hit Ratio: " + stats.cacheStats().cacheHitRatio());
    }
}
```

## Notes

- **Shutdown**: **Always call `plugin.shutdown()`** to release resources like thread pools and prevent memory leaks, especially in long-running applications.
- **Virtual Threads**: The use of Java 21 virtual threads makes this library highly scalable for I/O-bound tasks like API calls.
- **Persistent Cache**: JSON is used for human readability. For extreme performance scenarios, the cache implementation can be extended to support binary formats like Protocol Buffers.

## Contributing

Contributions are welcome! Please submit issues or pull requests to the project repository. Ensure your code follows the existing style, includes tests, and updates documentation as needed.

## License

This library is licensed under the MIT License.
See the [LICENSE](https://github.com/Vidigal-code/libretranslate-java/blob/main/License.mit) file for more details.

---

## Credits

- **Creator**: Kauan Vidigal
- **Translation API**: [LibreTranslate](https://www.libretranslate.com/)
- **Contributions**: Contributions are welcome! Feel free to fork the repository, open an issue, or submit a pull
  request for improvements or new features.

## Links

- [LibreTranslate API Documentation](https://libretranslate.com/docs/)
- [LibreTranslate API GitHub](https://github.com/LibreTranslate/LibreTranslate)
