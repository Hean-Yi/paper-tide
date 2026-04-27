package com.example.review.analysis.infrastructure;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalysisOutboxDispatcher {
    private final AnalysisOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String requestRoutingKey;

    public AnalysisOutboxDispatcher(
            AnalysisOutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${review.analysis.broker-exchange:review.analysis.exchange}") String exchange,
            @Value("${review.analysis.request-routing-key:analysis.requested}") String requestRoutingKey
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.requestRoutingKey = requestRoutingKey;
    }

    public int dispatchOnce() {
        int dispatched = 0;
        for (AnalysisOutboxRepository.AnalysisOutboxMessage message : outboxRepository.pendingRequested(50)) {
            rabbitTemplate.convertAndSend(exchange, requestRoutingKey, message.payload());
            outboxRepository.markPublished(message.messageKey());
            dispatched++;
        }
        return dispatched;
    }
}
