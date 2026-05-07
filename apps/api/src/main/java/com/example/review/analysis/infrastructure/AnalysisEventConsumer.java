package com.example.review.analysis.infrastructure;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisEventConsumer.class);

    private final AnalysisInboxRepository inboxRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;

    public AnalysisEventConsumer(
            AnalysisInboxRepository inboxRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisProjectionRepository projectionRepository
    ) {
        this.inboxRepository = inboxRepository;
        this.intentRepository = intentRepository;
        this.projectionRepository = projectionRepository;
    }

    public boolean hasProcessed(String messageKey) {
        return inboxRepository.alreadyProcessed(messageKey);
    }

    @SuppressWarnings("unchecked")
    public void consume(Map<String, Object> message) {
        String traceId = optionalString(message, "traceId");
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            String messageKey = requireString(message, "messageKey");
            if (inboxRepository.alreadyProcessed(messageKey)) {
                LOGGER.info("analysis.completed duplicate ignored messageKey={}", messageKey);
                return;
            }
            String eventType = requireString(message, "eventType");
            if (!"analysis.completed".equals(eventType)) {
                throw new IllegalArgumentException("Unsupported analysis event type: " + eventType);
            }
            long intentId = requireLong(message, "intentId");
            String analysisType = requireString(message, "analysisType");
            String businessStatus = requireString(message, "businessStatus");
            Map<String, Object> summaryProjection = requireMap(message, "summaryProjection");
            Map<String, Object> redactedResult = requireMap(message, "redactedResult");

            LOGGER.info(
                    "analysis.completed consuming messageKey={} intentId={} analysisType={} businessStatus={}",
                    messageKey,
                    intentId,
                    analysisType,
                    businessStatus
            );
            intentRepository.updateBusinessStatus(intentId, businessStatus);
            projectionRepository.saveProjection(intentId, analysisType, businessStatus, summaryProjection, redactedResult);
            inboxRepository.recordProcessed(messageKey, eventType, intentId, message);
        } finally {
            if (traceId != null) {
                MDC.remove("traceId");
            }
        }
    }

    private String optionalString(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return null;
    }

    private String requireString(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return stringValue;
    }

    private long requireLong(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(field + " is required");
    }

    private Map<String, Object> requireMap(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        throw new IllegalArgumentException(field + " is required");
    }
}
