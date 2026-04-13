package com.example.review.agent;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.review.agent.AgentDtos.AgentServiceCreateRequest;
import com.example.review.agent.AgentDtos.AgentServiceResult;
import com.example.review.agent.AgentDtos.AgentServiceTaskStatus;
import com.example.review.agent.AgentDtos.AgentServiceTaskSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AgentIntegrationServiceTest {
    private static final byte[] PDF_BYTES = "%PDF-1.4\n% test pdf\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RecordingAgentServiceClient agentClient;

    @BeforeEach
    void cleanAgentTables() {
        agentClient.reset();
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_RESULT");
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_TASK");
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
    void reviewAssistTaskUploadsPdfAndStoresPendingTask() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/agent-tasks", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "REVIEW_ASSIST_ANALYSIS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isNumber())
                .andExpect(jsonPath("$.externalTaskId").value("external-task-1"))
                .andExpect(jsonPath("$.taskStatus").value("PENDING"));

        Assertions.assertEquals(1, agentClient.createRequests.get());
        Assertions.assertEquals("REVIEW_ASSIST_ANALYSIS", agentClient.lastCreateRequest.taskType());
        Assertions.assertEquals(fixture.manuscriptId(), agentClient.lastCreateRequest.manuscriptId());
        Assertions.assertEquals(fixture.versionId(), agentClient.lastCreateRequest.versionId());
        Assertions.assertArrayEquals(PDF_BYTES, agentClient.lastCreateRequest.pdfBytes());

        Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                "SELECT TASK_TYPE, TASK_STATUS, EXTERNAL_TASK_ID FROM AGENT_ANALYSIS_TASK WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ?",
                fixture.manuscriptId(),
                fixture.versionId()
        );
        Assertions.assertEquals("REVIEW_ASSIST_ANALYSIS", taskRow.get("TASK_TYPE"));
        Assertions.assertEquals("PENDING", taskRow.get("TASK_STATUS"));
        Assertions.assertEquals("external-task-1", taskRow.get("EXTERNAL_TASK_ID"));
    }

    @Test
    void agentTaskCreationFailsWhenPdfMissing() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(false);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/agent-tasks", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "SCREENING_ANALYSIS"
                                }
                                """))
                .andExpect(status().isBadRequest());

        Integer taskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM AGENT_ANALYSIS_TASK", Integer.class);
        Assertions.assertEquals(0, taskCount);
        Assertions.assertEquals(0, agentClient.createRequests.get());
    }

    @Test
    void pollingCompletedTaskPersistsRawAndRedactedResults() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        long taskId = seedAgentTask(fixture, "external-success", "REVIEW_ASSIST_ANALYSIS", "PENDING", Instant.now());
        agentClient.completedTaskIds.add("external-success");

        pollOnce();

        Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, FINISHED_AT FROM AGENT_ANALYSIS_TASK WHERE TASK_ID = ?",
                taskId
        );
        Map<String, Object> resultRow = jdbcTemplate.queryForMap(
                "SELECT RESULT_TYPE, RAW_RESULT_JSON, REDACTED_RESULT_JSON FROM AGENT_ANALYSIS_RESULT WHERE TASK_ID = ?",
                taskId
        );

        Assertions.assertEquals("SUCCESS", taskRow.get("TASK_STATUS"));
        Assertions.assertNotNull(taskRow.get("FINISHED_AT"));
        Assertions.assertEquals("REVIEW_ASSIST_ANALYSIS", resultRow.get("RESULT_TYPE"));
        Assertions.assertTrue(resultRow.get("RAW_RESULT_JSON").toString().contains("raw summary"));
        Assertions.assertTrue(resultRow.get("REDACTED_RESULT_JSON").toString().contains("redacted summary"));

        pollOnce();
        Integer resultCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AGENT_ANALYSIS_RESULT WHERE TASK_ID = ?",
                Integer.class,
                taskId
        );
        Assertions.assertEquals(1, resultCount);
    }

    @Test
    void pollingTimedOutTaskMarksFailed() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        long taskId = seedAgentTask(
                fixture,
                "external-timeout",
                "SCREENING_ANALYSIS",
                "PENDING",
                Instant.now().minus(11, ChronoUnit.MINUTES)
        );

        pollOnce();

        Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                "SELECT TASK_STATUS, RESULT_SUMMARY FROM AGENT_ANALYSIS_TASK WHERE TASK_ID = ?",
                taskId
        );
        Assertions.assertEquals("FAILED", taskRow.get("TASK_STATUS"));
        Assertions.assertTrue(taskRow.get("RESULT_SUMMARY").toString().contains("timed out"));
        Assertions.assertEquals(0, agentClient.statusRequests.get());
    }

    @Test
    void reviewerGetsOnlyRedactedResult() throws Exception {
        ManuscriptFixture fixture = seedUnderReviewManuscriptWithReviewer();
        long taskId = seedAgentTask(fixture, "external-success", "REVIEW_ASSIST_ANALYSIS", "SUCCESS", Instant.now());
        seedAgentResult(taskId, fixture, "REVIEW_ASSIST_ANALYSIS");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/agent-results", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resultType").value("REVIEW_ASSIST_ANALYSIS"))
                .andExpect(jsonPath("$[0].redactedResult.summary").value("redacted summary"))
                .andExpect(jsonPath("$[0].rawResult").doesNotExist());
    }

    @Test
    void externalTaskIdWriteIsIdempotentForRepeatedRetry() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        agentClient.fixedCreateTaskId = "same-external-task";

        MvcResult first = mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/agent-tasks", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "SCREENING_ANALYSIS"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/agent-tasks", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "SCREENING_ANALYSIS",
                                  "force": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long firstTaskId = objectMapper.readTree(first.getResponse().getContentAsString()).get("taskId").asLong();
        long secondTaskId = objectMapper.readTree(second.getResponse().getContentAsString()).get("taskId").asLong();
        Assertions.assertEquals(firstTaskId, secondTaskId);
        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AGENT_ANALYSIS_TASK WHERE EXTERNAL_TASK_ID = 'same-external-task'",
                Integer.class
        );
        Assertions.assertEquals(1, taskCount);
    }

    @Test
    void failedTaskIsReusedWhenForceIsFalse() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        long failedTaskId = seedAgentTask(fixture, "external-failed", "SCREENING_ANALYSIS", "FAILED", Instant.now());

        MvcResult result = mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/agent-tasks", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "SCREENING_ANALYSIS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("FAILED"))
                .andReturn();

        long returnedTaskId = objectMapper.readTree(result.getResponse().getContentAsString()).get("taskId").asLong();
        Assertions.assertEquals(failedTaskId, returnedTaskId);
        Assertions.assertEquals(0, agentClient.createRequests.get());
    }

    @Test
    void conflictAnalysisPayloadIncludesReviewReports() throws Exception {
        ManuscriptFixture fixture = seedUnderReviewManuscriptWithSubmittedReports();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/review-rounds/{roundId}/conflict-analysis", fixture.roundId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalTaskId").value("external-task-1"));

        Assertions.assertEquals("DECISION_CONFLICT_ANALYSIS", agentClient.lastCreateRequest.taskType());
        Assertions.assertTrue(agentClient.lastCreateRequest.requestPayload().containsKey("reviewReports"));
        Assertions.assertTrue(agentClient.lastCreateRequest.requestPayload().get("reviewReports").toString().contains("MINOR_REVISION"));
    }

    private ManuscriptFixture seedSubmittedManuscript(boolean withPdf) {
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
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Agent Seed', 'seed abstract', 'agent,pdf', ?, ?, ?, 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                withPdf ? PDF_BYTES : null,
                withPdf ? "agent-seed.pdf" : null,
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
        return new ManuscriptFixture(manuscriptId, versionId, null);
    }

    private ManuscriptFixture seedUnderReviewManuscriptWithReviewer() {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        long assignmentId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_STATUS = 'UNDER_REVIEW', CURRENT_ROUND_NO = 1 WHERE MANUSCRIPT_ID = ?", fixture.manuscriptId());
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT)
                VALUES (?, ?, 1, ?, 'PENDING', 'REALLOCATE_REVIEWERS', 1, ?, 1003, ?)
                """,
                roundId,
                fixture.manuscriptId(),
                fixture.versionId(),
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
                fixture.manuscriptId(),
                fixture.versionId(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS))
        );
        return new ManuscriptFixture(fixture.manuscriptId(), fixture.versionId(), roundId);
    }

    private ManuscriptFixture seedUnderReviewManuscriptWithSubmittedReports() {
        ManuscriptFixture fixture = seedUnderReviewManuscriptWithReviewer();
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_REPORT (
                  REVIEW_ID, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID, NOVELTY_SCORE, METHOD_SCORE, EXPERIMENT_SCORE, WRITING_SCORE, OVERALL_SCORE,
                  CONFIDENCE_LEVEL, STRENGTHS, WEAKNESSES, COMMENTS_TO_AUTHOR, COMMENTS_TO_CHAIR, RECOMMENDATION, SUBMITTED_AT
                )
                SELECT SEQ_REVIEW_REPORT.NEXTVAL, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID, 4, 4, 3, 4, 4,
                       'HIGH', 'strong contribution', 'needs more evaluation', 'please expand evaluation', 'borderline but promising', 'MINOR_REVISION', ?
                FROM REVIEW_ASSIGNMENT
                WHERE ROUND_ID = ?
                """,
                Timestamp.from(Instant.now()),
                fixture.roundId()
        );
        return fixture;
    }

    private long seedAgentTask(ManuscriptFixture fixture, String externalTaskId, String taskType, String status, Instant createdAt) {
        long taskId = jdbcTemplate.queryForObject("SELECT SEQ_AGENT_ANALYSIS_TASK.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO AGENT_ANALYSIS_TASK (TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, REQUEST_PAYLOAD, RESULT_SUMMARY, EXTERNAL_TASK_ID, RETRY_COUNT, CREATED_AT, FINISHED_AT)
                VALUES (?, ?, ?, ?, ?, ?, '{}', NULL, ?, 0, ?, NULL)
                """,
                taskId,
                fixture.manuscriptId(),
                fixture.versionId(),
                fixture.roundId(),
                taskType,
                status,
                externalTaskId,
                Timestamp.from(createdAt)
        );
        return taskId;
    }

    private void seedAgentResult(long taskId, ManuscriptFixture fixture, String resultType) {
        jdbcTemplate.update(
                """
                INSERT INTO AGENT_ANALYSIS_RESULT (RESULT_ID, TASK_ID, MANUSCRIPT_ID, VERSION_ID, RESULT_TYPE, RAW_RESULT_JSON, REDACTED_RESULT_JSON, CREATED_AT)
                VALUES (SEQ_AGENT_ANALYSIS_RESULT.NEXTVAL, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId,
                fixture.manuscriptId(),
                fixture.versionId(),
                resultType,
                "{\"summary\":\"raw summary\"}",
                "{\"summary\":\"redacted summary\"}",
                Timestamp.from(Instant.now())
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
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void pollOnce() throws Exception {
        Object scheduler = applicationContext.getBean("agentPollingScheduler");
        scheduler.getClass().getMethod("pollOnce").invoke(scheduler);
    }

    private record ManuscriptFixture(long manuscriptId, long versionId, Long roundId) {
    }

    @TestConfiguration
    static class AgentClientTestConfiguration {
        @Bean
        @Primary
        RecordingAgentServiceClient recordingAgentServiceClient() {
            return new RecordingAgentServiceClient();
        }
    }

    static final class RecordingAgentServiceClient implements AgentServiceClient {
        private final AtomicInteger taskCounter = new AtomicInteger(0);
        private final AtomicInteger createRequests = new AtomicInteger(0);
        private final AtomicInteger statusRequests = new AtomicInteger(0);
        private final List<String> completedTaskIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile AgentServiceCreateRequest lastCreateRequest;
        private volatile String fixedCreateTaskId;

        void reset() {
            taskCounter.set(0);
            createRequests.set(0);
            statusRequests.set(0);
            completedTaskIds.clear();
            lastCreateRequest = null;
            fixedCreateTaskId = null;
        }

        @Override
        public AgentServiceTaskSummary createTask(AgentServiceCreateRequest request) {
            createRequests.incrementAndGet();
            lastCreateRequest = request;
            String taskId = fixedCreateTaskId == null ? "external-task-" + taskCounter.incrementAndGet() : fixedCreateTaskId;
            return new AgentServiceTaskSummary(taskId, "PENDING", "queued");
        }

        @Override
        public AgentServiceTaskStatus getTaskStatus(String externalTaskId) {
            statusRequests.incrementAndGet();
            if (completedTaskIds.contains(externalTaskId)) {
                return new AgentServiceTaskStatus(externalTaskId, "REVIEW_ASSIST_ANALYSIS", "SUCCESS", "completed", null);
            }
            return new AgentServiceTaskStatus(externalTaskId, "REVIEW_ASSIST_ANALYSIS", "PROCESSING", "analyzing", null);
        }

        @Override
        public AgentServiceResult getTaskResult(String externalTaskId) {
            return new AgentServiceResult(
                    "REVIEW_ASSIST_ANALYSIS",
                    Map.of("summary", "raw summary", "confidence", 0.8),
                    Map.of("summary", "redacted summary", "confidence", 0.8)
            );
        }
    }
}
