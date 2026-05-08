package com.example.review.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class RealPlatformWorkflowServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRealPlatformTables() {
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE");
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
    void chairConfiguresRequiredReviewFieldAndReviewerDraftDoesNotSubmitAssignment() throws Exception {
        long formId = createReviewFormWithRequiredField();
        AssignmentFixture fixture = seedAcceptedAssignment();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/form-response", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "DRAFT",
                                  "answers": {}
                                }
                                """.formatted(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("DRAFT"));

        String taskStatus = jdbcTemplate.queryForObject(
                "SELECT TASK_STATUS FROM REVIEW_ASSIGNMENT WHERE ASSIGNMENT_ID = ?",
                String.class,
                fixture.assignmentId()
        );
        assertThat(taskStatus).isEqualTo("ACCEPTED");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/form-response", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": {}
                                }
                                """.formatted(formId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewerLoadsActiveDynamicReviewFormWithSavedDraftAnswers() throws Exception {
        long formId = createReviewFormWithRequiredField();
        AssignmentFixture fixture = seedAcceptedAssignment();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/form-response", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "DRAFT",
                                  "answers": { "summary": "Promising but incomplete." }
                                }
                                """.formatted(formId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/review-form", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form.formId").value(formId))
                .andExpect(jsonPath("$.form.fields[0].fieldKey").value("summary"))
                .andExpect(jsonPath("$.currentResponse.responseStatus").value("DRAFT"))
                .andExpect(jsonPath("$.currentResponse.answers.summary").value("Promising but incomplete."));
    }

    @Test
    void authorSubmitsRebuttalAndChairCanReadIt() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/author-feedback", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "REBUTTAL",
                                  "feedbackText": "We address the missing ablation in the revision plan."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackType").value("REBUTTAL"))
                .andExpect(jsonPath("$.feedbackText").value("We address the missing ablation in the revision plan."));

        mockMvc.perform(get("/api/manuscripts/{manuscriptId}/author-feedback", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].feedbackType").value("REBUTTAL"));
    }

    @Test
    void chairTagsPaperAssignsPaperRoleAndCsvPreviewDoesNotMutateTags() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/tags", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "tagName": "needs-discussion",
                                  "tagValue": "5"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagName").value("needs-discussion"))
                .andExpect(jsonPath("$.tagValue").value("5"));

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/paper-roles", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1002,
                                  "roleType": "SHEPHERD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleType").value("SHEPHERD"));

        MvcResult previewResult = mockMvc.perform(post("/api/conferences/{conferenceId}/imports/tags/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "manuscriptId,tagName,tagValue\\n%d,bulk-tag,1\\nnot-a-number,bad,2"
                                }
                                """.formatted(fixture.manuscriptId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.validRowCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andReturn();

        JsonNode preview = objectMapper.readTree(previewResult.getResponse().getContentAsString());
        long batchId = preview.path("batchId").asLong();
        Integer tagsBeforeConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PAPER_TAG WHERE TAG_NAME = 'bulk-tag'",
                Integer.class
        );
        assertThat(tagsBeforeConfirm).isZero();

        mockMvc.perform(post("/api/imports/{batchId}/confirm", batchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        Integer tagsAfterConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PAPER_TAG WHERE TAG_NAME = 'bulk-tag'",
                Integer.class
        );
        assertThat(tagsAfterConfirm).isEqualTo(1);
    }

    private long createReviewFormWithRequiredField() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        MvcResult result = mockMvc.perform(post("/api/conferences/{conferenceId}/forms", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formType": "REVIEW",
                                  "formName": "Default Review",
                                  "fields": [
                                    {
                                      "fieldKey": "summary",
                                      "fieldLabel": "Summary",
                                      "fieldType": "TEXT",
                                      "required": true,
                                      "visibility": "AUTHOR_VISIBLE",
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formId").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("formId").asLong();
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
                VALUES (?, ?, 1, 'INITIAL', 'Wave 6 Seed', 'seed abstract', 'seed', 1001, ?, NULL)
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
