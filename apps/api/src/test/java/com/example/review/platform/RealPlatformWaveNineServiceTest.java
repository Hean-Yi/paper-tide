package com.example.review.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
class RealPlatformWaveNineServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWaveNineTables() {
        jdbcTemplate.update("DELETE FROM PROCEEDINGS_EXPORT_BATCH");
        jdbcTemplate.update("DELETE FROM PUBLICATION_METADATA");
        jdbcTemplate.update("DELETE FROM CAMERA_READY_FILE");
        jdbcTemplate.update("DELETE FROM OFFLINE_REVIEW_IMPORT_ROW");
        jdbcTemplate.update("DELETE FROM OFFLINE_REVIEW_IMPORT_BATCH");
        jdbcTemplate.update("DELETE FROM OUTBOUND_EMAIL_HISTORY");
        jdbcTemplate.update("UPDATE EMAIL_TEMPLATE SET ACTIVE_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM EMAIL_TEMPLATE_VERSION");
        jdbcTemplate.update("DELETE FROM EMAIL_TEMPLATE");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_OVERRIDE_AUDIT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL_BUNDLE");
        jdbcTemplate.update("DELETE FROM REVIEWER_MATCHING_SCORE");
        jdbcTemplate.update("DELETE FROM CONFLICT_RELATIONSHIP");
        jdbcTemplate.update("DELETE FROM EXTERNAL_REVIEWER_DELEGATION");
        jdbcTemplate.update("DELETE FROM REVIEWER_INVITATION");
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
        jdbcTemplate.update("DELETE FROM CONFERENCE_REVIEWER");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        seedLegacyConference();
    }

    @Test
    void chairPreviewsEmailTemplateAndRecordsFakeSendHistory() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        MvcResult templateResult = mockMvc.perform(post("/api/conferences/{conferenceId}/email-templates", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "decision.accept",
                                  "subjectTemplate": "Decision for {{title}}",
                                  "bodyTemplate": "Dear {{authorName}}, decision is {{decisionCode}}."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value("decision.accept"))
                .andReturn();

        long templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString())
                .path("templateId")
                .asLong();

        mockMvc.perform(post("/api/email-templates/{templateId}/preview", templateId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": {
                                    "title": "Wave 9 Paper",
                                    "authorName": "Author Demo",
                                    "decisionCode": "ACCEPT"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Decision for Wave 9 Paper"))
                .andExpect(jsonPath("$.body").value("Dear Author Demo, decision is ACCEPT."));

        mockMvc.perform(post("/api/email-templates/{templateId}/test-send", templateId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientEmail": "author_demo@example.com",
                                  "variables": {
                                    "title": "Wave 9 Paper",
                                    "authorName": "Author Demo",
                                    "decisionCode": "ACCEPT"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("RECORDED"));
    }

    @Test
    void offlineReviewPreviewDoesNotMutateReportUntilConfirm() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/offline-review/template", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csvHeader").value("overallScore,recommendation,commentsToAuthor"));

        MvcResult previewResult = mockMvc.perform(post("/api/review-assignments/{assignmentId}/offline-review/preview", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "overallScore,recommendation,commentsToAuthor\\n4,ACCEPT,Strong paper"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validRowCount").value(1))
                .andReturn();

        Integer reportsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM REVIEW_REPORT", Integer.class);
        assertThat(reportsBefore).isZero();

        long batchId = objectMapper.readTree(previewResult.getResponse().getContentAsString())
                .path("batchId")
                .asLong();
        mockMvc.perform(post("/api/offline-review-imports/{batchId}/confirm", batchId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        Integer reportsAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM REVIEW_REPORT", Integer.class);
        assertThat(reportsAfter).isEqualTo(1);
    }

    @Test
    void cameraReadyMetadataAndProceedingsPreviewUseAcceptedManuscript() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_STATUS = 'ACCEPTED', LAST_DECISION_CODE = 'ACCEPT' WHERE MANUSCRIPT_ID = ?", fixture.manuscriptId());
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/camera-ready-files", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "camera-ready.pdf",
                                  "fileSize": 2048,
                                  "checksumSha256": "abc123",
                                  "copyrightConfirmed": true,
                                  "licenseType": "CC-BY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileStatus").value("SUBMITTED"));
        long cameraReadyFileId = jdbcTemplate.queryForObject(
                "SELECT CAMERA_READY_FILE_ID FROM CAMERA_READY_FILE WHERE MANUSCRIPT_ID = ?",
                Long.class,
                fixture.manuscriptId()
        );
        mockMvc.perform(post("/api/camera-ready-files/{fileId}/accept", cameraReadyFileId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"decisionNote\":\"Package accepted.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileStatus").value("ACCEPTED"));

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/publication-metadata", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "doi": "10.5555/wave9",
                                  "indexKeywords": "systems,review",
                                  "publicationStatus": "READY_FOR_PROCEEDINGS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("READY_FOR_PROCEEDINGS"));

        mockMvc.perform(post("/api/conferences/{conferenceId}/proceedings/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportName\":\"Wave 9 Proceedings\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paperCount").value(1))
                .andExpect(jsonPath("$.exportStatus").value("PREVIEWED"));
    }

    @Test
    void proceedingsExportMetadataMarksPreviewAsExportedWithoutExternalProvider() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_STATUS = 'ACCEPTED', LAST_DECISION_CODE = 'ACCEPT' WHERE MANUSCRIPT_ID = ?", fixture.manuscriptId());
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/publication-metadata", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "doi": "10.5555/wave9-export",
                                  "indexKeywords": "systems,export",
                                  "publicationStatus": "READY_FOR_PROCEEDINGS"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult previewResult = mockMvc.perform(post("/api/conferences/{conferenceId}/proceedings/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportName\":\"Wave 9 Metadata Export\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long exportBatchId = objectMapper.readTree(previewResult.getResponse().getContentAsString())
                .path("exportBatchId")
                .asLong();

        mockMvc.perform(post("/api/proceedings-exports/{exportBatchId}/download-metadata", exportBatchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportBatchId").value(exportBatchId))
                .andExpect(jsonPath("$.downloadFileName").value("wave-9-metadata-export.json"))
                .andExpect(jsonPath("$.exportStatus").value("EXPORTED"));

        String exportStatus = jdbcTemplate.queryForObject(
                "SELECT EXPORT_STATUS FROM PROCEEDINGS_EXPORT_BATCH WHERE EXPORT_BATCH_ID = ?",
                String.class,
                exportBatchId
        );
        String publicationStatus = jdbcTemplate.queryForObject(
                "SELECT PUBLICATION_STATUS FROM PUBLICATION_METADATA WHERE MANUSCRIPT_ID = ?",
                String.class,
                fixture.manuscriptId()
        );
        assertThat(exportStatus).isEqualTo("EXPORTED");
        assertThat(publicationStatus).isEqualTo("EXPORTED");
    }

    @Test
    void publicationOperationsReadModelListsTemplatesOfflineImportsCameraReadyAndExports() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_STATUS = 'ACCEPTED', LAST_DECISION_CODE = 'ACCEPT' WHERE MANUSCRIPT_ID = ?", fixture.manuscriptId());
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        String authorToken = loginAndExtractToken("author_demo", "demo123");

        MvcResult templateResult = mockMvc.perform(post("/api/conferences/{conferenceId}/email-templates", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "decision_notice",
                                  "subjectTemplate": "Decision for {{title}}",
                                  "bodyTemplate": "Dear author, {{decision}}"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).path("templateId").asLong();
        mockMvc.perform(post("/api/email-templates/{templateId}/test-send", templateId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientEmail": "chair@example.com",
                                  "variables": { "title": "Wave 9 Paper", "decision": "Accept" }
                                }
                                """))
                .andExpect(status().isOk());
        MvcResult offlinePreviewResult = mockMvc.perform(post("/api/review-assignments/{assignmentId}/offline-review/preview", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "overallScore,recommendation,commentsToAuthor\\n4,ACCEPT,Clear contribution"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long offlineBatchId = objectMapper.readTree(offlinePreviewResult.getResponse().getContentAsString()).path("batchId").asLong();
        mockMvc.perform(post("/api/offline-review-imports/{batchId}/confirm", offlineBatchId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/camera-ready-files", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "camera-ready.pdf",
                                  "fileSize": 2048,
                                  "checksumSha256": "abc123",
                                  "copyrightConfirmed": true,
                                  "licenseType": "CC-BY"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/publication-metadata", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "doi": "10.5555/wave9-dashboard",
                                  "indexKeywords": "systems,dashboard",
                                  "publicationStatus": "READY_FOR_PROCEEDINGS"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/conferences/{conferenceId}/proceedings/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportName\":\"Wave 9 Dashboard Export\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conferences/{conferenceId}/publication-operations", 0)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conferenceId").value(0))
                .andExpect(jsonPath("$.emailTemplates[0].templateKey").value("decision_notice"))
                .andExpect(jsonPath("$.emailHistory[0].recipientEmail").value("chair@example.com"))
                .andExpect(jsonPath("$.offlineReviewImports[0].batchStatus").value("APPLIED"))
                .andExpect(jsonPath("$.cameraReadyFiles[0].fileName").value("camera-ready.pdf"))
                .andExpect(jsonPath("$.publicationMetadata[0].doi").value("10.5555/wave9-dashboard"))
                .andExpect(jsonPath("$.proceedingsExports[0].exportName").value("Wave 9 Dashboard Export"));
    }

    private AssignmentFixture seedAcceptedAssignment() {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        long assignmentId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CONFERENCE_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, 0, NULL, 'UNDER_REVIEW', 1, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Wave 9 Paper', 'seed abstract', 'seed', 1001, ?, NULL)
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
                ) VALUES (?, ?, ?, ?, 1002, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL, ?, NULL, NULL)
                """,
                assignmentId,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plusSeconds(86400))
        );
        return new AssignmentFixture(manuscriptId, assignmentId);
    }

    private void seedLegacyConference() {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE C
                USING (
                  SELECT 0 AS CONFERENCE_ID, 'Legacy / Platform Default' AS NAME, 'LEGACY' AS ACRONYM,
                         2026 AS CONFERENCE_YEAR, 1003 AS ORGANIZER_USER_ID, 'OPEN_FOR_SUBMISSION' AS CONFERENCE_STATUS,
                         'DOUBLE_BLIND' AS BLIND_MODE, 'Default conference for tests.' AS CFP_TEXT,
                         '["LEGACY"]' AS TOPIC_AREAS_JSON, 3 AS TARGET_REVIEWS_PER_PAPER,
                         3 AS DEFAULT_REVIEWER_MAX_LOAD, 'legacy-platform-default' AS PUBLIC_SLUG, 0 AS CFP_PUBLISHED
                  FROM DUAL
                ) S
                ON (C.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (
                    CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                    CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                    TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                  ) VALUES (
                    S.CONFERENCE_ID, S.NAME, S.ACRONYM, S.CONFERENCE_YEAR, S.ORGANIZER_USER_ID,
                    S.CONFERENCE_STATUS, S.BLIND_MODE, S.CFP_TEXT, S.TOPIC_AREAS_JSON,
                    S.TARGET_REVIEWS_PER_PAPER, S.DEFAULT_REVIEWER_MAX_LOAD, S.PUBLIC_SLUG, S.CFP_PUBLISHED
                  )
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private record AssignmentFixture(long manuscriptId, long assignmentId) {
    }
}
