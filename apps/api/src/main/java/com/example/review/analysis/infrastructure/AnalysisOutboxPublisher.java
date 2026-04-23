package com.example.review.analysis.infrastructure;

import com.example.review.analysis.domain.AnalysisType;
import java.util.LinkedHashMap;
import java.util.Map;
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
        outboxRepository.enqueueRequested(intentId, idempotencyKey, envelope);
    }
}
