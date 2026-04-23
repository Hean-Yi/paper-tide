package com.example.review.analysis.infrastructure;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisProjectionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisProjectionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<AnalysisProjectionResponse> listForAnchor(AnalysisType analysisType, AnalysisBusinessAnchor businessAnchor) {
        return jdbcTemplate.query(
                """
                SELECT P.PROJECTION_ID,
                       P.ANALYSIS_TYPE,
                       P.BUSINESS_STATUS,
                       P.SUMMARY_TEXT,
                       P.REDACTED_RESULT,
                       P.IS_SUPERSEDED,
                       P.UPDATED_AT
                FROM ANALYSIS_PROJECTION P
                JOIN ANALYSIS_INTENT I ON I.INTENT_ID = P.INTENT_ID
                WHERE I.ANALYSIS_TYPE = ?
                  AND I.BUSINESS_ANCHOR_TYPE = ?
                  AND I.BUSINESS_ANCHOR_ID = ?
                  AND (
                    (I.BUSINESS_ANCHOR_VERSION_ID IS NULL AND ? IS NULL)
                    OR I.BUSINESS_ANCHOR_VERSION_ID = ?
                  )
                ORDER BY P.UPDATED_AT DESC, P.PROJECTION_ID DESC
                """,
                ps -> {
                    ps.setString(1, analysisType.name());
                    ps.setString(2, businessAnchor.businessAnchorType().name());
                    ps.setLong(3, businessAnchor.businessAnchorId());
                    setNullableVersionId(ps, 4, businessAnchor.businessAnchorVersionId());
                    setNullableVersionId(ps, 5, businessAnchor.businessAnchorVersionId());
                },
                (rs, rowNum) -> new AnalysisProjectionResponse(
                        rs.getLong("PROJECTION_ID"),
                        rs.getString("ANALYSIS_TYPE"),
                        rs.getString("BUSINESS_STATUS"),
                        rs.getString("SUMMARY_TEXT"),
                        parseJson(rs.getString("REDACTED_RESULT")),
                        rs.getInt("IS_SUPERSEDED") == 1,
                        toInstant(rs.getTimestamp("UPDATED_AT"))
                )
        );
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse analysis projection JSON", ex);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setNullableVersionId(java.sql.PreparedStatement ps, int index, Long versionId) throws java.sql.SQLException {
        if (versionId == null) {
            ps.setNull(index, Types.NUMERIC);
            return;
        }
        ps.setLong(index, versionId);
    }

    public void saveProjection(
            long intentId,
            String analysisType,
            String businessStatus,
            Map<String, Object> summaryProjection,
            Map<String, Object> redactedResult
    ) {
        jdbcTemplate.update(
                """
                MERGE INTO ANALYSIS_PROJECTION p
                USING (SELECT ? AS INTENT_ID FROM DUAL) incoming
                ON (p.INTENT_ID = incoming.INTENT_ID)
                WHEN MATCHED THEN UPDATE SET
                  ANALYSIS_TYPE = ?,
                  VISIBILITY_LEVEL = 'REDACTED_ONLY',
                  BUSINESS_STATUS = ?,
                  SUMMARY_TEXT = ?,
                  REDACTED_RESULT = ?,
                  RAW_RESULT_REFERENCE = NULL,
                  IS_SUPERSEDED = 0,
                  UPDATED_AT = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                  PROJECTION_ID, INTENT_ID, ANALYSIS_TYPE, VISIBILITY_LEVEL, BUSINESS_STATUS, SUMMARY_TEXT,
                  REDACTED_RESULT, RAW_RESULT_REFERENCE, IS_SUPERSEDED, UPDATED_AT
                ) VALUES (
                  SEQ_ANALYSIS_PROJECTION.NEXTVAL, ?, ?, 'REDACTED_ONLY', ?, ?, ?, NULL, 0, CURRENT_TIMESTAMP
                )
                """,
                intentId,
                analysisType,
                businessStatus,
                summaryText(summaryProjection),
                toJson(redactedResult),
                intentId,
                analysisType,
                businessStatus,
                summaryText(summaryProjection),
                toJson(redactedResult)
        );
    }

    private String summaryText(Map<String, Object> summaryProjection) {
        Object summary = summaryProjection.get("summary");
        return summary == null ? null : String.valueOf(summary);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize analysis projection JSON", ex);
        }
    }
}
