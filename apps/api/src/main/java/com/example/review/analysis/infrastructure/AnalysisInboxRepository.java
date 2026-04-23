package com.example.review.analysis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisInboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisInboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean alreadyProcessed(String messageKey) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ANALYSIS_INBOX
                WHERE MESSAGE_KEY = ?
                  AND PROCESS_STATUS = 'PROCESSED'
                """,
                Integer.class,
                messageKey
        );
        return count != null && count > 0;
    }

    public void recordProcessed(String messageKey, String messageType, Long intentId, Map<String, Object> payload) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ANALYSIS_INBOX (
                      INBOX_ID, INTENT_ID, MESSAGE_TYPE, MESSAGE_KEY, MESSAGE_PAYLOAD, PROCESS_STATUS, RECEIVED_AT, PROCESSED_AT
                    ) VALUES (SEQ_ANALYSIS_INBOX.NEXTVAL, ?, ?, ?, ?, 'PROCESSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    intentId,
                    messageType,
                    messageKey,
                    objectMapper.writeValueAsString(payload)
            );
        } catch (DuplicateKeyException ignored) {
            // Idempotent re-delivery is allowed.
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize analysis inbox payload", ex);
        }
    }
}
