package com.example.review.analysis.infrastructure;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventConsumer {
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
        String messageKey = requireString(message, "messageKey");
        if (inboxRepository.alreadyProcessed(messageKey)) {
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

        intentRepository.updateBusinessStatus(intentId, businessStatus);
        projectionRepository.saveProjection(intentId, analysisType, businessStatus, summaryProjection, redactedResult);
        inboxRepository.recordProcessed(messageKey, eventType, intentId, message);
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
