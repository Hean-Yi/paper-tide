package com.example.review.registration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmailVerificationTokenSweeper {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationTokenSweeper.class);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(7);

    private final RegistrationRepository repository;
    private final Clock clock;

    public EmailVerificationTokenSweeper(RegistrationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.registration.token-sweep-delay-ms:3600000}",
            initialDelayString = "${app.registration.token-sweep-initial-delay-ms:60000}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now(clock).minus(RETENTION_AFTER_EXPIRY);
        int deleted = repository.deleteExpiredTokens(cutoff);
        if (deleted > 0) {
            LOGGER.info("Swept {} email verification tokens expired before {}", deleted, cutoff);
        }
    }
}
