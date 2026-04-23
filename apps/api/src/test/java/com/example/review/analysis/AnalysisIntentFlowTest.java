package com.example.review.analysis;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Assertions;
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
class AnalysisIntentFlowTest {
    private static final byte[] PDF_BYTES = "%PDF-1.4\n% test pdf\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanWorkflowTables() {
        LegacyAgentArtifactsCleanup.deleteLegacyAgentArtifacts(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM ANALYSIS_OUTBOX");
        jdbcTemplate.update("DELETE FROM ANALYSIS_PROJECTION");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INTENT");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
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
    }

    @Test
    void reviewerAssistRequestCreatesIntentInsteadOfPollingTask() throws Exception {
        ManuscriptFixture fixture = seedUnderReviewManuscriptWithReviewer();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/agent-assist", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.intentId").isNumber())
                .andExpect(jsonPath("$.analysisType").value("REVIEWER_ASSIST"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.taskStatus").doesNotExist());

        Integer intentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ANALYSIS_INTENT", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ANALYSIS_OUTBOX", Integer.class);
        String messagePayload = jdbcTemplate.queryForObject("SELECT MESSAGE_PAYLOAD FROM ANALYSIS_OUTBOX", String.class);
        JsonNode message = objectMapper.readTree(messagePayload);

        Assertions.assertEquals(1, intentCount);
        Assertions.assertEquals(1, outboxCount);
        Assertions.assertEquals("REVIEWER_ASSIST", message.get("analysisType").asText());
        Assertions.assertTrue(message.hasNonNull("idempotencyKey"));
        Assertions.assertEquals(fixture.assignmentId(), message.at("/requestPayload/reviewerAssist/assignmentId").asLong());
        Assertions.assertEquals("checklist_only", message.at("/requestPayload/reviewerAssist/allowedOutput").asText());
    }

    private ManuscriptFixture seedUnderReviewManuscriptWithReviewer() {
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
                INSERT INTO MANUSCRIPT_VERSION (
                  VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE, SUBMITTED_BY, SUBMITTED_AT
                ) VALUES (?, ?, 1, 'INITIAL', 'Workflow Seed', 'workflow abstract', 'workflow,pdf', ?, 'workflow.pdf', ?, 1001, ?)
                """,
                versionId,
                manuscriptId,
                PDF_BYTES,
                PDF_BYTES.length,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT)
                VALUES (?, ?, 1, ?, 'PENDING', 'REALLOCATE_REVIEWERS', 1, ?, 1003, ?)
                """,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT, DEADLINE_AT)
                VALUES (?, ?, ?, ?, 1002, 'ACCEPTED', ?, ?)
                """,
                assignmentId,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS))
        );
        return new ManuscriptFixture(manuscriptId, versionId, assignmentId);
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private record ManuscriptFixture(long manuscriptId, long versionId, long assignmentId) {
    }
}
