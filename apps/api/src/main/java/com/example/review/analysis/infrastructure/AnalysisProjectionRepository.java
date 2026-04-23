package com.example.review.analysis.infrastructure;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
                (rs, rowNum) -> new AnalysisProjectionResponse(
                        rs.getLong("PROJECTION_ID"),
                        rs.getString("ANALYSIS_TYPE"),
                        rs.getString("BUSINESS_STATUS"),
                        rs.getString("SUMMARY_TEXT"),
                        parseJson(rs.getString("REDACTED_RESULT")),
                        rs.getInt("IS_SUPERSEDED") == 1,
                        toInstant(rs.getTimestamp("UPDATED_AT"))
                ),
                analysisType.name(),
                businessAnchor.businessAnchorType().name(),
                businessAnchor.businessAnchorId(),
                businessAnchor.businessAnchorVersionId(),
                businessAnchor.businessAnchorVersionId()
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
}
