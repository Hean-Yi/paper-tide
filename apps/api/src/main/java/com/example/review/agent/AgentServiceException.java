package com.example.review.agent;

/**
 * Exception thrown when communication with the Agent service fails.
 * Includes the HTTP status code from the Agent service.
 */
public class AgentServiceException extends RuntimeException {
    private final int statusCode;

    public AgentServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AgentServiceException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
