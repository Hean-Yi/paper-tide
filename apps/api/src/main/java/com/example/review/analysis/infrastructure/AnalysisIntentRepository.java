package com.example.review.analysis.infrastructure;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisStatus;
import com.example.review.analysis.domain.AnalysisType;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisIntentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AnalysisIntentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createOrReuseIntent(
            AnalysisType analysisType,
            AnalysisBusinessAnchor businessAnchor,
            long requestedBy,
            String idempotencyKey
    ) {
        Long intentId = jdbcTemplate.queryForObject("SELECT SEQ_ANALYSIS_INTENT.NEXTVAL FROM DUAL", Long.class);
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ANALYSIS_INTENT (
                      INTENT_ID, ANALYSIS_TYPE, BUSINESS_ANCHOR_TYPE, BUSINESS_ANCHOR_ID, BUSINESS_ANCHOR_VERSION_ID,
                      REQUESTED_BY, IDEMPOTENCY_KEY, BUSINESS_STATUS, EXECUTION_JOB_ID, CREATED_AT
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)
                    """,
                    intentId,
                    analysisType.name(),
                    businessAnchor.businessAnchorType().name(),
                    businessAnchor.businessAnchorId(),
                    businessAnchor.businessAnchorVersionId(),
                    requestedBy,
                    idempotencyKey,
                    AnalysisStatus.REQUESTED.name()
            );
            return intentId;
        } catch (DuplicateKeyException ex) {
            return findIntentIdByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
        }
    }

    public Optional<IntentSummary> findLatestIntent(AnalysisType analysisType, AnalysisBusinessAnchor businessAnchor) {
        List<IntentSummary> rows = jdbcTemplate.query(
                """
                SELECT INTENT_ID, ANALYSIS_TYPE, BUSINESS_STATUS
                FROM ANALYSIS_INTENT
                WHERE ANALYSIS_TYPE = ?
                  AND BUSINESS_ANCHOR_TYPE = ?
                  AND BUSINESS_ANCHOR_ID = ?
                  AND (
                    (BUSINESS_ANCHOR_VERSION_ID IS NULL AND ? IS NULL)
                    OR BUSINESS_ANCHOR_VERSION_ID = ?
                  )
                ORDER BY INTENT_ID DESC
                FETCH FIRST 1 ROWS ONLY
                """,
                ps -> {
                    ps.setString(1, analysisType.name());
                    ps.setString(2, businessAnchor.businessAnchorType().name());
                    ps.setLong(3, businessAnchor.businessAnchorId());
                    setNullableVersionId(ps, 4, businessAnchor.businessAnchorVersionId());
                    setNullableVersionId(ps, 5, businessAnchor.businessAnchorVersionId());
                },
                (rs, rowNum) -> new IntentSummary(
                        rs.getLong("INTENT_ID"),
                        rs.getString("ANALYSIS_TYPE"),
                        rs.getString("BUSINESS_STATUS")
                )
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public int nextRequestVersion(AnalysisType analysisType, AnalysisBusinessAnchor businessAnchor) {
        Integer count = jdbcTemplate.query(
                """
                SELECT COUNT(*)
                FROM ANALYSIS_INTENT
                WHERE ANALYSIS_TYPE = ?
                  AND BUSINESS_ANCHOR_TYPE = ?
                  AND BUSINESS_ANCHOR_ID = ?
                  AND (
                    (BUSINESS_ANCHOR_VERSION_ID IS NULL AND ? IS NULL)
                    OR BUSINESS_ANCHOR_VERSION_ID = ?
                  )
                """,
                ps -> {
                    ps.setString(1, analysisType.name());
                    ps.setString(2, businessAnchor.businessAnchorType().name());
                    ps.setLong(3, businessAnchor.businessAnchorId());
                    setNullableVersionId(ps, 4, businessAnchor.businessAnchorVersionId());
                    setNullableVersionId(ps, 5, businessAnchor.businessAnchorVersionId());
                },
                rs -> rs.next() ? rs.getInt(1) : 0
        );
        return (count == null ? 0 : count) + 1;
    }

    private Optional<Long> findIntentIdByIdempotencyKey(String idempotencyKey) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT INTENT_ID FROM ANALYSIS_INTENT WHERE IDEMPOTENCY_KEY = ?",
                (rs, rowNum) -> rs.getLong("INTENT_ID"),
                idempotencyKey
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void updateBusinessStatus(long intentId, String businessStatus) {
        jdbcTemplate.update(
                "UPDATE ANALYSIS_INTENT SET BUSINESS_STATUS = ? WHERE INTENT_ID = ?",
                businessStatus,
                intentId
        );
    }

    private void setNullableVersionId(java.sql.PreparedStatement ps, int index, Long versionId) throws java.sql.SQLException {
        if (versionId == null) {
            ps.setNull(index, Types.NUMERIC);
            return;
        }
        ps.setLong(index, versionId);
    }

    public record IntentSummary(long intentId, String analysisType, String businessStatus) {
    }
}
