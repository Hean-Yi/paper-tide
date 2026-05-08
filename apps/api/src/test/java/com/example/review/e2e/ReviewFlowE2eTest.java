package com.example.review.e2e;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewFlowE2eTest {
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanWorkflowTables() {
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
        jdbcTemplate.update("DELETE FROM REVIEWER_BID");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_DRAFT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_REVIEWER");
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
    void completeReviewFlowPersistsDecisionAndProtectsAgentVisibility() throws Exception {
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String adminToken = loginAndExtractToken("admin_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        long conferenceId = seedOpenConference();
        ManuscriptIds manuscript = createManuscript(authorToken, conferenceId);
        uploadPdf(authorToken, manuscript);
        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", manuscript.manuscriptId(), manuscript.versionId())
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"));

        addReviewerToPool(chairToken, conferenceId);
        openBidding(conferenceId);
        submitBid(reviewerToken, conferenceId, manuscript.manuscriptId());

        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manuscriptId").value(manuscript.manuscriptId()));
        mockMvc.perform(get("/api/chair/screening-queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/start-screening", manuscript.manuscriptId(), manuscript.versionId())
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("UNDER_SCREENING"));

        long roundId = createRound(chairToken, manuscript);
        mockMvc.perform(get("/api/chair/decision-workbench")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roundId").value(roundId));

        generateAssignmentDrafts(chairToken, roundId);
        requestAssignmentAssist(chairToken, roundId);
        long assignmentId = confirmAssignmentDrafts(chairToken, roundId);
        mockMvc.perform(post("/api/review-assignments/{assignmentId}/accept", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("ACCEPTED"));

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/pdf", manuscript.manuscriptId(), manuscript.versionId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.manuscriptId").value(manuscript.manuscriptId()))
                .andExpect(jsonPath("$.versionId").value(manuscript.versionId()))
                .andExpect(jsonPath("$.downloadAllowed").value(false));

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/paper/pages/1", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        requestReviewerAssist(reviewerToken, assignmentId);

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/agent-assist", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.analysisType").value("REVIEWER_ASSIST"))
                .andExpect(jsonPath("$.intent.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.projections").isArray())
                .andExpect(jsonPath("$.task").doesNotExist());

        submitReviewReport(reviewerToken, assignmentId);

        triggerConflictAnalysis(chairToken, roundId);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ANALYSIS_OUTBOX", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, outboxCount);

        mockMvc.perform(post("/api/decisions")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "roundId": %d,
                                  "versionId": %d,
                                  "decisionCode": "MINOR_REVISION",
                                  "decisionReason": "Promising paper with a small required revision."
                                }
                                """.formatted(manuscript.manuscriptId(), roundId, manuscript.versionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("REVISION_REQUIRED"))
                .andExpect(jsonPath("$.roundStatus").value("COMPLETED"));

        mockMvc.perform(get("/api/manuscripts")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manuscriptId").value(manuscript.manuscriptId()))
                .andExpect(jsonPath("$[0].currentStatus").value("REVISION_REQUIRED"))
                .andExpect(jsonPath("$[0].lastDecisionCode").value("MINOR_REVISION"));
    }

    private ManuscriptIds createManuscript(String authorToken, long conferenceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conferenceId", conferenceId,
                                "title", "Robust Review Systems",
                                "abstract", "A paper about resilient manuscript review workflows.",
                                "keywords", "review,workflow,agent",
                                "blindMode", "DOUBLE_BLIND",
                                "authors", List.of(
                                        Map.of(
                                                "authorName", "Author Demo",
                                                "email", "author_demo@example.com",
                                                "institution", "Southeast University",
                                                "authorOrder", 1,
                                                "userId", 1001,
                                                "isCorresponding", true,
                                                "isExternal", false
                                        ),
                                        Map.of(
                                                "authorName", "External Collaborator",
                                                "email", "external@example.com",
                                                "institution", "Zhejiang University",
                                                "authorOrder", 2,
                                                "isCorresponding", false,
                                                "isExternal", true
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new ManuscriptIds(root.path("manuscriptId").asLong(), root.path("currentVersionId").asLong());
    }

    private void uploadPdf(String authorToken, ManuscriptIds manuscript) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "robust-review-systems.pdf", "application/pdf", PDF_BYTES);
        mockMvc.perform(multipart("/api/manuscripts/{id}/versions/{versionId}/pdf", manuscript.manuscriptId(), manuscript.versionId())
                        .file(file)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
    }

    private long createRound(String chairToken, ManuscriptIds manuscript) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/review-rounds")
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "versionId": %d,
                                  "assignmentStrategy": "REALLOCATE_REVIEWERS",
                                  "screeningRequired": true,
                                  "deadlineAt": "2099-05-01T12:00:00Z"
                                }
                                """.formatted(manuscript.manuscriptId(), manuscript.versionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundStatus").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("roundId").asLong();
    }

    private void generateAssignmentDrafts(String chairToken, long roundId) throws Exception {
        mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/generate", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "limit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewerId").value(1002))
                .andExpect(jsonPath("$[0].draftStatus").value("PROPOSED"));
    }

    private long confirmAssignmentDrafts(String chairToken, long roundId) throws Exception {
        Long draftId = jdbcTemplate.queryForObject(
                "SELECT ASSIGNMENT_DRAFT_ID FROM ASSIGNMENT_DRAFT WHERE ROUND_ID = ? AND REVIEWER_ID = 1002",
                Long.class,
                roundId
        );
        MvcResult result = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/confirm", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "draftIds": [%d],
                                  "deadlineAt": "2099-05-01T12:00:00Z"
                                }
                                """.formatted(draftId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskStatus").value("ASSIGNED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(0).path("assignmentId").asLong();
    }

    private void requestReviewerAssist(String reviewerToken, long assignmentId) throws Exception {
        mockMvc.perform(post("/api/review-assignments/{assignmentId}/agent-assist", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisType").value("REVIEWER_ASSIST"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.externalTaskId").doesNotExist());
    }

    private void triggerConflictAnalysis(String chairToken, long roundId) throws Exception {
        mockMvc.perform(post("/api/review-rounds/{roundId}/conflict-analysis", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisType").value("CONFLICT_ANALYSIS"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.externalTaskId").doesNotExist());
    }

    private void requestAssignmentAssist(String chairToken, long roundId) throws Exception {
        mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-assist", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisType").value("REVIEWER_ASSIGNMENT_ASSIST"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"));

        mockMvc.perform(get("/api/review-rounds/{roundId}/assignment-assist", roundId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.analysisType").value("REVIEWER_ASSIGNMENT_ASSIST"));
    }

    private void addReviewerToPool(String chairToken, long conferenceId) throws Exception {
        mockMvc.perform(post("/api/chair/conferences/{conferenceId}/reviewers", conferenceId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1002,
                                  "maxLoad": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerId").value(1002));
    }

    private void submitBid(String reviewerToken, long conferenceId, long manuscriptId) throws Exception {
        mockMvc.perform(get("/api/reviewer/conferences/{conferenceId}/bids/open", conferenceId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manuscriptId").value(manuscriptId));

        mockMvc.perform(post("/api/reviewer/conferences/{conferenceId}/bids", conferenceId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "manuscriptId": %d,
                                  "bidValue": "WANT_TO_REVIEW",
                                  "conflictDeclared": false
                                }
                                """.formatted(manuscriptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidValue").value("WANT_TO_REVIEW"));
    }

    private void submitReviewReport(String reviewerToken, long assignmentId) throws Exception {
        mockMvc.perform(post("/api/review-assignments/{assignmentId}/review-report", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "noveltyScore": 4,
                                  "methodScore": 4,
                                  "experimentScore": 3,
                                  "writingScore": 4,
                                  "overallScore": 4,
                                  "confidenceLevel": "HIGH",
                                  "strengths": "Clear contribution and well-scoped system.",
                                  "weaknesses": "Needs a broader evaluation section.",
                                  "commentsToAuthor": "Please add one more evaluation scenario.",
                                  "commentsToChair": "Minor revision is appropriate.",
                                  "recommendation": "MINOR_REVISION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("SUBMITTED"));
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private long seedOpenConference() {
        long conferenceId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE.NEXTVAL FROM DUAL", Long.class);
        Instant submissionCloseAt = Instant.now().plusSeconds(30 * 24 * 60 * 60);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE (
                  CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID, CONFERENCE_STATUS,
                  BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON, TARGET_REVIEWS_PER_PAPER,
                  DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED, APPROVED_BY, APPROVED_AT
                ) VALUES (?, ?, ?, ?, 1003, 'OPEN_FOR_SUBMISSION', 'DOUBLE_BLIND', ?, '[\"review\",\"agent\"]',
                          2, 3, ?, 1, 1004, CURRENT_TIMESTAMP)
                """,
                conferenceId,
                "E2E Conference " + conferenceId,
                "E2E" + conferenceId,
                2026,
                "E2E CFP",
                "e2e-" + conferenceId
        );
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_PHASE (
                  PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, SUBMISSION_CLOSE_AT, BIDDING_OPEN_AT,
                  BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                ) VALUES (SEQ_CONFERENCE_PHASE.NEXTVAL, ?, ?, ?, ?, ?, ?, ?)
                """,
                conferenceId,
                Timestamp.from(submissionCloseAt.minusSeconds(30 * 24 * 60 * 60)),
                Timestamp.from(submissionCloseAt),
                Timestamp.from(submissionCloseAt.plusSeconds(24 * 60 * 60)),
                Timestamp.from(submissionCloseAt.plusSeconds(7 * 24 * 60 * 60)),
                Timestamp.from(submissionCloseAt.plusSeconds(14 * 24 * 60 * 60)),
                Timestamp.from(submissionCloseAt.plusSeconds(21 * 24 * 60 * 60))
        );
        return conferenceId;
    }

    private void openBidding(long conferenceId) {
        jdbcTemplate.update(
                "UPDATE CONFERENCE SET CONFERENCE_STATUS = 'BIDDING_OPEN' WHERE CONFERENCE_ID = ?",
                conferenceId
        );
    }

    private record ManuscriptIds(long manuscriptId, long versionId) {
    }
}
