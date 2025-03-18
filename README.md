# LibreTranslate Java Client

Java client for the LibreTranslate API featuring advanced capabilities like rate limiting, caching, and comprehensive monitoring.

## Key Features

- **Synchronous & Asynchronous Translation**
- **Smart Rate Limiting** (configurable requests per second)
- **Translation Cache** with expiration and size limits
- **Metrics Monitoring** (cache hits/misses, average response time)
- **AutoCloseable Support** for automatic resource management
- **Batch Command Processing** with multiple operation modes

## Installation

### Maven

```xml
<dependency>
    <groupId>com.vidigal.code</groupId>
    <artifactId>libretranslate-java-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.vidigal.code:libretranslate-java-client:1.0.0'
```

## Configuration

Create a configuration object:

```java

LibreTranslateConfig config = new LibreTranslateConfig.Builder()
        .apiUrl("https://libretranslate.com")
        .apiKey("your-api-key")
        .maxRequestsPerSecond(10)
        .connectionTimeout(5000)
        .socketTimeout(10000)
        .build();

//Example  simple 2
LibreTranslateConfig config = TranslatorService.createConfig(API, KEY);

```

## Basic Usage

### Simple Translation

```java
try (LibreTranslateClient client = new LibreTranslateClient(config)) {
    String translated = client.translate("Hello", "en", "es");
    System.out.println(translated); // Hola
}
```

### Async Translation

```java
try (LibreTranslateClient client = new LibreTranslateClient(config)) {
    CompletableFuture<String> future = client.translateAsync("Goodbye", "en", "fr");
    future.thenAccept(translation -> {
        System.out.println("Translation: " + translation); // Au revoir
    }).join();
}
```

## Advanced Features

### Batch Processing

```java
List<String> commands = List.of(
    "m:s;t:Hello;en;es",    // Sync mode
    "m:as;t:World;en;pt",   // Async mode
    "t:Thank you;en;ja"     // Default mode (sync)
);

List<String> results = client.processCommands(commands, true);
results.forEach(System.out::println);
```

### Metrics Monitoring

```java
// Enable monitoring logs
LibreTranslateClient.CacheLog = true;

// Access metrics programmatically
System.out.println("Cache Hits: " + client.getCacheHits());
System.out.println("Avg Response: " + client.getAverageResponseTime() + "ms");
```

## Resource Management

The client implements `AutoCloseable` and should be used with try-with-resources:

```java
try (LibreTranslateClient client = new LibreTranslateClient(config)) {
    // Translation operations...
} // Automatically closes resources
```

## Error Handling

All methods throw `TranslationException` for error conditions:

```java
try {
    String result = client.translate(null, "en", "es");
} catch (TranslationException e) {
    System.err.println("Error: " + e.getMessage());
}
```

# LibreTranslate Client Configuration

| Parameter              | Description                                                  | Valid Range      | Default Value |
|------------------------|--------------------------------------------------------------|------------------|---------------|
| `maxRequestsPerSecond` | Maximum allowed requests per second                          | 1-1000           | 10            |
| `connectionTimeout`    | Connection establishment timeout (milliseconds)              | > 0 ms           | 5000          |
| `socketTimeout`        | Response read timeout (milliseconds)                         | > 0 ms           | 10000         |
| `rateLimitCooldown`    | Cooldown period after hitting API rate limits (milliseconds) | 100-60000 ms     | 5000          |

**Key Improvements:**
1. Unified table format with consistent naming
2. Added parameter types in descriptions
3. Clear validation ranges
4. Standardized time units
5. Fixed typo in `rateLimitCooldown` parameter name
6. Added proper millisecond abbreviations

For a complete configuration reference, see the `LibreTranslateConfig` class documentation.

## Best Practices

1. Always use try-with-resources for client instances.
2. Leverage cache for frequently translated texts.
3. Monitor metrics to optimize configuration.
4. Handle translation-specific exceptions.
5. Use async operations for high-volume scenarios.

## Technical Limitations

- Maximum cache size: 1000 entries
- Rate limiting precision: ±10ms
- Minimum request interval: 10ms

For support and issues: [GitHub Issues](https://github.com/Vidigal-code/libretranslate-java)

---

# License

This project is licensed under the **MIT License**.

See the [LICENSE](https://github.com/Vidigal-code/libretranslate-java/blob/main/License.mit) file for more details.


# License - API

This project is licensed api under the **MIT License**.

See the [LICENSE](https://github.com/LibreTranslate/LibreTranslate/blob/main/LICENSE) file for more details.


---




## Credits

- **Creator**: Kauan Vidigal
- **Translation API**: [LibreTranslate](https://libretranslate.com/)
- **Contributions**: Contributions are welcome! Feel free to fork the repository, open an issue, or submit a pull request for improvements or new features.

## Links
- [LibreTranslate API Documentation](https://libretranslate.com/docs)
- [LibreTranslate API GitHub](https://github.com/LibreTranslate/LibreTranslate)

---

## Example Usage

### Example 1: Basic Usage of `TranslatorService`

```java
public static void main(String[] args) {

    // Create a TranslatorService instance with the API URL and API Key
    TranslatorService translator = TranslatorService.create(API, KEY);

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
public static void main(String[] args) {

    // Step 1: Build a custom configuration using LibreTranslateConfig.Builder
    LibreTranslateConfig config = new LibreTranslateConfig.Builder()
            .apiUrl(API)               // Set the API URL
            .apiKey(KEY)               // Set the API Key
            .rateLimitCooldown(1000)   // Rate limit cooldown (100 and 60000)
            .connectionTimeout(10000)  // Connection timeout in milliseconds
            .socketTimeout(15000)      // Socket timeout in milliseconds
            .maxRetries(5)             // Maximum number of retries for failed requests
            .build();

    // Step 2: Create a TranslatorService instance with the custom configuration
    TranslatorService customTranslator = TranslatorService.create(config);

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

---

### Summary of Key Changes:
1. Added more comprehensive examples to demonstrate different use cases.
2. Updated all method references and variable names for clarity.
3. Provided additional guidance on custom configuration and asynchronous operations.
4. Organized the examples into two distinct sections for Basic and Advanced usage.

This should give your users clear guidance on how to use the **LibreTranslate Java Client** effectively, both with basic operations and more advanced features.

---

Feel free to modify and enhance this project to suit your needs!

