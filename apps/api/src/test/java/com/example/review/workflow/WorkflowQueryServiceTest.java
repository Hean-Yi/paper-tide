package com.example.review.workflow;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItem;
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
        ensureLegacyConference();
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

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/screening-analysis", fixture.manuscriptId(), fixture.versionId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisType").value("SCREENING"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"));

        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].screeningIntent.analysisType").value("SCREENING"))
                .andExpect(jsonPath("$[0].screeningIntent.businessStatus").value("REQUESTED"));

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
    void chairScreeningQueueExcludesOtherOrganizerConferenceManuscripts() throws Exception {
        WorkflowFixture ownFixture = seedSubmittedManuscript("SUBMITTED", true);
        long otherConferenceId = 991001L;
        ensureConference(otherConferenceId, 1004L, "Other Organizer Conference", "OT991001", "other-organizer-conference");
        WorkflowFixture otherFixture = seedSubmittedManuscript(
                "SUBMITTED",
                true,
                otherConferenceId,
                "Other Organizer Paper"
        );
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manuscriptId", hasItem((int) ownFixture.manuscriptId())))
                .andExpect(jsonPath("$[*].manuscriptId", not(hasItem((int) otherFixture.manuscriptId()))));
    }

    @Test
    void adminScreeningQueueKeepsExplicitGlobalScope() throws Exception {
        WorkflowFixture ownFixture = seedSubmittedManuscript("SUBMITTED", true);
        long otherConferenceId = 991002L;
        ensureConference(otherConferenceId, 1004L, "Admin Visible Other Conference", "OT991002", "admin-visible-other-conference");
        WorkflowFixture otherFixture = seedSubmittedManuscript(
                "SUBMITTED",
                true,
                otherConferenceId,
                "Admin Visible Other Paper"
        );
        String adminToken = loginAndExtractToken("admin_demo", "demo123");

        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manuscriptId", hasItem((int) ownFixture.manuscriptId())))
                .andExpect(jsonPath("$[*].manuscriptId", hasItem((int) otherFixture.manuscriptId())));
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
    void chairConferencePaperListIncludesReviewerScoresAndAverage() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("SUBMITTED", true, true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/chair/conferences/{conferenceId}/papers", 0)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manuscriptId").value(fixture.manuscriptId()))
                .andExpect(jsonPath("$[0].averageOverallScore").value(4.0))
                .andExpect(jsonPath("$[0].reviewerScores[0].assignmentId").value(fixture.assignmentId()))
                .andExpect(jsonPath("$[0].reviewerScores[0].reviewerId").value(1002))
                .andExpect(jsonPath("$[0].reviewerScores[0].reviewerName").value("Reviewer Demo"))
                .andExpect(jsonPath("$[0].reviewerScores[0].overallScore").value(4))
                .andExpect(jsonPath("$[0].reviewerScores[0].recommendation").value("MINOR_REVISION"));
    }

    @Test
    void chairReadsConferencePaperReviewDetailAndRenderedPages() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("SUBMITTED", true, true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/chair/conferences/{conferenceId}/papers/{manuscriptId}/review-detail", 0, fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manuscriptId").value(fixture.manuscriptId()))
                .andExpect(jsonPath("$.versionId").value(fixture.versionId()))
                .andExpect(jsonPath("$.title").value("Workflow Seed"))
                .andExpect(jsonPath("$.abstractText").value("workflow abstract"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.averageOverallScore").value(4.0))
                .andExpect(jsonPath("$.reviews[0].assignmentId").value(fixture.assignmentId()))
                .andExpect(jsonPath("$.reviews[0].reviewerName").value("Reviewer Demo"))
                .andExpect(jsonPath("$.reviews[0].noveltyScore").value(4))
                .andExpect(jsonPath("$.reviews[0].commentsToAuthor").value("good paper"))
                .andExpect(jsonPath("$.reviews[0].commentsToChair").value("ready for decision"));

        MvcResult page = mockMvc.perform(get("/api/chair/conferences/{conferenceId}/papers/{manuscriptId}/paper/pages/1", 0, fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken))
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
    void adminCannotReadConferencePaperContent() throws Exception {
        WorkflowFixture fixture = seedUnderReviewWorkflow("SUBMITTED", true, true);
        String adminToken = loginAndExtractToken("admin_demo", "demo123");

        mockMvc.perform(get("/api/chair/conferences/{conferenceId}/papers/{manuscriptId}/review-detail", 0, fixture.manuscriptId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/chair/conferences/{conferenceId}/papers/{manuscriptId}/paper/pages/1", 0, fixture.manuscriptId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorReviewerInterfaceChoicePromptsOnlyForOpenConferenceAssignments() throws Exception {
        seedUnderReviewWorkflow("ASSIGNED", true, false);
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT 1002 AS USER_ID, R.ROLE_ID FROM SYS_ROLE R WHERE R.ROLE_CODE = 'AUTHOR') S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """
        );
        String reviewerAuthorToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/reviewer/interface-choice")
                        .header("Authorization", "Bearer " + reviewerAuthorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldPrompt").value(true))
                .andExpect(jsonPath("$.activeAssignmentCount").value(1));

        jdbcTemplate.update("UPDATE CONFERENCE SET CONFERENCE_STATUS = 'CLOSED' WHERE CONFERENCE_ID = 0");

        mockMvc.perform(get("/api/reviewer/interface-choice")
                        .header("Authorization", "Bearer " + reviewerAuthorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldPrompt").value(false))
                .andExpect(jsonPath("$.activeAssignmentCount").value(0));
    }

    private WorkflowFixture seedSubmittedManuscript(String status, boolean withPdf) {
        return seedSubmittedManuscript(status, withPdf, 0L, "Workflow Seed");
    }

    private WorkflowFixture seedSubmittedManuscript(String status, boolean withPdf, long conferenceId, String title) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE, CONFERENCE_ID)
                VALUES (?, 1001, NULL, ?, 0, 'DOUBLE_BLIND', ?, NULL, ?)
                """,
                manuscriptId,
                status,
                Timestamp.from(Instant.now()),
                conferenceId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', ?, 'workflow abstract', 'workflow,pdf', ?, ?, ?, 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                title,
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

    private void ensureLegacyConference() {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE C
                USING (
                  SELECT 0 AS CONFERENCE_ID,
                         'Legacy / Platform Default' AS NAME,
                         'LEGACY' AS ACRONYM,
                         2026 AS CONFERENCE_YEAR,
                         1003 AS ORGANIZER_USER_ID,
                         'OPEN_FOR_SUBMISSION' AS CONFERENCE_STATUS,
                         'DOUBLE_BLIND' AS BLIND_MODE,
                         'Default conference for workflow tests.' AS CFP_TEXT,
                         '["LEGACY"]' AS TOPIC_AREAS_JSON,
                         3 AS TARGET_REVIEWS_PER_PAPER,
                         3 AS DEFAULT_REVIEWER_MAX_LOAD,
                         'legacy-platform-default' AS PUBLIC_SLUG,
                         0 AS CFP_PUBLISHED
                  FROM DUAL
                ) S
                ON (C.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN MATCHED THEN
                  UPDATE SET C.NAME = S.NAME,
                             C.ACRONYM = S.ACRONYM,
                             C.CONFERENCE_YEAR = S.CONFERENCE_YEAR,
                             C.ORGANIZER_USER_ID = S.ORGANIZER_USER_ID,
                             C.CONFERENCE_STATUS = S.CONFERENCE_STATUS,
                             C.BLIND_MODE = S.BLIND_MODE,
                             C.CFP_TEXT = S.CFP_TEXT,
                             C.TOPIC_AREAS_JSON = S.TOPIC_AREAS_JSON,
                             C.TARGET_REVIEWS_PER_PAPER = S.TARGET_REVIEWS_PER_PAPER,
                             C.DEFAULT_REVIEWER_MAX_LOAD = S.DEFAULT_REVIEWER_MAX_LOAD,
                             C.PUBLIC_SLUG = S.PUBLIC_SLUG,
                             C.CFP_PUBLISHED = S.CFP_PUBLISHED
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
                """);
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE_PHASE P
                USING (
                  SELECT 0 AS CONFERENCE_ID,
                         TIMESTAMP '2099-01-01 00:00:00' AS SUBMISSION_OPEN_AT,
                         TIMESTAMP '2099-02-01 00:00:00' AS ABSTRACT_SUBMISSION_CLOSE_AT,
                         TIMESTAMP '2099-03-01 00:00:00' AS SUBMISSION_CLOSE_AT,
                         TIMESTAMP '2099-04-01 00:00:00' AS BIDDING_OPEN_AT,
                         TIMESTAMP '2099-05-01 00:00:00' AS BIDDING_CLOSE_AT,
                         TIMESTAMP '2099-06-01 00:00:00' AS REVIEW_DEADLINE_AT,
                         TIMESTAMP '2099-07-01 00:00:00' AS DECISION_RELEASE_AT
                  FROM DUAL
                ) S
                ON (P.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN MATCHED THEN
                  UPDATE SET P.SUBMISSION_OPEN_AT = S.SUBMISSION_OPEN_AT,
                             P.ABSTRACT_SUBMISSION_CLOSE_AT = S.ABSTRACT_SUBMISSION_CLOSE_AT,
                             P.SUBMISSION_CLOSE_AT = S.SUBMISSION_CLOSE_AT,
                             P.BIDDING_OPEN_AT = S.BIDDING_OPEN_AT,
                             P.BIDDING_CLOSE_AT = S.BIDDING_CLOSE_AT,
                             P.REVIEW_DEADLINE_AT = S.REVIEW_DEADLINE_AT,
                             P.DECISION_RELEASE_AT = S.DECISION_RELEASE_AT
                WHEN NOT MATCHED THEN
                  INSERT (
                    PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, ABSTRACT_SUBMISSION_CLOSE_AT, SUBMISSION_CLOSE_AT,
                    BIDDING_OPEN_AT, BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                  )
                  VALUES (
                    SEQ_CONFERENCE_PHASE.NEXTVAL, S.CONFERENCE_ID, S.SUBMISSION_OPEN_AT, S.ABSTRACT_SUBMISSION_CLOSE_AT, S.SUBMISSION_CLOSE_AT,
                    S.BIDDING_OPEN_AT, S.BIDDING_CLOSE_AT, S.REVIEW_DEADLINE_AT, S.DECISION_RELEASE_AT
                  )
                """
        );
    }

    private void ensureConference(long conferenceId, long organizerUserId, String name, String acronym, String publicSlug) {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE C
                USING (
                  SELECT ? AS CONFERENCE_ID,
                         ? AS NAME,
                         ? AS ACRONYM,
                         2026 AS CONFERENCE_YEAR,
                         ? AS ORGANIZER_USER_ID,
                         'OPEN_FOR_SUBMISSION' AS CONFERENCE_STATUS,
                         'DOUBLE_BLIND' AS BLIND_MODE,
                         'Other conference for workflow scope tests.' AS CFP_TEXT,
                         '["SCOPE"]' AS TOPIC_AREAS_JSON,
                         3 AS TARGET_REVIEWS_PER_PAPER,
                         3 AS DEFAULT_REVIEWER_MAX_LOAD,
                         ? AS PUBLIC_SLUG,
                         0 AS CFP_PUBLISHED
                  FROM DUAL
                ) S
                ON (C.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN MATCHED THEN
                  UPDATE SET C.NAME = S.NAME,
                             C.ACRONYM = S.ACRONYM,
                             C.CONFERENCE_YEAR = S.CONFERENCE_YEAR,
                             C.ORGANIZER_USER_ID = S.ORGANIZER_USER_ID,
                             C.CONFERENCE_STATUS = S.CONFERENCE_STATUS,
                             C.BLIND_MODE = S.BLIND_MODE,
                             C.CFP_TEXT = S.CFP_TEXT,
                             C.TOPIC_AREAS_JSON = S.TOPIC_AREAS_JSON,
                             C.TARGET_REVIEWS_PER_PAPER = S.TARGET_REVIEWS_PER_PAPER,
                             C.DEFAULT_REVIEWER_MAX_LOAD = S.DEFAULT_REVIEWER_MAX_LOAD,
                             C.PUBLIC_SLUG = S.PUBLIC_SLUG,
                             C.CFP_PUBLISHED = S.CFP_PUBLISHED
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
                acronym,
                organizerUserId,
                publicSlug
        );
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
