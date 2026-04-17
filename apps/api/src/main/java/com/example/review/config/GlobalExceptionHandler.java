package com.example.review.config;

import com.example.review.agent.AgentServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAgentServiceException(
            AgentServiceException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "error", "Agent Service Error",
                        "message", ex.getMessage(),
                        "status", ex.getStatusCode(),
                        "path", request.getRequestURI(),
                        "timestamp", Instant.now().toString()
                ));
    }
}
