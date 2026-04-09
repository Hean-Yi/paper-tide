package com.example.review.review;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConflictCheckRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConflictCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextConflictId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_CONFLICT_CHECK_RECORD.NEXTVAL FROM DUAL", Long.class);
    }

    public void insertSystemDetected(
            long conflictId,
            long assignmentId,
            long manuscriptId,
            long reviewerId,
            String conflictType,
            String conflictDesc
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_CHECK_RECORD (
                  CONFLICT_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REVIEWER_ID, CONFLICT_TYPE, CONFLICT_DESC,
                  SOURCE, DECLARED_BY, DECLARED_AT, DETECTED_AT, CONFIRMED_BY_CHAIR
                ) VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM_DETECTED', NULL, NULL, CURRENT_TIMESTAMP, NULL)
                """,
                conflictId,
                assignmentId,
                manuscriptId,
                reviewerId,
                conflictType,
                conflictDesc
        );
    }

    public void insertSelfDeclared(
            long conflictId,
            long assignmentId,
            long manuscriptId,
            long reviewerId,
            String conflictType,
            String conflictDesc,
            long declaredBy,
            Timestamp declaredAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_CHECK_RECORD (
                  CONFLICT_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REVIEWER_ID, CONFLICT_TYPE, CONFLICT_DESC,
                  SOURCE, DECLARED_BY, DECLARED_AT, DETECTED_AT, CONFIRMED_BY_CHAIR
                ) VALUES (?, ?, ?, ?, ?, ?, 'SELF_DECLARED', ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                conflictId,
                assignmentId,
                manuscriptId,
                reviewerId,
                conflictType,
                conflictDesc,
                declaredBy,
                declaredAt
        );
    }

    public List<ConflictCheckResponse> listByRound(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT C.CONFLICT_ID, C.ASSIGNMENT_ID, C.REVIEWER_ID, C.CONFLICT_TYPE, C.CONFLICT_DESC, C.SOURCE
                FROM CONFLICT_CHECK_RECORD C
                JOIN REVIEW_ASSIGNMENT A ON A.ASSIGNMENT_ID = C.ASSIGNMENT_ID
                WHERE A.ROUND_ID = ?
                ORDER BY C.CONFLICT_ID
                """,
                (rs, rowNum) -> new ConflictCheckResponse(
                        rs.getLong("CONFLICT_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("CONFLICT_TYPE"),
                        rs.getString("CONFLICT_DESC"),
                        rs.getString("SOURCE")
                ),
                roundId
        );
    }
}
