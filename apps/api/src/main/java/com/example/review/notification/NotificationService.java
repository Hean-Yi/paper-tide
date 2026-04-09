package com.example.review.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void notifyDecision(long receiverId, long manuscriptId, String decisionCode) {
        jdbcTemplate.update(
                """
                INSERT INTO SYS_NOTIFICATION (
                  NOTIFICATION_ID, RECEIVER_ID, BIZ_TYPE, BIZ_ID, TITLE, CONTENT, IS_READ, CREATED_AT
                ) VALUES (
                  NULL, ?, 'DECISION', ?, ?, ?, 0, CURRENT_TIMESTAMP
                )
                """,
                receiverId,
                manuscriptId,
                "Decision Recorded",
                "Decision " + decisionCode + " has been recorded for manuscript " + manuscriptId
        );
    }

    public void notifyReviewSubmitted(long receiverId, long manuscriptId, long assignmentId) {
        jdbcTemplate.update(
                """
                INSERT INTO SYS_NOTIFICATION (
                  NOTIFICATION_ID, RECEIVER_ID, BIZ_TYPE, BIZ_ID, TITLE, CONTENT, IS_READ, CREATED_AT
                ) VALUES (
                  NULL, ?, 'REVIEW_REPORT', ?, ?, ?, 0, CURRENT_TIMESTAMP
                )
                """,
                receiverId,
                assignmentId,
                "Review Submitted",
                "A review was submitted for manuscript " + manuscriptId
        );
    }
}
