package com.example.review.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
class ReviewWorkflowServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanReviewTables() {
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_RESULT");
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_TASK");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        seedChairAdminAndReviewerRoles();
    }

    @Test
    void assignReviewerCreatesAssignedTask() throws Exception {
        TestManuscript manuscript = seedSubmittedManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        long roundId = createRound(chairToken, manuscript.manuscriptId(), manuscript.versionId(), 1);

        MvcResult result = mockMvc.perform(post("/api/review-rounds/{roundId}/assignments", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1002,
                                  "deadlineAt": "2026-05-01T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").isNumber())
                .andExpect(jsonPath("$.taskStatus").value("ASSIGNED"))
                .andReturn();

        long assignmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("assignmentId")
                .asLong();

        Map<String, Object> roundRow = jdbcTemplate.queryForMap(
                "SELECT ROUND_STATUS FROM REVIEW_ROUND WHERE ROUND_ID = ?",
                roundId
        );
        Map<String, Object> assignmentRow = jdbcTemplate.queryForMap(
                """
                SELECT ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS
                FROM REVIEW_ASSIGNMENT
                WHERE ASSIGNMENT_ID = ?
                """,
                assignmentId
        );

        org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", roundRow.get("ROUND_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("ASSIGNED", assignmentRow.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals(roundId, ((Number) assignmentRow.get("ROUND_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(manuscript.manuscriptId(), ((Number) assignmentRow.get("MANUSCRIPT_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(manuscript.versionId(), ((Number) assignmentRow.get("VERSION_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(1002L, ((Number) assignmentRow.get("REVIEWER_ID")).longValue());
    }

    @Test
    void reviewerAcceptMovesAssignmentToAccepted() throws Exception {
        TestManuscript manuscript = seedSubmittedManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        long roundId = createRound(chairToken, manuscript.manuscriptId(), manuscript.versionId(), 1);
        long assignmentId = assignReviewer(chairToken, roundId, 1002);

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/accept", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("ACCEPTED"));

        Map<String, Object> assignmentRow = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, ACCEPTED_AT FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                assignmentId
        );
        org.junit.jupiter.api.Assertions.assertEquals("ACCEPTED", assignmentRow.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertNotNull(assignmentRow.get("ACCEPTED_AT"));
    }

    @Test
    void selfDeclaredConflictMovesTaskToDeclined() throws Exception {
        TestManuscript manuscript = seedSubmittedManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        long roundId = createRound(chairToken, manuscript.manuscriptId(), manuscript.versionId(), 1);
        long assignmentId = assignReviewer(chairToken, roundId, 1002);

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/decline", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "same lab collaboration",
                                  "conflictDeclared": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("DECLINED"));

        Map<String, Object> assignmentRow = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, DECLINED_AT FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                assignmentId
        );
        Integer conflictCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM CONFLICT_CHECK_RECORD
                WHERE ASSIGNMENT_ID = ? AND SOURCE = 'SELF_DECLARED'
                """,
                Integer.class,
                assignmentId
        );

        org.junit.jupiter.api.Assertions.assertEquals("DECLINED", assignmentRow.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertNotNull(assignmentRow.get("DECLINED_AT"));
        org.junit.jupiter.api.Assertions.assertEquals(1, conflictCount);
    }

    @Test
    void overdueTaskCanBeReassigned() throws Exception {
        TestManuscript manuscript = seedSubmittedManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        long roundId = createRound(chairToken, manuscript.manuscriptId(), manuscript.versionId(), 1);
        long originalAssignmentId = assignReviewer(chairToken, roundId, 1002);

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/mark-overdue", originalAssignmentId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("OVERDUE"));

        MvcResult reassignResult = mockMvc.perform(post("/api/review-assignments/{assignmentId}/reassign", originalAssignmentId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1012,
                                  "deadlineAt": "2026-05-10T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("ASSIGNED"))
                .andReturn();

        long newAssignmentId = objectMapper.readTree(reassignResult.getResponse().getContentAsString())
                .path("assignmentId")
                .asLong();

        Map<String, Object> oldAssignment = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                originalAssignmentId
        );
        Map<String, Object> newAssignment = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, REASSIGNED_FROM_ID, REVIEWER_ID FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                newAssignmentId
        );

        org.junit.jupiter.api.Assertions.assertEquals("REASSIGNED", oldAssignment.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("ASSIGNED", newAssignment.get("TASK_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals(originalAssignmentId, ((Number) newAssignment.get("REASSIGNED_FROM_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(1012L, ((Number) newAssignment.get("REVIEWER_ID")).longValue());
    }

    @Test
    void systemConflictCheckIsReturnedForRound() throws Exception {
        TestManuscript manuscript = seedSubmittedManuscript();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        long roundId = createRound(chairToken, manuscript.manuscriptId(), manuscript.versionId(), 1);
        assignReviewer(chairToken, roundId, 1012);

        mockMvc.perform(get("/api/review-rounds/{roundId}/conflict-checks", roundId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].source", is("SYSTEM_DETECTED")))
                .andExpect(jsonPath("$[0].reviewerId", is(1012)))
                .andExpect(jsonPath("$[0].conflictType", is("SAME_INSTITUTION")));
    }

    @Test
    void roundCreationRejectsUnsupportedManuscriptState() throws Exception {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, 'DRAFT', 0, 'DOUBLE_BLIND', NULL, NULL)
                """,
                manuscriptId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Draft Only', 'draft', 'draft', 1001, CURRENT_TIMESTAMP, NULL)
                """,
                versionId,
                manuscriptId
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);

        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/review-rounds")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "versionId": %d,
                                  "assignmentStrategy": "REALLOCATE_REVIEWERS",
                                  "screeningRequired": true,
                                  "deadlineAt": "2026-05-01T12:00:00Z"
                                }
                                """.formatted(manuscriptId, versionId)))
                .andExpect(status().isConflict());
    }

    private long createRound(String chairToken, long manuscriptId, long versionId, int expectedRoundNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/review-rounds")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "versionId": %d,
                                  "assignmentStrategy": "REALLOCATE_REVIEWERS",
                                  "screeningRequired": true,
                                  "deadlineAt": "2026-05-01T12:00:00Z"
                                }
                                """.formatted(manuscriptId, versionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").isNumber())
                .andExpect(jsonPath("$.roundNo").value(expectedRoundNo))
                .andExpect(jsonPath("$.roundStatus").value("PENDING"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("roundId").asLong();
    }

    private long assignReviewer(String chairToken, long roundId, long reviewerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/review-rounds/{roundId}/assignments", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": %d,
                                  "deadlineAt": "2026-05-01T12:00:00Z"
                                }
                                """.formatted(reviewerId)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("assignmentId").asLong();
    }

    private TestManuscript seedSubmittedManuscript() {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, 'SUBMITTED', 0, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Task 5 Seed', 'seed abstract', 'seed', 1001, ?, NULL)
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
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID, MANUSCRIPT_ID, VERSION_ID, USER_ID, AUTHOR_NAME, EMAIL, INSTITUTION, AUTHOR_ORDER, IS_CORRESPONDING, IS_EXTERNAL
                ) VALUES (
                  SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, NULL, 'Collaborator', 'collab@example.com', 'Nanjing University', 2, 0, 1
                )
                """,
                manuscriptId,
                versionId
        );

        return new TestManuscript(manuscriptId, versionId);
    }

    private void seedChairAdminAndReviewerRoles() {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER U
                USING (
                  SELECT 1012 AS USER_ID, 'conflicted_reviewer_demo' AS USERNAME, '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq' AS PASSWORD_HASH,
                         'Conflicted Reviewer Demo' AS REAL_NAME, 'conflicted_reviewer_demo@example.com' AS EMAIL, 'Southeast University' AS INSTITUTION, 'ACTIVE' AS STATUS
                  FROM DUAL
                ) S
                ON (U.USERNAME = S.USERNAME)
                WHEN MATCHED THEN
                  UPDATE SET U.PASSWORD_HASH = S.PASSWORD_HASH, U.REAL_NAME = S.REAL_NAME, U.EMAIL = S.EMAIL, U.INSTITUTION = S.INSTITUTION, U.STATUS = S.STATUS
                WHEN NOT MATCHED THEN
                  INSERT (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS, CREATED_AT)
                  VALUES (S.USER_ID, S.USERNAME, S.PASSWORD_HASH, S.REAL_NAME, S.EMAIL, S.INSTITUTION, S.STATUS, CURRENT_TIMESTAMP)
                """
        );
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT 1112 AS USER_ROLE_ID, 1012 AS USER_ID, 2 AS ROLE_ID FROM DUAL) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (S.USER_ROLE_ID, S.USER_ID, S.ROLE_ID)
                """
        );
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

    private record TestManuscript(long manuscriptId, long versionId) {
    }
}
