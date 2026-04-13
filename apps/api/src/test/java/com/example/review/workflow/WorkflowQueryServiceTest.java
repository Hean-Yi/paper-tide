package com.example.review.workflow;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class WorkflowQueryServiceTest {
    private static final byte[] PDF_BYTES = "%PDF-1.4\n% workflow pdf\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWorkflowTables() {
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_RESULT");
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_TASK");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM SYS_NOTIFICATION");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM AUDIT_LOG");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
    }

    @Test
    void reviewerListsOwnAssignmentsAndDownloadsAssignedPdf() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("ACCEPTED", true, false);
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/review-assignments")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(fixture.assignmentId()))
                .andExpect(jsonPath("$[0].title").value("Workflow Seed"))
                .andExpect(jsonPath("$[0].taskStatus").value("ACCEPTED"));

        mockMvc.perform(get("/api/review-assignments/{assignmentId}", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abstractText").value("workflow abstract"))
                .andExpect(jsonPath("$.pdfFileName").value("workflow.pdf"));

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/pdf", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    void chairListsScreeningQueueStartsScreeningAndDownloadsPdf() throws Exception {
        WorkflowFixture fixture = seedSubmittedManuscript("SUBMITTED", true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manuscriptId").value(fixture.manuscriptId()))
                .andExpect(jsonPath("$[0].title").value("Workflow Seed"))
                .andExpect(jsonPath("$[0].currentStatus").value("SUBMITTED"));

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/start-screening", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("UNDER_SCREENING"));

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/pdf", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    void chairDecisionWorkbenchIncludesAssignmentAndConflictCounts() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("SUBMITTED", true, true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/chair/decision-workbench")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roundId").value(fixture.roundId()))
                .andExpect(jsonPath("$[0].assignmentCount").value(1))
                .andExpect(jsonPath("$[0].submittedReviewCount").value(1))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].assignments[0].assignmentId").value(fixture.assignmentId()))
                .andExpect(jsonPath("$[0].assignments[0].taskStatus").value("SUBMITTED"));
    }

    @Test
    void adminListsAgentTasksWithFilters() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("ACCEPTED", true, false);
        long taskId = seedAgentTask(fixture);
        String adminToken = loginAndExtractToken("admin_demo", "demo123");

        mockMvc.perform(get("/api/agent-tasks")
                        .param("status", "SUCCESS")
                        .param("taskType", "REVIEW_ASSIST_ANALYSIS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId))
                .andExpect(jsonPath("$[0].externalTaskId").value("workflow-agent-task"))
                .andExpect(jsonPath("$[0].taskStatus").value("SUCCESS"))
                .andExpect(jsonPath("$[0].resultSummary").value("finished"));
    }

    private WorkflowFixture seedSubmittedManuscript(String status, boolean withPdf) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, ?, 0, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                status,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Workflow Seed', 'workflow abstract', 'workflow,pdf', ?, ?, ?, 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                withPdf ? PDF_BYTES : null,
                withPdf ? "workflow.pdf" : null,
                withPdf ? PDF_BYTES.length : null,
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
        return new WorkflowFixture(manuscriptId, versionId, null, null);
    }

    private WorkflowFixture seedUnderReviewWorkflow(String assignmentStatus, boolean withPdf, boolean withReportAndConflict) {
        WorkflowFixture manuscript = seedSubmittedManuscript("UNDER_REVIEW", withPdf);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        long assignmentId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_ROUND_NO = 1 WHERE MANUSCRIPT_ID = ?", manuscript.manuscriptId());
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT)
                VALUES (?, ?, 1, ?, 'IN_PROGRESS', 'REALLOCATE_REVIEWERS', 1, ?, 1003, ?)
                """,
                roundId,
                manuscript.manuscriptId(),
                manuscript.versionId(),
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT, ACCEPTED_AT, DEADLINE_AT, SUBMITTED_AT)
                VALUES (?, ?, ?, ?, 1002, ?, ?, ?, ?, ?)
                """,
                assignmentId,
                roundId,
                manuscript.manuscriptId(),
                manuscript.versionId(),
                assignmentStatus,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)),
                "SUBMITTED".equals(assignmentStatus) ? Timestamp.from(Instant.now()) : null
        );
        if (withReportAndConflict) {
            jdbcTemplate.update(
                    """
                    INSERT INTO REVIEW_REPORT (
                      REVIEW_ID, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID, NOVELTY_SCORE, METHOD_SCORE, EXPERIMENT_SCORE, WRITING_SCORE, OVERALL_SCORE,
                      CONFIDENCE_LEVEL, STRENGTHS, WEAKNESSES, COMMENTS_TO_AUTHOR, COMMENTS_TO_CHAIR, RECOMMENDATION, SUBMITTED_AT
                    ) VALUES (
                      SEQ_REVIEW_REPORT.NEXTVAL, ?, ?, ?, 1002, 4, 4, 4, 4, 4,
                      'HIGH', 'clear contribution', 'limited scope', 'good paper', 'ready for decision', 'MINOR_REVISION', ?
                    )
                    """,
                    assignmentId,
                    roundId,
                    manuscript.manuscriptId(),
                    Timestamp.from(Instant.now())
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO CONFLICT_CHECK_RECORD (CONFLICT_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REVIEWER_ID, CONFLICT_TYPE, CONFLICT_DESC, SOURCE, DETECTED_AT)
                    VALUES (SEQ_CONFLICT_CHECK_RECORD.NEXTVAL, ?, ?, 1002, 'INSTITUTION', 'same institution', 'SYSTEM_DETECTED', ?)
                    """,
                    assignmentId,
                    manuscript.manuscriptId(),
                    Timestamp.from(Instant.now())
            );
        }
        return new WorkflowFixture(manuscript.manuscriptId(), manuscript.versionId(), roundId, assignmentId);
    }

    private long seedAgentTask(WorkflowFixture fixture) {
        long taskId = jdbcTemplate.queryForObject("SELECT SEQ_AGENT_ANALYSIS_TASK.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO AGENT_ANALYSIS_TASK (TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, REQUEST_PAYLOAD, RESULT_SUMMARY, EXTERNAL_TASK_ID, RETRY_COUNT, CREATED_AT, FINISHED_AT)
                VALUES (?, ?, ?, ?, 'REVIEW_ASSIST_ANALYSIS', 'SUCCESS', '{}', 'finished', 'workflow-agent-task', 0, ?, ?)
                """,
                taskId,
                fixture.manuscriptId(),
                fixture.versionId(),
                fixture.roundId(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
        return taskId;
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
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private record WorkflowFixture(long manuscriptId, long versionId, Long roundId, Long assignmentId) {
    }
}
