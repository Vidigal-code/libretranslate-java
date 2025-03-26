package com.vidigal.code.libretranslate.exception;

/**
 * Base exception for all translation-related errors.
 */
public class TranslationException extends RuntimeException {

    /**
     * Creates a new TranslationException with the specified message.
     *
     * @param message The error message
     */
    public TranslationException(String message) {
        super(message);
    }

    /**
     * Creates a new TranslationException with the specified message and cause.
     *
     * @param message The error message
     * @param cause   The cause of the error
     */
    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
