package com.example.review.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorResponseFactory {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    public Map<String, Object> body(HttpStatus status, String message, HttpServletRequest request) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "code", status.name(),
                "error", status.getReasonPhrase(),
                "message", message == null ? status.getReasonPhrase() : message,
                "traceId", traceId(request)
        );
    }

    public String traceId(HttpServletRequest request) {
        Object attribute = request == null ? null : request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        String header = request == null ? null : request.getHeader(TRACE_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String mdcTraceId = MDC.get(TRACE_ID_ATTRIBUTE);
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            return mdcTraceId;
        }
        return UUID.randomUUID().toString();
    }
}
