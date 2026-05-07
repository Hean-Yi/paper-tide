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

    public Optional<DecisionPackageRow> findAuthorDecisionPackage(long authorId, long manuscriptId) {
        List<DecisionPackageRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       D.ROUND_ID,
                       D.VERSION_ID,
                       V.VERSION_NO,
                       V.TITLE,
                       D.DECISION_CODE,
                       D.DECISION_REASON,
                       D.DECIDED_AT
                FROM MANUSCRIPT M
                JOIN DECISION_RECORD D ON D.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = D.VERSION_ID
                WHERE M.MANUSCRIPT_ID = ?
                  AND M.SUBMITTER_ID = ?
                ORDER BY D.DECIDED_AT DESC, D.DECISION_ID DESC
                FETCH FIRST 1 ROW ONLY
                """,
                (rs, rowNum) -> new DecisionPackageRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        rs.getString("TITLE"),
                        rs.getString("DECISION_CODE"),
                        rs.getString("DECISION_REASON"),
                        rs.getTimestamp("DECIDED_AT")
                ),
                manuscriptId,
                authorId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<DecisionPackageReviewRow> findAuthorVisibleReviews(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT REVIEW_ID,
                       REVIEWER_ID,
                       OVERALL_SCORE,
                       CONFIDENCE_LEVEL,
                       STRENGTHS,
                       WEAKNESSES,
                       COMMENTS_TO_AUTHOR,
                       COMMENTS_TO_CHAIR,
                       RECOMMENDATION
                FROM REVIEW_REPORT
                WHERE ROUND_ID = ?
                ORDER BY REVIEW_ID
                """,
                (rs, rowNum) -> new DecisionPackageReviewRow(
                        rs.getLong("REVIEW_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("OVERALL_SCORE"),
                        rs.getString("CONFIDENCE_LEVEL"),
                        rs.getString("STRENGTHS"),
                        rs.getString("WEAKNESSES"),
                        rs.getString("COMMENTS_TO_AUTHOR"),
                        rs.getString("COMMENTS_TO_CHAIR"),
                        rs.getString("RECOMMENDATION")
                ),
                roundId
        );
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

record DecisionPackageRow(
        long manuscriptId,
        long roundId,
        long versionId,
        int versionNo,
        String title,
        String decisionCode,
        String decisionReason,
        Timestamp decidedAt
) {
}

record DecisionPackageReviewRow(
        long reviewId,
        long reviewerId,
        int overallScore,
        String confidenceLevel,
        String strengths,
        String weaknesses,
        String commentsToAuthor,
        String commentsToChair,
        String recommendation
) {
}
