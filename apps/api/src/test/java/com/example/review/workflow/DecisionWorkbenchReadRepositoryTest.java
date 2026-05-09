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

@SpringBootTest
class DecisionWorkbenchReadRepositoryTest {

    @Autowired
    private DecisionWorkbenchReadRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWorkflowTables() {
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE_REVISION");
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE");
        jdbcTemplate.update("DELETE FROM WORKFLOW_FORM_RESPONSE");
        jdbcTemplate.update("DELETE FROM AUTHOR_FEEDBACK");
        jdbcTemplate.update("DELETE FROM PAPER_TAG");
        jdbcTemplate.update("DELETE FROM IMPORT_BATCH");
        jdbcTemplate.update("DELETE FROM PAPER_ROLE_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_FORM_FIELD");
        jdbcTemplate.update("DELETE FROM CONFERENCE_FORM_DEFINITION");
        jdbcTemplate.update("DELETE FROM REVIEW_DISCUSSION_MESSAGE");
        jdbcTemplate.update("DELETE FROM CAMERA_READY_SUBMISSION");
        jdbcTemplate.update("DELETE FROM COMMUNICATION_LOG");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEWER_BID");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_DRAFT");
        jdbcTemplate.update("DELETE FROM SYS_NOTIFICATION");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM AUDIT_LOG");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        
        // Clean up analysis and execution tables. ANALYSIS_INTENT and EXECUTION_JOB have a deferred bidirectional FK.
        jdbcTemplate.update("UPDATE ANALYSIS_INTENT SET EXECUTION_JOB_ID = NULL WHERE EXECUTION_JOB_ID IS NOT NULL");
        jdbcTemplate.update("DELETE FROM EXECUTION_INBOX");
        jdbcTemplate.update("DELETE FROM EXECUTION_OUTBOX");
        jdbcTemplate.update("DELETE FROM EXECUTION_ARTIFACT");
        jdbcTemplate.update("DELETE FROM EXECUTION_ATTEMPT");
        jdbcTemplate.update("DELETE FROM EXECUTION_JOB");
        jdbcTemplate.update("DELETE FROM ANALYSIS_OUTBOX");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INBOX");
        jdbcTemplate.update("DELETE FROM ANALYSIS_PROJECTION");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INTENT");
        ensureChairUser(1003L, "chair_demo");
        ensureChairUser(2003L, "decision_workbench_other_chair");
        ensureConference(0L, 1003L, "Legacy / Platform Default");
        ensureConference(77L, 2003L, "Other Chair Conference");
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
        List<DecisionWorkbenchBase> rounds = repository.findPendingAndInProgressRounds(1003L);
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

    @Test
    void findPendingAndInProgressRounds_filtersByConferenceOrganizer() {
        long ownedManuscript = seedManuscript("UNDER_REVIEW", 0L);
        long ownedVersion = seedVersion(ownedManuscript);
        long ownedRound = seedRound(ownedManuscript, ownedVersion, "IN_PROGRESS");

        long otherManuscript = seedManuscript("UNDER_REVIEW", 77L);
        long otherVersion = seedVersion(otherManuscript);
        long otherRound = seedRound(otherManuscript, otherVersion, "IN_PROGRESS");

        List<Long> chairRoundIds = repository.findPendingAndInProgressRounds(1003L).stream()
                .map(DecisionWorkbenchBase::roundId)
                .toList();
        List<Long> adminRoundIds = repository.findPendingAndInProgressRounds(null).stream()
                .map(DecisionWorkbenchBase::roundId)
                .toList();

        assertThat(chairRoundIds).containsExactly(ownedRound);
        assertThat(adminRoundIds).containsExactly(ownedRound, otherRound);
    }

    private long seedManuscript(String status) {
        return seedManuscript(status, 0L);
    }

    private long seedManuscript(String status, long conferenceId) {
        long id = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, CONFERENCE_ID)
                VALUES (?, 1001, ?, 1, 'DOUBLE_BLIND', CURRENT_TIMESTAMP, ?)
                """, id, status, conferenceId);
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

    private void ensureConference(long conferenceId, long organizerUserId, String name) {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE C
                USING (
                  SELECT ? AS CONFERENCE_ID,
                         ? AS NAME,
                         'WB' || ? AS ACRONYM,
                         2026 AS CONFERENCE_YEAR,
                         ? AS ORGANIZER_USER_ID,
                         'OPEN_FOR_SUBMISSION' AS CONFERENCE_STATUS,
                         'DOUBLE_BLIND' AS BLIND_MODE,
                         'Default conference for decision workbench tests.' AS CFP_TEXT,
                         '["TEST"]' AS TOPIC_AREAS_JSON,
                         3 AS TARGET_REVIEWS_PER_PAPER,
                         3 AS DEFAULT_REVIEWER_MAX_LOAD,
                         'decision-workbench-' || ? AS PUBLIC_SLUG,
                         0 AS CFP_PUBLISHED
                  FROM DUAL
                ) S
                ON (C.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN MATCHED THEN
                  UPDATE SET C.ORGANIZER_USER_ID = S.ORGANIZER_USER_ID,
                             C.CONFERENCE_STATUS = S.CONFERENCE_STATUS,
                             C.UPDATED_AT = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN
                  INSERT (
                    CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                    CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                    TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                  )
                  VALUES (
                    S.CONFERENCE_ID, S.NAME, S.ACRONYM, S.CONFERENCE_YEAR, S.ORGANIZER_USER_ID,
                    S.CONFERENCE_STATUS, S.BLIND_MODE, S.CFP_TEXT, S.TOPIC_AREAS_JSON,
                    S.TARGET_REVIEWS_PER_PAPER, S.DEFAULT_REVIEWER_MAX_LOAD, S.PUBLIC_SLUG, S.CFP_PUBLISHED
                  )
                """,
                conferenceId,
                name,
                conferenceId,
                organizerUserId,
                conferenceId
        );
    }

    private void ensureChairUser(long userId, String username) {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER U
                USING (
                  SELECT ? AS USER_ID,
                         ? AS USERNAME,
                         '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq' AS PASSWORD_HASH,
                         ? AS REAL_NAME,
                         ? || '@example.com' AS EMAIL,
                         'Test University' AS INSTITUTION,
                         'ACTIVE' AS STATUS
                  FROM DUAL
                ) S
                ON (U.USER_ID = S.USER_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS)
                  VALUES (S.USER_ID, S.USERNAME, S.PASSWORD_HASH, S.REAL_NAME, S.EMAIL, S.INSTITUTION, S.STATUS)
                """,
                userId,
                username,
                username,
                username
        );
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT ? AS USER_ID, 3 AS ROLE_ID FROM DUAL) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """,
                userId
        );
    }
}
