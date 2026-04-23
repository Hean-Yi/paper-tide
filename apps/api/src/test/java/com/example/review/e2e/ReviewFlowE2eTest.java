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
import com.example.review.support.LegacyAgentArtifactsCleanup;

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
        LegacyAgentArtifactsCleanup.deleteLegacyAgentArtifacts(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM ANALYSIS_OUTBOX");
        jdbcTemplate.update("DELETE FROM ANALYSIS_PROJECTION");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INTENT");
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
    void completeReviewFlowPersistsDecisionAndProtectsAgentVisibility() throws Exception {
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String adminToken = loginAndExtractToken("admin_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        ManuscriptIds manuscript = createManuscript(authorToken);
        uploadPdf(authorToken, manuscript);
        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", manuscript.manuscriptId(), manuscript.versionId())
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"));

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

        long assignmentId = assignReviewer(chairToken, roundId);
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
        org.junit.jupiter.api.Assertions.assertEquals(2, outboxCount);

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

    private ManuscriptIds createManuscript(String authorToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
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
                                  "deadlineAt": "2026-05-01T12:00:00Z"
                                }
                                """.formatted(manuscript.manuscriptId(), manuscript.versionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundStatus").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("roundId").asLong();
    }

    private long assignReviewer(String chairToken, long roundId) throws Exception {
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
                .andExpect(jsonPath("$.taskStatus").value("ASSIGNED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("assignmentId").asLong();
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

    private record ManuscriptIds(long manuscriptId, long versionId) {
    }
}
