package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.infrastructure.AnalysisCompletionListener;
import com.example.review.analysis.infrastructure.AnalysisEventConsumer;
import com.example.review.analysis.infrastructure.AnalysisOutboxDispatcher;
import com.example.review.analysis.infrastructure.AnalysisOutboxDispatchScheduler;
import com.example.review.analysis.infrastructure.AnalysisOutboxRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxRepository.AnalysisOutboxMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AnalysisBrokerDispatchTest {
    @Test
    void dispatchOncePublishesPendingRequestedMessagesAndMarksThemPublished() {
        CapturingOutboxRepository repository = new CapturingOutboxRepository();
        CapturingRabbitTemplate rabbitTemplate = new CapturingRabbitTemplate();
        AnalysisOutboxDispatcher dispatcher = new AnalysisOutboxDispatcher(
                repository,
                rabbitTemplate,
                "review.analysis.exchange",
                "analysis.requested"
        );

        int dispatched = dispatcher.dispatchOnce();

        assertThat(dispatched).isEqualTo(1);
        assertThat(rabbitTemplate.exchange).isEqualTo("review.analysis.exchange");
        assertThat(rabbitTemplate.routingKey).isEqualTo("analysis.requested");
        assertThat(rabbitTemplate.payload).isEqualTo(Map.of("analysisType", "REVIEWER_ASSIST"));
        assertThat(repository.publishedKeys).containsExactly("analysis.requested:key-1");
    }

    @Test
    void completionListenerDelegatesBrokerPayloadToEventConsumer() {
        CapturingEventConsumer consumer = new CapturingEventConsumer();
        AnalysisCompletionListener listener = new AnalysisCompletionListener(consumer);
        Map<String, Object> payload = Map.of(
                "messageKey", "analysis.completed:key-1",
                "eventType", "analysis.completed",
                "intentId", 101L,
                "analysisType", "REVIEWER_ASSIST",
                "businessStatus", "AVAILABLE",
                "summaryProjection", Map.of("summary", "Ready"),
                "redactedResult", Map.of("taskType", "REVIEW_ASSIST_ANALYSIS")
        );

        listener.onCompleted(payload);

        assertThat(consumer.message).isEqualTo(payload);
    }

    @Test
    void schedulerDelegatesToDispatcherForDeterministicPollingTests() {
        CapturingOutboxRepository repository = new CapturingOutboxRepository();
        CapturingRabbitTemplate rabbitTemplate = new CapturingRabbitTemplate();
        AnalysisOutboxDispatcher dispatcher = new AnalysisOutboxDispatcher(
                repository,
                rabbitTemplate,
                "review.analysis.exchange",
                "analysis.requested"
        );
        AnalysisOutboxDispatchScheduler scheduler = new AnalysisOutboxDispatchScheduler(dispatcher);

        int dispatched = scheduler.pollOnce();

        assertThat(dispatched).isEqualTo(1);
        assertThat(repository.publishedKeys).containsExactly("analysis.requested:key-1");
    }

    private static final class CapturingOutboxRepository extends AnalysisOutboxRepository {
        private final List<String> publishedKeys = new ArrayList<>();

        CapturingOutboxRepository() {
            super(null, null);
        }

        @Override
        public List<AnalysisOutboxMessage> pendingRequested(int limit) {
            return List.of(new AnalysisOutboxMessage(
                    "analysis.requested:key-1",
                    "analysis.requested",
                    Map.of("analysisType", "REVIEWER_ASSIST")
            ));
        }

        @Override
        public void markPublished(String messageKey) {
            publishedKeys.add(messageKey);
        }
    }

    private static final class CapturingRabbitTemplate extends RabbitTemplate {
        private String exchange;
        private String routingKey;
        private Object payload;

        @Override
        public void convertAndSend(String exchange, String routingKey, Object object) {
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.payload = object;
        }
    }

    private static final class CapturingEventConsumer extends AnalysisEventConsumer {
        private Map<String, Object> message;

        CapturingEventConsumer() {
            super(null, null, null);
        }

        @Override
        public void consume(Map<String, Object> message) {
            this.message = message;
        }
    }
}
