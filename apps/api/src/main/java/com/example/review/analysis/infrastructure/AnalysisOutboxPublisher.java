package com.example.review.analysis.infrastructure;

import com.example.review.analysis.domain.AnalysisType;
import com.example.review.config.ApiErrorResponseFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AnalysisOutboxPublisher {
    private final AnalysisOutboxRepository outboxRepository;

    public AnalysisOutboxPublisher(AnalysisOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void publishRequested(long intentId, AnalysisType analysisType, String idempotencyKey, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("idempotencyKey", idempotencyKey);
        envelope.put("analysisType", analysisType.name());
        envelope.put("intentReference", Long.toString(intentId));
        envelope.put("requestPayload", payload);
        String traceId = MDC.get(ApiErrorResponseFactory.TRACE_ID_ATTRIBUTE);
        if (traceId != null && !traceId.isBlank()) {
            envelope.put(ApiErrorResponseFactory.TRACE_ID_ATTRIBUTE, traceId);
        }
        outboxRepository.enqueueRequested(intentId, idempotencyKey, envelope);
    }
}
