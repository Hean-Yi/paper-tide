package com.example.review.registration;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryVerificationEmailGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryVerificationEmailGateway.class);
    private final List<VerificationEmailMessage> messages = new ArrayList<>();

    public synchronized void sendVerificationEmail(VerificationEmailMessage message) {
        messages.add(message);
        LOGGER.info("Queued verification email for user {} and application {}", message.userId(), message.applicationId());
    }

    synchronized List<VerificationEmailMessage> messages() {
        return List.copyOf(messages);
    }
}
