package com.example.review.analysis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

    public List<AnalysisOutboxMessage> pendingRequested(int limit) {
        return jdbcTemplate.query(
                """
                SELECT MESSAGE_KEY, MESSAGE_TYPE, MESSAGE_PAYLOAD
                  FROM (
                    SELECT MESSAGE_KEY, MESSAGE_TYPE, MESSAGE_PAYLOAD
                      FROM ANALYSIS_OUTBOX
                     WHERE MESSAGE_STATUS = 'PENDING'
                       AND MESSAGE_TYPE = 'analysis.requested'
                     ORDER BY CREATED_AT
                  )
                 WHERE ROWNUM <= ?
                """,
                (rs, rowNum) -> new AnalysisOutboxMessage(
                        rs.getString("MESSAGE_KEY"),
                        rs.getString("MESSAGE_TYPE"),
                        fromJson(rs.getString("MESSAGE_PAYLOAD"))
                ),
                limit
        );
    }

    public void markPublished(String messageKey) {
        jdbcTemplate.update(
                """
                UPDATE ANALYSIS_OUTBOX
                   SET MESSAGE_STATUS = 'PUBLISHED',
                       RETRY_COUNT = RETRY_COUNT + 1,
                       PUBLISHED_AT = CURRENT_TIMESTAMP
                 WHERE MESSAGE_KEY = ?
                """,
                messageKey
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize analysis outbox payload", ex);
        }
    }

    private Map<String, Object> fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse analysis outbox payload", ex);
        }
    }

    public record AnalysisOutboxMessage(String messageKey, String messageType, Map<String, Object> payload) {
    }
}
