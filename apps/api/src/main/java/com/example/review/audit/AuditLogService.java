package com.example.review.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordDecision(long operatorId, long roundId, long manuscriptId, String decisionCode) {
        jdbcTemplate.update(
                """
                INSERT INTO AUDIT_LOG (
                  LOG_ID, OPERATOR_ID, OPERATION_TYPE, BIZ_TYPE, BIZ_ID, DETAIL_JSON, CREATED_AT
                ) VALUES (
                  NULL, ?, 'DECISION_RECORDED', 'DECISION', ?, ?, CURRENT_TIMESTAMP
                )
                """,
                operatorId,
                roundId,
                "{\"manuscriptId\":%d,\"decisionCode\":\"%s\"}".formatted(manuscriptId, decisionCode)
        );
    }
}
