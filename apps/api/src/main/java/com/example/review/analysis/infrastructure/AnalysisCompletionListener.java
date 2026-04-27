package com.example.review.analysis.infrastructure;

import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "review.analysis.broker-enabled", havingValue = "true")
public class AnalysisCompletionListener {
    private final AnalysisEventConsumer eventConsumer;

    public AnalysisCompletionListener(AnalysisEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @RabbitListener(queues = "${review.analysis.completion-queue:analysis.completed}")
    public void onCompleted(Map<String, Object> message) {
        eventConsumer.consume(message);
    }
}
