package com.example.review.analysis.infrastructure;

import org.springframework.stereotype.Component;

@Component
public class AnalysisEventConsumer {
    private final AnalysisInboxRepository inboxRepository;

    public AnalysisEventConsumer(AnalysisInboxRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    public boolean hasProcessed(String messageKey) {
        return inboxRepository.alreadyProcessed(messageKey);
    }
}
