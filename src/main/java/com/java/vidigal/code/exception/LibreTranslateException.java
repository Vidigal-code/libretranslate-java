package com.java.vidigal.code.exception;

/**
 * Base exception for errors in the LibreTranslate client, such as network issues or API failures.
 */
public class LibreTranslateException extends Exception {

    /**
     * Constructs a new LibreTranslateException with the specified message.
     *
     * @param message The detail message.
     */
    public LibreTranslateException(String message) {
        super(message);
    }

    /**
     * Constructs a new LibreTranslateException with the specified message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause of the exception.
     */
    public LibreTranslateException(String message, Throwable cause) {
        super(message, cause);
    }
}
