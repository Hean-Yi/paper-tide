package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.AnalysisOutboxRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisOutboxPublisherTest {
    @Test
    void publishRequestedWrapsBusinessPayloadInAnalysisCommandEnvelope() {
        CapturingOutboxRepository repository = new CapturingOutboxRepository();
        AnalysisOutboxPublisher publisher = new AnalysisOutboxPublisher(repository);

        publisher.publishRequested(
                12L,
                AnalysisType.REVIEWER_ASSIST,
                "idem-1",
                Map.<String, Object>of("title", "Boundary Paper")
        );

        assertThat(repository.intentId).isEqualTo(12L);
        assertThat(repository.idempotencyKey).isEqualTo("idem-1");
        assertThat(repository.payload).containsEntry("idempotencyKey", "idem-1");
        assertThat(repository.payload).containsEntry("analysisType", "REVIEWER_ASSIST");
        assertThat(repository.payload).containsEntry("intentReference", "12");
        assertThat(repository.payload).containsEntry("requestPayload", Map.of("title", "Boundary Paper"));
    }

    private static class CapturingOutboxRepository extends AnalysisOutboxRepository {
        private long intentId;
        private String idempotencyKey;
        private Map<String, Object> payload;

        CapturingOutboxRepository() {
            super(null, null);
        }

        @Override
        public void enqueueRequested(long intentId, String idempotencyKey, Map<String, Object> payload) {
            this.intentId = intentId;
            this.idempotencyKey = idempotencyKey;
            this.payload = payload;
        }
    }
}
