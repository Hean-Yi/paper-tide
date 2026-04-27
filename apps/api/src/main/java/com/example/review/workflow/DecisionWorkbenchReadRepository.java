package com.example.review.workflow;

import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionWorkbenchReadRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DecisionWorkbenchReadRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DecisionWorkbenchBase> findPendingAndInProgressRounds() {
        return jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       R.ROUND_NO,
                       R.ROUND_STATUS,
                       R.DEADLINE_AT,
                       M.CURRENT_STATUS,
                       M.LAST_DECISION_CODE,
                       V.TITLE,
                       V.VERSION_NO,
                       (SELECT COUNT(*) FROM REVIEW_ASSIGNMENT A WHERE A.ROUND_ID = R.ROUND_ID) AS ASSIGNMENT_COUNT,
                       (SELECT COUNT(*) FROM REVIEW_REPORT RP WHERE RP.ROUND_ID = R.ROUND_ID) AS SUBMITTED_REVIEW_COUNT,
                       (SELECT COUNT(*)
                        FROM CONFLICT_CHECK_RECORD C
                        WHERE C.ASSIGNMENT_ID IN (
                          SELECT A.ASSIGNMENT_ID FROM REVIEW_ASSIGNMENT A WHERE A.ROUND_ID = R.ROUND_ID
                        )) AS CONFLICT_COUNT
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = R.VERSION_ID
                WHERE R.ROUND_STATUS IN ('PENDING', 'IN_PROGRESS')
                ORDER BY R.ROUND_ID
                """,
                (rs, rowNum) -> new DecisionWorkbenchBase(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getString("ROUND_STATUS"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getString("LAST_DECISION_CODE"),
                        rs.getString("TITLE"),
                        rs.getInt("VERSION_NO"),
                        rs.getInt("ASSIGNMENT_COUNT"),
                        rs.getInt("SUBMITTED_REVIEW_COUNT"),
                        rs.getInt("CONFLICT_COUNT")
                )
        );
    }

    public Map<Long, List<DecisionAssignmentItem>> findAssignmentsByRoundIds(List<Long> roundIds) {
        if (roundIds.isEmpty()) return Collections.emptyMap();
        
        List<DecisionAssignmentItemWithRound> items = jdbcTemplate.query(
                """
                SELECT ROUND_ID,
                       ASSIGNMENT_ID,
                       REVIEWER_ID,
                       TASK_STATUS,
                       ASSIGNED_AT,
                       ACCEPTED_AT,
                       DEADLINE_AT,
                       SUBMITTED_AT,
                       REASSIGNED_FROM_ID
                FROM REVIEW_ASSIGNMENT
                WHERE ROUND_ID IN (:roundIds)
                ORDER BY ASSIGNMENT_ID
                """,
                new MapSqlParameterSource("roundIds", roundIds),
                (rs, rowNum) -> new DecisionAssignmentItemWithRound(
                        rs.getLong("ROUND_ID"),
                        new DecisionAssignmentItem(
                                rs.getLong("ASSIGNMENT_ID"),
                                rs.getLong("REVIEWER_ID"),
                                rs.getString("TASK_STATUS"),
                                rs.getTimestamp("ASSIGNED_AT"),
                                rs.getTimestamp("ACCEPTED_AT"),
                                rs.getTimestamp("DEADLINE_AT"),
                                rs.getTimestamp("SUBMITTED_AT"),
                                rs.getObject("REASSIGNED_FROM_ID", Long.class)
                        )
                )
        );
        
        return items.stream().collect(Collectors.groupingBy(
                DecisionAssignmentItemWithRound::roundId,
                Collectors.mapping(DecisionAssignmentItemWithRound::item, Collectors.toList())
        ));
    }

    public Map<Long, AnalysisIntentResponse> findConflictIntentsByRoundIds(List<Long> roundIds) {
        if (roundIds.isEmpty()) return Collections.emptyMap();
        
        // Use ROW_NUMBER to get the latest intent for each round
        List<AnalysisIntentResponseWithRound> intents = jdbcTemplate.query(
                """
                SELECT BUSINESS_ANCHOR_ID AS ROUND_ID,
                       INTENT_ID,
                       ANALYSIS_TYPE,
                       BUSINESS_STATUS
                FROM (
                    SELECT BUSINESS_ANCHOR_ID,
                           INTENT_ID,
                           ANALYSIS_TYPE,
                           BUSINESS_STATUS,
                           ROW_NUMBER() OVER(PARTITION BY BUSINESS_ANCHOR_ID ORDER BY INTENT_ID DESC) as rn
                    FROM ANALYSIS_INTENT
                    WHERE ANALYSIS_TYPE = :analysisType
                      AND BUSINESS_ANCHOR_TYPE = 'ROUND'
                      AND BUSINESS_ANCHOR_ID IN (:roundIds)
                )
                WHERE rn = 1
                """,
                new MapSqlParameterSource()
                        .addValue("analysisType", AnalysisType.CONFLICT_ANALYSIS.name())
                        .addValue("roundIds", roundIds),
                (rs, rowNum) -> new AnalysisIntentResponseWithRound(
                        rs.getLong("ROUND_ID"),
                        new AnalysisIntentResponse(
                                rs.getLong("INTENT_ID"),
                                rs.getString("ANALYSIS_TYPE"),
                                rs.getString("BUSINESS_STATUS")
                        )
                )
        );
        
        return intents.stream().collect(Collectors.toMap(
                AnalysisIntentResponseWithRound::roundId,
                AnalysisIntentResponseWithRound::intent
        ));
    }

    public Map<Long, List<AnalysisProjectionResponse>> findConflictProjectionsByRoundIds(List<Long> roundIds) {
        if (roundIds.isEmpty()) return Collections.emptyMap();
        
        List<AnalysisProjectionResponseWithRound> projections = jdbcTemplate.query(
                """
                SELECT I.BUSINESS_ANCHOR_ID AS ROUND_ID,
                       P.PROJECTION_ID,
                       P.ANALYSIS_TYPE,
                       P.BUSINESS_STATUS,
                       P.SUMMARY_TEXT,
                       P.REDACTED_RESULT,
                       P.IS_SUPERSEDED,
                       P.UPDATED_AT
                FROM ANALYSIS_PROJECTION P
                JOIN ANALYSIS_INTENT I ON I.INTENT_ID = P.INTENT_ID
                WHERE I.ANALYSIS_TYPE = :analysisType
                  AND I.BUSINESS_ANCHOR_TYPE = 'ROUND'
                  AND I.BUSINESS_ANCHOR_ID IN (:roundIds)
                ORDER BY P.UPDATED_AT DESC, P.PROJECTION_ID DESC
                """,
                new MapSqlParameterSource()
                        .addValue("analysisType", AnalysisType.CONFLICT_ANALYSIS.name())
                        .addValue("roundIds", roundIds),
                (rs, rowNum) -> new AnalysisProjectionResponseWithRound(
                        rs.getLong("ROUND_ID"),
                        new AnalysisProjectionResponse(
                                rs.getLong("PROJECTION_ID"),
                                rs.getString("ANALYSIS_TYPE"),
                                rs.getString("BUSINESS_STATUS"),
                                rs.getString("SUMMARY_TEXT"),
                                parseJson(rs.getString("REDACTED_RESULT")),
                                rs.getInt("IS_SUPERSEDED") == 1,
                                toInstant(rs.getTimestamp("UPDATED_AT"))
                        )
                )
        );
        
        return projections.stream().collect(Collectors.groupingBy(
                AnalysisProjectionResponseWithRound::roundId,
                Collectors.mapping(AnalysisProjectionResponseWithRound::projection, Collectors.toList())
        ));
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

    private record DecisionAssignmentItemWithRound(long roundId, DecisionAssignmentItem item) {}
    private record AnalysisIntentResponseWithRound(long roundId, AnalysisIntentResponse intent) {}
    private record AnalysisProjectionResponseWithRound(long roundId, AnalysisProjectionResponse projection) {}
}
