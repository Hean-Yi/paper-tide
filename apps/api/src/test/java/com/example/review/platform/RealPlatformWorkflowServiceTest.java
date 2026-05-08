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
        resetLegacyConferencePhase();
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
    void chairConfiguresAndActorsLoadNonReviewWorkflowForms() throws Exception {
        long submissionFormId = createForm("SUBMISSION", "Submission Checklist", "ethics", "Ethics statement", "AUTHOR_VISIBLE");
        long metaReviewFormId = createForm("META_REVIEW", "Meta Review", "summary", "Meta review summary", "CHAIR_ONLY");
        long feedbackFormId = createForm("AUTHOR_FEEDBACK", "Author Feedback", "response", "Response", "AUTHOR_VISIBLE");
        long cameraReadyFormId = createForm("CAMERA_READY", "Camera Ready Checklist", "copyright", "Copyright", "AUTHOR_VISIBLE");
        AssignmentFixture fixture = seedAcceptedAssignment();
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/conferences/{conferenceId}/forms/{formType}/active", 0, "SUBMISSION")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form.formId").value(submissionFormId))
                .andExpect(jsonPath("$.form.fields[0].fieldKey").value("ethics"));

        mockMvc.perform(get("/api/conferences/{conferenceId}/forms/{formType}/active", 0, "META_REVIEW")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form.formId").value(metaReviewFormId));

        mockMvc.perform(get("/api/manuscripts/{manuscriptId}/forms/{formType}", fixture.manuscriptId(), "AUTHOR_FEEDBACK")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form.formId").value(feedbackFormId));

        mockMvc.perform(get("/api/manuscripts/{manuscriptId}/forms/{formType}", fixture.manuscriptId(), "CAMERA_READY")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form.formId").value(cameraReadyFormId));
    }

    @Test
    void authorWorkflowFormResponseValidatesRequiredFieldsAtSubmitTime() throws Exception {
        long formId = createForm("SUBMISSION", "Submission Checklist", "ethics", "Ethics statement", "AUTHOR_VISIBLE");
        AssignmentFixture fixture = seedAcceptedAssignment();
        String authorToken = loginAndExtractToken("author_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/form-response", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": {}
                                }
                                """.formatted(formId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/form-response", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": { "ethics": "No human subjects." }
                                }
                                """.formatted(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.answers.ethics").value("No human subjects."));
    }

    @Test
    void chairSubmitsMetaReviewFormForManuscriptAndAuthorCannot() throws Exception {
        long formId = createForm("META_REVIEW", "Meta Review", "summary", "Meta review summary", "CHAIR_ONLY");
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String authorToken = loginAndExtractToken("author_demo", "demo123");

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/form-response", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": { "summary": "Author must not write the meta review." }
                                }
                                """.formatted(formId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/form-response", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": { "summary": "Reviews are consistent enough for acceptance." }
                                }
                                """.formatted(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectType").value("MANUSCRIPT"))
                .andExpect(jsonPath("$.responseStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.answers.summary").value("Reviews are consistent enough for acceptance."));
    }

    @Test
    void submittedReviewFormResponsesKeepRevisionHistory() throws Exception {
        long formId = createReviewFormWithRequiredField();
        AssignmentFixture fixture = seedAcceptedAssignment();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        submitReviewForm(fixture.assignmentId(), formId, reviewerToken, "Initial summary");
        submitReviewForm(fixture.assignmentId(), formId, reviewerToken, "Updated summary");

        mockMvc.perform(get("/api/review-assignments/{assignmentId}/review-form/revisions", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionNo").value(1))
                .andExpect(jsonPath("$[0].answers.summary").value("Initial summary"))
                .andExpect(jsonPath("$[1].revisionNo").value(2))
                .andExpect(jsonPath("$[1].answers.summary").value("Updated summary"));
    }

    @Test
    void rebuttalSubmissionClosesAfterDecisionReleaseUntilExplicitWindowIsOpened() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        Instant past = Instant.now().minusSeconds(7200);
        jdbcTemplate.update(
                """
                UPDATE CONFERENCE_PHASE
                SET SUBMISSION_OPEN_AT = ?,
                    SUBMISSION_CLOSE_AT = ?,
                    BIDDING_OPEN_AT = ?,
                    BIDDING_CLOSE_AT = ?,
                    REVIEW_DEADLINE_AT = ?,
                    DECISION_RELEASE_AT = ?
                WHERE CONFERENCE_ID = 0
                """,
                Timestamp.from(past),
                Timestamp.from(past.plusSeconds(600)),
                Timestamp.from(past.plusSeconds(1200)),
                Timestamp.from(past.plusSeconds(1800)),
                Timestamp.from(past.plusSeconds(2400)),
                Timestamp.from(past.plusSeconds(3000))
        );

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/author-feedback", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "REBUTTAL",
                                  "feedbackText": "This late rebuttal should be blocked."
                                }
                                """))
                .andExpect(status().isConflict());
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
        return createForm("REVIEW", "Default Review", "summary", "Summary", "AUTHOR_VISIBLE");
    }

    private long createForm(String formType, String formName, String fieldKey, String fieldLabel, String visibility) throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        MvcResult result = mockMvc.perform(post("/api/conferences/{conferenceId}/forms", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formType": "%s",
                                  "formName": "%s",
                                  "fields": [
                                    {
                                      "fieldKey": "%s",
                                      "fieldLabel": "%s",
                                      "fieldType": "TEXT",
                                      "required": true,
                                      "visibility": "%s",
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """.formatted(formType, formName, fieldKey, fieldLabel, visibility)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formId").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("formId").asLong();
    }

    private void submitReviewForm(long assignmentId, long formId, String reviewerToken, String summary) throws Exception {
        mockMvc.perform(post("/api/review-assignments/{assignmentId}/form-response", assignmentId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "formId": %d,
                                  "responseStatus": "SUBMITTED",
                                  "answers": { "summary": "%s" }
                                }
                                """.formatted(formId, summary)))
                .andExpect(status().isOk());
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

    private void resetLegacyConferencePhase() {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE_PHASE P
                USING (
                  SELECT 0 AS CONFERENCE_ID,
                         TIMESTAMP '2099-01-01 00:00:00' AS SUBMISSION_OPEN_AT,
                         TIMESTAMP '2099-02-01 00:00:00' AS SUBMISSION_CLOSE_AT,
                         TIMESTAMP '2099-03-01 00:00:00' AS BIDDING_OPEN_AT,
                         TIMESTAMP '2099-04-01 00:00:00' AS BIDDING_CLOSE_AT,
                         TIMESTAMP '2099-05-01 00:00:00' AS REVIEW_DEADLINE_AT,
                         TIMESTAMP '2099-06-01 00:00:00' AS DECISION_RELEASE_AT
                  FROM DUAL
                ) S
                ON (P.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN MATCHED THEN UPDATE SET
                  P.SUBMISSION_OPEN_AT = S.SUBMISSION_OPEN_AT,
                  P.SUBMISSION_CLOSE_AT = S.SUBMISSION_CLOSE_AT,
                  P.BIDDING_OPEN_AT = S.BIDDING_OPEN_AT,
                  P.BIDDING_CLOSE_AT = S.BIDDING_CLOSE_AT,
                  P.REVIEW_DEADLINE_AT = S.REVIEW_DEADLINE_AT,
                  P.DECISION_RELEASE_AT = S.DECISION_RELEASE_AT,
                  P.REBUTTAL_OPEN_AT = NULL,
                  P.REBUTTAL_CLOSE_AT = NULL,
                  P.CAMERA_READY_OPEN_AT = NULL,
                  P.CAMERA_READY_CLOSE_AT = NULL
                WHEN NOT MATCHED THEN INSERT (
                  PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, SUBMISSION_CLOSE_AT,
                  BIDDING_OPEN_AT, BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                ) VALUES (
                  SEQ_CONFERENCE_PHASE.NEXTVAL, S.CONFERENCE_ID, S.SUBMISSION_OPEN_AT, S.SUBMISSION_CLOSE_AT,
                  S.BIDDING_OPEN_AT, S.BIDDING_CLOSE_AT, S.REVIEW_DEADLINE_AT, S.DECISION_RELEASE_AT
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
