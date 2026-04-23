package com.example.review.analysis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisOutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueueRequested(long intentId, String idempotencyKey, Map<String, Object> payload) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ANALYSIS_OUTBOX (
                      OUTBOX_ID, INTENT_ID, MESSAGE_TYPE, MESSAGE_KEY, MESSAGE_PAYLOAD, MESSAGE_STATUS, RETRY_COUNT, CREATED_AT, PUBLISHED_AT
                    ) VALUES (SEQ_ANALYSIS_OUTBOX.NEXTVAL, ?, 'analysis.requested', ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, NULL)
                    """,
                    intentId,
                    "analysis.requested:" + idempotencyKey,
                    toJson(payload)
            );
        } catch (DuplicateKeyException ignored) {
            // Idempotent request replay should not duplicate transport work.
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize analysis outbox payload", ex);
        }
    }
}
