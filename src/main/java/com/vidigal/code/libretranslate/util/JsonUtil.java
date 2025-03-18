package com.vidigal.code.libretranslate.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Utility class providing JSON parsing and serialization operations with enhanced validation.
 */
public class JsonUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ERROR_PARSING_JSON = "Failed to parse JSON: {}";
    private static final String ERROR_CONVERSION_TO_JSON = "Failed to convert object to JSON: {}";
    private static final String ERROR_MISSING_FIELD = "Missing required field: {}";
    private static final String ERROR_INVALID_TYPE = "Invalid type for field '{}'. Expected {}, but got {}";

    /**
     * Parses a JSON string into a Map of key-value pairs with validation.
     *
     * @param json The JSON string to parse
     * @return A Map containing the parsed key-value pairs. Returns an empty map if parsing fails.
     */
    public static Map<String, Object> parseJson(String json) {
        if (isEmpty(json)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (IOException e) {
            logError(ERROR_PARSING_JSON, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Converts a Java object to its JSON string representation.
     *
     * @param object The object to convert
     * @return The JSON string representation of the object. Returns null if conversion fails.
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logError(ERROR_CONVERSION_TO_JSON, e.getMessage());
            return null;
        }
    }

    /**
     * Converts a JSON string into a specified Java object type with validation.
     *
     * @param json  The JSON string to parse.
     * @param clazz The class of the target Java object type.
     * @param <T>   The type of the target Java object.
     * @return An instance of the specified class populated with data from the JSON string.
     * Returns null if parsing fails or if the input JSON is empty.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (isEmpty(json)) {
            logError(ERROR_PARSING_JSON, "Empty or null JSON input");
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            logError(ERROR_PARSING_JSON, e.getMessage());
            return null;
        }
    }

    /**
     * Validates that a JSON string contains all required fields and that their types match expectations.
     *
     * @param json    The JSON string to validate.
     * @param requiredFields A map of required field names and their expected types (e.g., "field1": "String").
     * @return true if the JSON passes validation, false otherwise.
     */
    public static boolean validateJson(String json, Map<String, Class<?>> requiredFields) {
        if (isEmpty(json)) {
            logError(ERROR_PARSING_JSON, "Empty or null JSON input");
            return false;
        }

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(json);

            for (Map.Entry<String, Class<?>> entry : requiredFields.entrySet()) {
                String fieldName = entry.getKey();
                Class<?> expectedType = entry.getValue();

                if (!rootNode.has(fieldName)) {
                    logError(ERROR_MISSING_FIELD, fieldName);
                    return false;
                }

                JsonNode fieldValue = rootNode.get(fieldName);
                if (!isValidType(fieldValue, expectedType)) {
                    logError(ERROR_INVALID_TYPE, fieldName);
                    return false;
                }
            }

            return true;
        } catch (IOException e) {
            logError(ERROR_PARSING_JSON, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a JsonNode matches the expected type.
     *
     * @param node         The JsonNode to check.
     * @param expectedType The expected type (e.g., String.class, Integer.class).
     * @return true if the node matches the expected type, false otherwise.
     */
    private static boolean isValidType(JsonNode node, Class<?> expectedType) {
        String typeName = expectedType.getSimpleName();

        switch (typeName) {
            case "String":
                return node.isTextual();
            case "Integer":
            case "int":
                return node.isInt();
            case "Long":
            case "long":
                return node.isLong();
            case "Double":
            case "double":
                return node.isDouble() || node.isFloat();
            case "Boolean":
            case "boolean":
                return node.isBoolean();
            case "Object":
                return node.isObject();
            case "Map":
                return node.isObject();
            default:
                return false;
        }
    }


    /**
     * Logs an error message with detailed exception information.
     *
     * @param message Error message template
     * @param details Additional details from the exception
     */
    private static void logError(String message, String details) {
        LOGGER.error(message, details);
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     *
     * @param str The string to check
     * @return true if the string is null, empty, or whitespace-only; false otherwise
     */
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}