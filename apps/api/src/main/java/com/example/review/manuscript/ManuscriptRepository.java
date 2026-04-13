package com.example.review.manuscript;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManuscriptRepository {
    private final JdbcTemplate jdbcTemplate;

    public ManuscriptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextManuscriptId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
    }

    public void insert(long manuscriptId, long submitterId, String blindMode) {
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (
                  MANUSCRIPT_ID,
                  SUBMITTER_ID,
                  CURRENT_VERSION_ID,
                  CURRENT_STATUS,
                  CURRENT_ROUND_NO,
                  BLIND_MODE,
                  SUBMITTED_AT,
                  LAST_DECISION_CODE
                ) VALUES (?, ?, NULL, 'DRAFT', 0, ?, NULL, NULL)
                """,
                manuscriptId,
                submitterId,
                blindMode
        );
    }

    public void updateCurrentVersion(long manuscriptId, long currentVersionId) {
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?",
                currentVersionId,
                manuscriptId
        );
    }

    public void updateStatusAndSubmittedAt(long manuscriptId, String currentStatus, Timestamp submittedAt) {
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_STATUS = ?, SUBMITTED_AT = ? WHERE MANUSCRIPT_ID = ?",
                currentStatus,
                submittedAt,
                manuscriptId
        );
    }

    public void updateStatus(long manuscriptId, String currentStatus) {
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_STATUS = ? WHERE MANUSCRIPT_ID = ?",
                currentStatus,
                manuscriptId
        );
    }

    public Optional<ManuscriptRow> findById(long manuscriptId) {
        List<ManuscriptRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.SUBMITTER_ID,
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       M.LAST_DECISION_CODE,
                       V.TITLE AS CURRENT_VERSION_TITLE,
                       V.VERSION_NO AS CURRENT_VERSION_NO
                FROM MANUSCRIPT M
                LEFT JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> {
                    Long currentVersionId = rs.getObject("CURRENT_VERSION_ID", Long.class);
                    Integer currentVersionNo = rs.getObject("CURRENT_VERSION_NO", Integer.class);
                    return new ManuscriptRow(
                            rs.getLong("MANUSCRIPT_ID"),
                            rs.getLong("SUBMITTER_ID"),
                            currentVersionId,
                            rs.getString("CURRENT_STATUS"),
                            rs.getInt("CURRENT_ROUND_NO"),
                            rs.getString("BLIND_MODE"),
                            rs.getTimestamp("SUBMITTED_AT"),
                            rs.getString("LAST_DECISION_CODE"),
                            rs.getString("CURRENT_VERSION_TITLE"),
                            currentVersionNo
                    );
                },
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<ManuscriptRow> findByIdForUpdate(long manuscriptId) {
        List<ManuscriptRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.SUBMITTER_ID,
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       M.LAST_DECISION_CODE
                FROM MANUSCRIPT M
                WHERE M.MANUSCRIPT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> {
                    Long currentVersionId = rs.getObject("CURRENT_VERSION_ID", Long.class);
                    return new ManuscriptRow(
                            rs.getLong("MANUSCRIPT_ID"),
                            rs.getLong("SUBMITTER_ID"),
                            currentVersionId,
                            rs.getString("CURRENT_STATUS"),
                            rs.getInt("CURRENT_ROUND_NO"),
                            rs.getString("BLIND_MODE"),
                            rs.getTimestamp("SUBMITTED_AT"),
                            rs.getString("LAST_DECISION_CODE"),
                            null,
                            null
                    );
                },
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<ManuscriptSummaryRow> listBySubmitter(long submitterId) {
        return jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       M.LAST_DECISION_CODE,
                       V.TITLE AS CURRENT_VERSION_TITLE,
                       V.VERSION_NO AS CURRENT_VERSION_NO
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.SUBMITTER_ID = ?
                ORDER BY M.MANUSCRIPT_ID
                """,
                (rs, rowNum) -> new ManuscriptSummaryRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("CURRENT_VERSION_ID"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getInt("CURRENT_ROUND_NO"),
                        rs.getString("BLIND_MODE"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getString("LAST_DECISION_CODE"),
                        rs.getString("CURRENT_VERSION_TITLE"),
                        rs.getInt("CURRENT_VERSION_NO")
                ),
                submitterId
        );
    }

    public boolean reviewerHasAssignment(long manuscriptId, long versionId, long reviewerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM REVIEW_ASSIGNMENT
                WHERE MANUSCRIPT_ID = ?
                  AND VERSION_ID = ?
                  AND REVIEWER_ID = ?
                """,
                Integer.class,
                manuscriptId,
                versionId,
                reviewerId
        );
        return count != null && count > 0;
    }
}

record ManuscriptRow(
        long manuscriptId,
        long submitterId,
        Long currentVersionId,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String lastDecisionCode,
        String currentVersionTitle,
        Integer currentVersionNo
) {
}

record ManuscriptSummaryRow(
        long manuscriptId,
        long currentVersionId,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String lastDecisionCode,
        String currentVersionTitle,
        int currentVersionNo
) {
}
