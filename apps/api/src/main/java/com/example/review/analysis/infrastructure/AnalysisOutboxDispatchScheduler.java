package com.example.review.analysis.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "review.analysis.broker-enabled", havingValue = "true")
public class AnalysisOutboxDispatchScheduler {
    private final AnalysisOutboxDispatcher dispatcher;

    public AnalysisOutboxDispatchScheduler(AnalysisOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public int pollOnce() {
        return dispatcher.dispatchOnce();
    }

    @Scheduled(fixedDelayString = "${review.analysis.outbox-dispatch-delay-ms:5000}")
    public void dispatchScheduled() {
        pollOnce();
    }
}
