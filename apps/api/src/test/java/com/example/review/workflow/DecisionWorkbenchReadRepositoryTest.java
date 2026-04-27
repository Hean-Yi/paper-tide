package com.example.review.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisStatus;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.review.support.LegacyAgentArtifactsCleanup;

@SpringBootTest
class DecisionWorkbenchReadRepositoryTest {

    @Autowired
    private DecisionWorkbenchReadRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWorkflowTables() {
        LegacyAgentArtifactsCleanup.deleteLegacyAgentArtifacts(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM SYS_NOTIFICATION");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM AUDIT_LOG");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        
        // Clean up analysis tables
        jdbcTemplate.update("DELETE FROM ANALYSIS_PROJECTION");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INTENT");
    }

    @Test
    void testBulkQueries() {
        // Seed round 1
        long man1 = seedManuscript("UNDER_REVIEW");
        long ver1 = seedVersion(man1);
        long round1 = seedRound(man1, ver1, "IN_PROGRESS");
        long assignment1 = seedAssignment(round1, man1, ver1, "SUBMITTED");
        
        // Seed round 2
        long man2 = seedManuscript("UNDER_REVIEW");
        long ver2 = seedVersion(man2);
        long round2 = seedRound(man2, ver2, "PENDING");
        
        // Seed analysis intent and projection for round 1
        long intentId = seedIntent(round1, "AVAILABLE");
        seedProjection(intentId, "AVAILABLE", "Summary text");
        
        // Query base rounds
        List<DecisionWorkbenchBase> rounds = repository.findPendingAndInProgressRounds();
        assertThat(rounds).hasSize(2);
        
        List<Long> roundIds = rounds.stream().map(DecisionWorkbenchBase::roundId).toList();
        
        // Query assignments
        Map<Long, List<DecisionAssignmentItem>> assignments = repository.findAssignmentsByRoundIds(roundIds);
        assertThat(assignments).containsKeys(round1);
        assertThat(assignments.get(round1)).hasSize(1);
        assertThat(assignments.get(round1).get(0).assignmentId()).isEqualTo(assignment1);
        assertThat(assignments.get(round2)).isNullOrEmpty();
        
        // Query intents
        Map<Long, AnalysisIntentResponse> intents = repository.findConflictIntentsByRoundIds(roundIds);
        assertThat(intents).containsKeys(round1);
        assertThat(intents.get(round1).intentId()).isEqualTo(intentId);
        assertThat(intents.get(round2)).isNull();
        
        // Query projections
        Map<Long, List<AnalysisProjectionResponse>> projections = repository.findConflictProjectionsByRoundIds(roundIds);
        assertThat(projections).containsKeys(round1);
        assertThat(projections.get(round1)).hasSize(1);
        assertThat(projections.get(round1).get(0).summaryText()).isEqualTo("Summary text");
        assertThat(projections.get(round2)).isNullOrEmpty();
    }

    private long seedManuscript(String status) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT)
                VALUES (?, 1001, ?, 1, 'DOUBLE_BLIND', CURRENT_TIMESTAMP)
                """, id, status);
        return id;
    }

    private long seedVersion(long manuscriptId) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, SUBMITTED_BY, SUBMITTED_AT)
                VALUES (?, ?, 1, 'INITIAL', 'Test', 1001, CURRENT_TIMESTAMP)
                """, id, manuscriptId);
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", id, manuscriptId);
        return id;
    }

    private long seedRound(long manuscriptId, long versionId, String status) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT)
                VALUES (?, ?, 1, ?, ?, 'REALLOCATE_REVIEWERS', 1, CURRENT_TIMESTAMP, 1003, CURRENT_TIMESTAMP)
                """, id, manuscriptId, versionId, status);
        return id;
    }
    
    private long seedAssignment(long roundId, long manuscriptId, long versionId, String status) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT)
                VALUES (?, ?, ?, ?, 1002, ?, CURRENT_TIMESTAMP)
                """, id, roundId, manuscriptId, versionId, status);
        return id;
    }
    
    private long seedIntent(long roundId, String status) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_ANALYSIS_INTENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ANALYSIS_INTENT (INTENT_ID, ANALYSIS_TYPE, BUSINESS_ANCHOR_TYPE, BUSINESS_ANCHOR_ID, REQUESTED_BY, IDEMPOTENCY_KEY, BUSINESS_STATUS, CREATED_AT)
                VALUES (?, ?, 'ROUND', ?, 1003, ?, ?, CURRENT_TIMESTAMP)
                """, id, AnalysisType.CONFLICT_ANALYSIS.name(), roundId, "test-key-" + roundId, status);
        return id;
    }
    
    private void seedProjection(long intentId, String status, String summary) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_ANALYSIS_PROJECTION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ANALYSIS_PROJECTION (PROJECTION_ID, INTENT_ID, ANALYSIS_TYPE, VISIBILITY_LEVEL, BUSINESS_STATUS, SUMMARY_TEXT, IS_SUPERSEDED, UPDATED_AT)
                VALUES (?, ?, ?, 'REDACTED_ONLY', ?, ?, 0, CURRENT_TIMESTAMP)
                """, id, intentId, AnalysisType.CONFLICT_ANALYSIS.name(), status, summary);
    }
}
