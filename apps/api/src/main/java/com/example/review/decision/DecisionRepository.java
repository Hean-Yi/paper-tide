package com.example.review.decision;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionRepository {
    private final JdbcTemplate jdbcTemplate;

    public DecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextDecisionId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_DECISION_RECORD.NEXTVAL FROM DUAL", Long.class);
    }

    public void insert(
            long decisionId,
            long manuscriptId,
            long roundId,
            long versionId,
            String decisionCode,
            String decisionReason,
            long decidedBy,
            Timestamp decidedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO DECISION_RECORD (
                  DECISION_ID, MANUSCRIPT_ID, ROUND_ID, VERSION_ID, DECISION_CODE, DECISION_REASON, DECIDED_BY, DECIDED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                decisionId,
                manuscriptId,
                roundId,
                versionId,
                decisionCode,
                decisionReason,
                decidedBy,
                decidedAt
        );
    }

    public Optional<DecisionRow> findByRoundId(long roundId) {
        List<DecisionRow> rows = jdbcTemplate.query(
                """
                SELECT DECISION_ID, MANUSCRIPT_ID, ROUND_ID, VERSION_ID, DECISION_CODE, DECISION_REASON, DECIDED_BY, DECIDED_AT
                FROM DECISION_RECORD
                WHERE ROUND_ID = ?
                """,
                (rs, rowNum) -> new DecisionRow(
                        rs.getLong("DECISION_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("DECISION_CODE"),
                        rs.getString("DECISION_REASON"),
                        rs.getLong("DECIDED_BY"),
                        rs.getTimestamp("DECIDED_AT")
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}

record DecisionRow(
        long decisionId,
        long manuscriptId,
        long roundId,
        long versionId,
        String decisionCode,
        String decisionReason,
        long decidedBy,
        Timestamp decidedAt
) {
}
