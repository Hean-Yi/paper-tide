package com.example.review.registration;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryVerificationEmailGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryVerificationEmailGateway.class);
    private final List<VerificationEmailMessage> messages = new ArrayList<>();
    private final String verificationLinkBase;

    public InMemoryVerificationEmailGateway(
            @Value("${app.registration.verification-link-base:http://localhost:5173/verify-email}")
            String verificationLinkBase
    ) {
        this.verificationLinkBase = verificationLinkBase;
    }

    public synchronized void sendVerificationEmail(VerificationEmailMessage message) {
        messages.add(message);
        LOGGER.info(
                "Queued verification email user={} application={} email={} link={}?token={} expires={}",
                message.userId(),
                message.applicationId(),
                message.email(),
                verificationLinkBase,
                message.rawToken(),
                message.expiresAt()
        );
    }

    synchronized List<VerificationEmailMessage> messages() {
        return List.copyOf(messages);
    }
}
