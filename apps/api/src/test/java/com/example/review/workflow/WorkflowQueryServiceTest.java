package com.example.review.workflow;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private static final byte[] PDF_BYTES = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R] /Count 1 >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
            endobj
            4 0 obj
            << /Length 171 >>
            stream
            BT
            /F1 12 Tf
            72 720 Td
            (Introduction This paper studies robust review systems.) Tj
            72 700 Td
            (Method We use deterministic parsing.) Tj
            72 680 Td
            (Experiment Results show stable behavior.) Tj
            72 660 Td
            (Conclusion The approach is practical.) Tj
            ET
            endstream
            endobj
            5 0 obj
            << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
            endobj
            xref
            0 6
            0000000000 65535 f
            0000000009 00000 n
            0000000058 00000 n
            0000000115 00000 n
            0000000241 00000 n
            0000000464 00000 n
            trailer
            << /Root 1 0 R /Size 6 >>
            startxref
            534
            %%EOF
            """.getBytes(StandardCharsets.UTF_8);

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
    void reviewerListsOwnAssignmentsAndReadsAcceptedPaperOnlineOnly() throws Exception {
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
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(fixture.assignmentId()))
                .andExpect(jsonPath("$.manuscriptId").value(fixture.manuscriptId()))
                .andExpect(jsonPath("$.versionId").value(fixture.versionId()))
                .andExpect(jsonPath("$.title").value("Workflow Seed"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.downloadAllowed").value(false));

        MvcResult page = mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper/pages/1", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        byte[] image = page.getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0x89, image[0]);
        org.junit.jupiter.api.Assertions.assertEquals((byte) 'P', image[1]);
        org.junit.jupiter.api.Assertions.assertEquals((byte) 'N', image[2]);
        org.junit.jupiter.api.Assertions.assertEquals((byte) 'G', image[3]);
    }

    @Test
    void reviewerMustAcceptAssignmentBeforeRenderedPaperPagesAreAvailable() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("ASSIGNED", true, false);
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(1));

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper/pages/1", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassignedReviewerCannotReadPaperMetadataOrPages() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("ACCEPTED", true, false);
        ensureSecondReviewer();
        String otherReviewerToken = loginAndExtractToken("second_reviewer_demo", "demo123");

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper", fixture.assignmentId())
                        .header("Authorization", "Bearer " + otherReviewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper/pages/1", fixture.assignmentId())
                        .header("Authorization", "Bearer " + otherReviewerToken))
                .andExpect(status().isForbidden());
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

    private void ensureSecondReviewer() {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER U
                USING (
                  SELECT 1013 AS USER_ID, 'second_reviewer_demo' AS USERNAME, '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq' AS PASSWORD_HASH,
                         'Second Reviewer Demo' AS REAL_NAME, 'second_reviewer_demo@example.com' AS EMAIL, 'Reviewer University' AS INSTITUTION, 'ACTIVE' AS STATUS
                  FROM DUAL
                ) S
                ON (U.USER_ID = S.USER_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS)
                  VALUES (S.USER_ID, S.USERNAME, S.PASSWORD_HASH, S.REAL_NAME, S.EMAIL, S.INSTITUTION, S.STATUS)
                """);
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT 1013 AS USER_ID, 2 AS ROLE_ID FROM DUAL) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """);
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
