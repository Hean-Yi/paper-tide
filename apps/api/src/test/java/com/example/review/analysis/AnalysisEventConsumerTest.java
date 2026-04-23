package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.infrastructure.AnalysisEventConsumer;
import com.example.review.analysis.infrastructure.AnalysisInboxRepository;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisEventConsumerTest {
    @Test
    void consumeCompletedEventMarksIntentAvailableAndCreatesProjection() {
        CapturingInboxRepository inboxRepository = new CapturingInboxRepository();
        CapturingIntentRepository intentRepository = new CapturingIntentRepository();
        CapturingProjectionRepository projectionRepository = new CapturingProjectionRepository();
        AnalysisEventConsumer consumer = new AnalysisEventConsumer(inboxRepository, intentRepository, projectionRepository);

        consumer.consume(Map.of(
                "messageKey", "completed:key-1",
                "eventType", "analysis.completed",
                "intentId", 101L,
                "jobId", "job-1",
                "analysisType", "REVIEWER_ASSIST",
                "businessStatus", "AVAILABLE",
                "summaryProjection", Map.of("businessStatus", "AVAILABLE", "summary", "Checklist ready."),
                "redactedResult", Map.of("checklist", List.of("Verify claims"))
        ));

        assertThat(intentRepository.updatedIntentId).isEqualTo(101L);
        assertThat(intentRepository.updatedBusinessStatus).isEqualTo("AVAILABLE");
        assertThat(projectionRepository.savedIntentId).isEqualTo(101L);
        assertThat(projectionRepository.savedAnalysisType).isEqualTo("REVIEWER_ASSIST");
        assertThat(projectionRepository.savedBusinessStatus).isEqualTo("AVAILABLE");
        assertThat(inboxRepository.processedMessageKey).isEqualTo("completed:key-1");
    }

    private static final class CapturingInboxRepository extends AnalysisInboxRepository {
        private String processedMessageKey;

        CapturingInboxRepository() {
            super(null, null);
        }

        @Override
        public boolean alreadyProcessed(String messageKey) {
            return false;
        }

        @Override
        public void recordProcessed(String messageKey, String messageType, Long intentId, Map<String, Object> payload) {
            this.processedMessageKey = messageKey;
        }
    }

    private static final class CapturingIntentRepository extends AnalysisIntentRepository {
        private long updatedIntentId;
        private String updatedBusinessStatus;

        CapturingIntentRepository() {
            super(null);
        }

        @Override
        public void updateBusinessStatus(long intentId, String businessStatus) {
            this.updatedIntentId = intentId;
            this.updatedBusinessStatus = businessStatus;
        }
    }

    private static final class CapturingProjectionRepository extends AnalysisProjectionRepository {
        private long savedIntentId;
        private String savedAnalysisType;
        private String savedBusinessStatus;

        CapturingProjectionRepository() {
            super(null, null);
        }

        @Override
        public void saveProjection(
                long intentId,
                String analysisType,
                String businessStatus,
                Map<String, Object> summaryProjection,
                Map<String, Object> redactedResult
        ) {
            this.savedIntentId = intentId;
            this.savedAnalysisType = analysisType;
            this.savedBusinessStatus = businessStatus;
        }
    }
}
