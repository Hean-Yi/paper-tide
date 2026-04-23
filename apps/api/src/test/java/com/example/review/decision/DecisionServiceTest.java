package com.example.review.decision;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.review.support.LegacyAgentArtifactsCleanup;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDecisionTables() {
        LegacyAgentArtifactsCleanup.deleteLegacyAgentArtifacts(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM SYS_NOTIFICATION");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM AUDIT_LOG");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
    }

    @Test
    void reviewerSubmitReviewReportPersistsScoresAndMarksAssignmentSubmitted() throws Exception {
        ReviewFixture fixture = seedUnderReviewAssignment("ACCEPTED");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/review-report", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "noveltyScore": 4,
                                  "methodScore": 5,
                                  "experimentScore": 4,
                                  "writingScore": 3,
                                  "overallScore": 4,
                                  "confidenceLevel": "HIGH",
                                  "strengths": "strong method",
                                  "weaknesses": "limited ablation",
                                  "commentsToAuthor": "please clarify section 4",
                                  "commentsToChair": "sound paper",
                                  "recommendation": "MINOR_REVISION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").isNumber())
                .andExpect(jsonPath("$.taskStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.assignmentId").value(fixture.assignmentId()));

        Map<String, Object> reportRow = jdbcTemplate.queryForMap(
                """
                SELECT ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID, OVERALL_SCORE, CONFIDENCE_LEVEL, RECOMMENDATION
                FROM REVIEW_REPORT
                WHERE ASSIGNMENT_ID = ?
                """,
                fixture.assignmentId()
        );
        Map<String, Object> assignmentRow = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, SUBMITTED_AT FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                fixture.assignmentId()
        );

        org.junit.jupiter.api.Assertions.assertEquals(fixture.assignmentId(), ((Number) reportRow.get("ASSIGNMENT_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(fixture.roundId(), ((Number) reportRow.get("ROUND_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("HIGH", reportRow.get("CONFIDENCE_LEVEL"));
        org.junit.jupiter.api.Assertions.assertEquals("MINOR_REVISION", reportRow.get("RECOMMENDATION"));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", assignmentRow.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertNotNull(assignmentRow.get("SUBMITTED_AT"));
    }

    @Test
    void chairDecisionUpdatesManuscriptAndRoundWithinSingleTransaction() throws Exception {
        ReviewFixture fixture = seedUnderReviewAssignment("SUBMITTED");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/decisions")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "roundId": %d,
                                  "versionId": %d,
                                  "decisionCode": "MAJOR_REVISION",
                                  "decisionReason": "needs a stronger experiment section"
                                }
                                """.formatted(fixture.manuscriptId(), fixture.roundId(), fixture.versionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionCode").value("MAJOR_REVISION"))
                .andExpect(jsonPath("$.currentStatus").value("REVISION_REQUIRED"))
                .andExpect(jsonPath("$.roundStatus").value("COMPLETED"));

        Map<String, Object> manuscriptRow = jdbcTemplate.queryForMap(
                "SELECT CURRENT_STATUS, LAST_DECISION_CODE FROM MANUSCRIPT WHERE MANUSCRIPT_ID = ?",
                fixture.manuscriptId()
        );
        Map<String, Object> roundRow = jdbcTemplate.queryForMap(
                "SELECT ROUND_STATUS FROM REVIEW_ROUND WHERE ROUND_ID = ?",
                fixture.roundId()
        );
        Map<String, Object> decisionRow = jdbcTemplate.queryForMap(
                """
                SELECT MANUSCRIPT_ID, ROUND_ID, VERSION_ID, DECISION_CODE, DECISION_REASON, DECIDED_BY
                FROM DECISION_RECORD
                WHERE ROUND_ID = ?
                """,
                fixture.roundId()
        );
        Integer authorNotifications = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM SYS_NOTIFICATION
                WHERE RECEIVER_ID = 1001 AND BIZ_TYPE = 'DECISION'
                """,
                Integer.class
        );
        Integer auditLogs = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM AUDIT_LOG
                WHERE BIZ_TYPE = 'DECISION' AND BIZ_ID = ?
                """,
                Integer.class,
                fixture.roundId()
        );

        org.junit.jupiter.api.Assertions.assertEquals("REVISION_REQUIRED", manuscriptRow.get("CURRENT_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("MAJOR_REVISION", manuscriptRow.get("LAST_DECISION_CODE"));
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", roundRow.get("ROUND_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals(fixture.manuscriptId(), ((Number) decisionRow.get("MANUSCRIPT_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(fixture.versionId(), ((Number) decisionRow.get("VERSION_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("MAJOR_REVISION", decisionRow.get("DECISION_CODE"));
        org.junit.jupiter.api.Assertions.assertEquals(1003L, ((Number) decisionRow.get("DECIDED_BY")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(1, authorNotifications);
        org.junit.jupiter.api.Assertions.assertEquals(1, auditLogs);
    }

    @Test
    void deskRejectPersistsDecisionCode() throws Exception {
        ScreeningFixture fixture = seedUnderScreeningManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/decisions")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "roundId": %d,
                                  "versionId": %d,
                                  "decisionCode": "DESK_REJECT",
                                  "decisionReason": "outside venue scope"
                                }
                                """.formatted(fixture.manuscriptId(), fixture.roundId(), fixture.versionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionCode").value("DESK_REJECT"))
                .andExpect(jsonPath("$.currentStatus").value("DESK_REJECTED"));

        Map<String, Object> manuscriptRow = jdbcTemplate.queryForMap(
                "SELECT CURRENT_STATUS, LAST_DECISION_CODE FROM MANUSCRIPT WHERE MANUSCRIPT_ID = ?",
                fixture.manuscriptId()
        );
        Map<String, Object> decisionRow = jdbcTemplate.queryForMap(
                "SELECT DECISION_CODE, ROUND_ID FROM DECISION_RECORD WHERE ROUND_ID = ?",
                fixture.roundId()
        );

        org.junit.jupiter.api.Assertions.assertEquals("DESK_REJECTED", manuscriptRow.get("CURRENT_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("DESK_REJECT", manuscriptRow.get("LAST_DECISION_CODE"));
        org.junit.jupiter.api.Assertions.assertEquals("DESK_REJECT", decisionRow.get("DECISION_CODE"));
    }

    private ReviewFixture seedUnderReviewAssignment(String assignmentStatus) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        long assignmentId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, 'UNDER_REVIEW', 1, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Task 6 Seed', 'seed abstract', 'seed', 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID, MANUSCRIPT_ID, VERSION_ID, USER_ID, AUTHOR_NAME, EMAIL, INSTITUTION, AUTHOR_ORDER, IS_CORRESPONDING, IS_EXTERNAL
                ) VALUES (
                  SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, 1001, 'Author Demo', 'author_demo@example.com', 'Southeast University', 1, 1, 0
                )
                """,
                manuscriptId,
                versionId
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 1, ?, 'IN_PROGRESS', 'REALLOCATE_REVIEWERS', 0, ?, 1003, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plusSeconds(86400))
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (
                  ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT, ACCEPTED_AT, DECLINED_AT, DECLINE_REASON, DEADLINE_AT, SUBMITTED_AT, REASSIGNED_FROM_ID
                ) VALUES (?, ?, ?, ?, 1002, ?, ?, ?, NULL, NULL, ?, ?, NULL)
                """,
                assignmentId,
                roundId,
                manuscriptId,
                versionId,
                assignmentStatus,
                Timestamp.from(Instant.now().minusSeconds(7200)),
                "ACCEPTED".equals(assignmentStatus) || "SUBMITTED".equals(assignmentStatus)
                        ? Timestamp.from(Instant.now().minusSeconds(3600))
                        : null,
                Timestamp.from(Instant.now().plusSeconds(86400)),
                "SUBMITTED".equals(assignmentStatus) ? Timestamp.from(Instant.now().minusSeconds(600)) : null
        );

        return new ReviewFixture(manuscriptId, versionId, roundId, assignmentId);
    }

    private ScreeningFixture seedUnderScreeningManuscript() {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, 'UNDER_SCREENING', 1, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Screening Seed', 'seed abstract', 'seed', 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 1, ?, 'PENDING', 'REUSE_REVIEWERS', 1, ?, 1003, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plusSeconds(86400))
        );

        return new ScreeningFixture(manuscriptId, versionId, roundId);
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("token").asText();
    }

    private record ReviewFixture(long manuscriptId, long versionId, long roundId, long assignmentId) {
    }

    private record ScreeningFixture(long manuscriptId, long versionId, long roundId) {
    }
}
