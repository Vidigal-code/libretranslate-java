package com.java.vidigal.code.exception;

/**
 * Exception for failures in LibreTranslate API requests, including HTTP status code.
 */
public class LibreTranslateApiException extends LibreTranslateException {
    private final int statusCode;

    /**
     * Constructs a new LibreTranslateApiException with the specified message and status code.
     *
     * @param message    The detail message.
     * @param statusCode The HTTP status code of the failed request.
     */
    public LibreTranslateApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code associated with this exception.
     *
     * @return The HTTP status code.
     */
    public int getStatusCode() {
        return statusCode;
    }
}
