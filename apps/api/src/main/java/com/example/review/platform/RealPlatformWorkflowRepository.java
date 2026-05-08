package com.example.review.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformWorkflowRepository extends RealPlatformRepository {
    public RealPlatformWorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        super(jdbcTemplate, objectMapper);
    }

    public long insertFormDefinition(long conferenceId, String formType, String formName, long createdBy) {
        long formId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE_FORM_DEFINITION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_FORM_DEFINITION (
                  FORM_ID, CONFERENCE_ID, FORM_TYPE, FORM_NAME, ACTIVE_FLAG, CREATED_BY, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                formId,
                conferenceId,
                formType,
                formName,
                createdBy
        );
        return formId;
    }

    public long insertFormField(
            long formId,
            String fieldKey,
            String fieldLabel,
            String fieldType,
            boolean required,
            String visibility,
            int displayOrder,
            String optionsJson
    ) {
        long fieldId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE_FORM_FIELD.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_FORM_FIELD (
                  FIELD_ID, FORM_ID, FIELD_KEY, FIELD_LABEL, FIELD_TYPE, REQUIRED_FLAG,
                  VISIBILITY, DISPLAY_ORDER, OPTIONS_JSON
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                fieldId,
                formId,
                fieldKey,
                fieldLabel,
                fieldType,
                required ? 1 : 0,
                visibility,
                displayOrder,
                optionsJson
        );
        return fieldId;
    }

    public Optional<PlatformFormRow> findForm(long formId) {
        List<PlatformFormRow> rows = jdbcTemplate.query(
                """
                SELECT FORM_ID, CONFERENCE_ID, FORM_TYPE, FORM_NAME
                FROM CONFERENCE_FORM_DEFINITION
                WHERE FORM_ID = ?
                  AND ACTIVE_FLAG = 1
                """,
                (rs, rowNum) -> new PlatformFormRow(
                        rs.getLong("FORM_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("FORM_TYPE"),
                        rs.getString("FORM_NAME")
                ),
                formId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformFormRow> findActiveForm(long conferenceId, String formType) {
        List<PlatformFormRow> rows = jdbcTemplate.query(
                """
                SELECT FORM_ID, CONFERENCE_ID, FORM_TYPE, FORM_NAME
                FROM CONFERENCE_FORM_DEFINITION
                WHERE CONFERENCE_ID = ?
                  AND FORM_TYPE = ?
                  AND ACTIVE_FLAG = 1
                ORDER BY UPDATED_AT DESC, FORM_ID DESC
                FETCH FIRST 1 ROW ONLY
                """,
                (rs, rowNum) -> new PlatformFormRow(
                        rs.getLong("FORM_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("FORM_TYPE"),
                        rs.getString("FORM_NAME")
                ),
                conferenceId,
                formType
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformFormFieldRow> listFormFields(long formId) {
        return jdbcTemplate.query(
                """
                SELECT FIELD_ID, FIELD_KEY, FIELD_LABEL, FIELD_TYPE, REQUIRED_FLAG, VISIBILITY, DISPLAY_ORDER
                FROM CONFERENCE_FORM_FIELD
                WHERE FORM_ID = ?
                ORDER BY DISPLAY_ORDER, FIELD_ID
                """,
                (rs, rowNum) -> new PlatformFormFieldRow(
                        rs.getLong("FIELD_ID"),
                        rs.getString("FIELD_KEY"),
                        rs.getString("FIELD_LABEL"),
                        rs.getString("FIELD_TYPE"),
                        rs.getInt("REQUIRED_FLAG") == 1,
                        rs.getString("VISIBILITY"),
                        rs.getInt("DISPLAY_ORDER")
                ),
                formId
        );
    }

    public long upsertReviewFormResponse(
            long formId,
            long assignmentId,
            long reviewerId,
            String responseStatus,
            Map<String, Object> answers
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID
                FROM REVIEW_FORM_RESPONSE
                WHERE FORM_ID = ?
                  AND ASSIGNMENT_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("RESPONSE_ID") : null,
                formId,
                assignmentId
        );
        String answersJson = toJson(answers);
        Timestamp submittedAt = "SUBMITTED".equals(responseStatus) ? new Timestamp(System.currentTimeMillis()) : null;
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE REVIEW_FORM_RESPONSE
                    SET REVIEWER_ID = ?,
                        RESPONSE_STATUS = ?,
                        ANSWERS_JSON = ?,
                        SUBMITTED_AT = ?
                    WHERE RESPONSE_ID = ?
                    """,
                    reviewerId,
                    responseStatus,
                    answersJson,
                    submittedAt,
                    existingId
            );
            return existingId;
        }
        long responseId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_FORM_RESPONSE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_FORM_RESPONSE (
                  RESPONSE_ID, FORM_ID, ASSIGNMENT_ID, REVIEWER_ID, RESPONSE_STATUS,
                  ANSWERS_JSON, CREATED_AT, UPDATED_AT, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                responseId,
                formId,
                assignmentId,
                reviewerId,
                responseStatus,
                answersJson,
                submittedAt
        );
        return responseId;
    }

    public Optional<PlatformReviewFormResponseRow> findReviewFormResponse(long responseId) {
        List<PlatformReviewFormResponseRow> rows = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID, FORM_ID, ASSIGNMENT_ID, RESPONSE_STATUS, ANSWERS_JSON, SUBMITTED_AT
                FROM REVIEW_FORM_RESPONSE
                WHERE RESPONSE_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewFormResponseRow(
                        rs.getLong("RESPONSE_ID"),
                        rs.getLong("FORM_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getString("RESPONSE_STATUS"),
                        fromJson(rs.getString("ANSWERS_JSON")),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                responseId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void insertReviewFormRevision(long responseId, Map<String, Object> answers) {
        Integer nextRevisionNo = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(REVISION_NO), 0) + 1
                FROM REVIEW_FORM_RESPONSE_REVISION
                WHERE RESPONSE_ID = ?
                """,
                Integer.class,
                responseId
        );
        long revisionId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_FORM_RESPONSE_REV.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_FORM_RESPONSE_REVISION (
                  REVISION_ID, RESPONSE_ID, REVISION_NO, ANSWERS_JSON, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                revisionId,
                responseId,
                nextRevisionNo == null ? 1 : nextRevisionNo,
                toJson(answers)
        );
    }

    public long upsertWorkflowFormResponse(
            long formId,
            String subjectType,
            long subjectId,
            long submittedBy,
            String responseStatus,
            Map<String, Object> answers
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID
                FROM WORKFLOW_FORM_RESPONSE
                WHERE FORM_ID = ?
                  AND SUBJECT_TYPE = ?
                  AND SUBJECT_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("RESPONSE_ID") : null,
                formId,
                subjectType,
                subjectId
        );
        String answersJson = toJson(answers);
        Timestamp submittedAt = "SUBMITTED".equals(responseStatus) ? new Timestamp(System.currentTimeMillis()) : null;
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE WORKFLOW_FORM_RESPONSE
                    SET SUBMITTED_BY = ?,
                        RESPONSE_STATUS = ?,
                        ANSWERS_JSON = ?,
                        SUBMITTED_AT = ?
                    WHERE RESPONSE_ID = ?
                    """,
                    submittedBy,
                    responseStatus,
                    answersJson,
                    submittedAt,
                    existingId
            );
            return existingId;
        }
        long responseId = jdbcTemplate.queryForObject("SELECT SEQ_WORKFLOW_FORM_RESPONSE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO WORKFLOW_FORM_RESPONSE (
                  RESPONSE_ID, FORM_ID, SUBJECT_TYPE, SUBJECT_ID, SUBMITTED_BY,
                  RESPONSE_STATUS, ANSWERS_JSON, CREATED_AT, UPDATED_AT, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                responseId,
                formId,
                subjectType,
                subjectId,
                submittedBy,
                responseStatus,
                answersJson,
                submittedAt
        );
        return responseId;
    }

    public Optional<PlatformWorkflowFormResponseRow> findWorkflowFormResponse(long responseId) {
        List<PlatformWorkflowFormResponseRow> rows = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID, FORM_ID, SUBJECT_TYPE, SUBJECT_ID, RESPONSE_STATUS, ANSWERS_JSON, SUBMITTED_AT
                FROM WORKFLOW_FORM_RESPONSE
                WHERE RESPONSE_ID = ?
                """,
                (rs, rowNum) -> new PlatformWorkflowFormResponseRow(
                        rs.getLong("RESPONSE_ID"),
                        rs.getLong("FORM_ID"),
                        rs.getString("SUBJECT_TYPE"),
                        rs.getLong("SUBJECT_ID"),
                        rs.getString("RESPONSE_STATUS"),
                        fromJson(rs.getString("ANSWERS_JSON")),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                responseId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformPhaseWindowRow> findPhaseWindows(long conferenceId) {
        List<PlatformPhaseWindowRow> rows = jdbcTemplate.query(
                """
                SELECT SUBMISSION_OPEN_AT,
                       SUBMISSION_CLOSE_AT,
                       REVIEW_DEADLINE_AT,
                       DECISION_RELEASE_AT,
                       REBUTTAL_OPEN_AT,
                       REBUTTAL_CLOSE_AT,
                       CAMERA_READY_OPEN_AT,
                       CAMERA_READY_CLOSE_AT
                FROM CONFERENCE_PHASE
                WHERE CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new PlatformPhaseWindowRow(
                        rs.getTimestamp("SUBMISSION_OPEN_AT"),
                        rs.getTimestamp("SUBMISSION_CLOSE_AT"),
                        rs.getTimestamp("REVIEW_DEADLINE_AT"),
                        rs.getTimestamp("DECISION_RELEASE_AT"),
                        rs.getTimestamp("REBUTTAL_OPEN_AT"),
                        rs.getTimestamp("REBUTTAL_CLOSE_AT"),
                        rs.getTimestamp("CAMERA_READY_OPEN_AT"),
                        rs.getTimestamp("CAMERA_READY_CLOSE_AT")
                ),
                conferenceId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void markAssignmentSubmitted(long assignmentId) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'SUBMITTED', SUBMITTED_AT = CURRENT_TIMESTAMP WHERE ASSIGNMENT_ID = ?",
                assignmentId
        );
    }

    public long insertAuthorFeedback(long manuscriptId, long submittedBy, String feedbackType, String feedbackText) {
        long feedbackId = jdbcTemplate.queryForObject("SELECT SEQ_AUTHOR_FEEDBACK.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO AUTHOR_FEEDBACK (
                  FEEDBACK_ID, MANUSCRIPT_ID, SUBMITTED_BY, FEEDBACK_TYPE, FEEDBACK_TEXT, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                feedbackId,
                manuscriptId,
                submittedBy,
                feedbackType,
                feedbackText
        );
        return feedbackId;
    }

    public Optional<PlatformAuthorFeedbackRow> findAuthorFeedback(long feedbackId) {
        List<PlatformAuthorFeedbackRow> rows = jdbcTemplate.query(
                """
                SELECT FEEDBACK_ID, MANUSCRIPT_ID, SUBMITTED_BY, FEEDBACK_TYPE, FEEDBACK_TEXT, CREATED_AT
                FROM AUTHOR_FEEDBACK
                WHERE FEEDBACK_ID = ?
                """,
                this::mapAuthorFeedback,
                feedbackId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformAuthorFeedbackRow> listAuthorFeedback(long manuscriptId) {
        return jdbcTemplate.query(
                """
                SELECT FEEDBACK_ID, MANUSCRIPT_ID, SUBMITTED_BY, FEEDBACK_TYPE, FEEDBACK_TEXT, CREATED_AT
                FROM AUTHOR_FEEDBACK
                WHERE MANUSCRIPT_ID = ?
                ORDER BY CREATED_AT, FEEDBACK_ID
                """,
                this::mapAuthorFeedback,
                manuscriptId
        );
    }

    public long upsertPaperTag(long conferenceId, long manuscriptId, String tagName, String tagValue, long createdBy) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT PAPER_TAG_ID
                FROM PAPER_TAG
                WHERE CONFERENCE_ID = ?
                  AND MANUSCRIPT_ID = ?
                  AND TAG_NAME = ?
                """,
                rs -> rs.next() ? rs.getLong("PAPER_TAG_ID") : null,
                conferenceId,
                manuscriptId,
                tagName
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE PAPER_TAG
                    SET TAG_VALUE = ?,
                        CREATED_BY = ?
                    WHERE PAPER_TAG_ID = ?
                    """,
                    tagValue,
                    createdBy,
                    existingId
            );
            return existingId;
        }
        long paperTagId = jdbcTemplate.queryForObject("SELECT SEQ_PAPER_TAG.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PAPER_TAG (
                  PAPER_TAG_ID, CONFERENCE_ID, MANUSCRIPT_ID, TAG_NAME, TAG_VALUE,
                  CREATED_BY, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                paperTagId,
                conferenceId,
                manuscriptId,
                tagName,
                tagValue,
                createdBy
        );
        return paperTagId;
    }

    public Optional<PlatformPaperTagRow> findPaperTag(long paperTagId) {
        List<PlatformPaperTagRow> rows = jdbcTemplate.query(
                """
                SELECT PAPER_TAG_ID, CONFERENCE_ID, MANUSCRIPT_ID, TAG_NAME, TAG_VALUE
                FROM PAPER_TAG
                WHERE PAPER_TAG_ID = ?
                """,
                (rs, rowNum) -> new PlatformPaperTagRow(
                        rs.getLong("PAPER_TAG_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("TAG_NAME"),
                        rs.getString("TAG_VALUE")
                ),
                paperTagId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long upsertPaperRole(long conferenceId, long manuscriptId, long userId, String roleType, long assignedBy) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT PAPER_ROLE_ID
                FROM PAPER_ROLE_ASSIGNMENT
                WHERE MANUSCRIPT_ID = ?
                  AND USER_ID = ?
                  AND ROLE_TYPE = ?
                """,
                rs -> rs.next() ? rs.getLong("PAPER_ROLE_ID") : null,
                manuscriptId,
                userId,
                roleType
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE PAPER_ROLE_ASSIGNMENT
                    SET CONFERENCE_ID = ?,
                        ASSIGNED_BY = ?,
                        ASSIGNED_AT = CURRENT_TIMESTAMP
                    WHERE PAPER_ROLE_ID = ?
                    """,
                    conferenceId,
                    assignedBy,
                    existingId
            );
            return existingId;
        }
        long paperRoleId = jdbcTemplate.queryForObject("SELECT SEQ_PAPER_ROLE_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PAPER_ROLE_ASSIGNMENT (
                  PAPER_ROLE_ID, CONFERENCE_ID, MANUSCRIPT_ID, USER_ID, ROLE_TYPE, ASSIGNED_BY, ASSIGNED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                paperRoleId,
                conferenceId,
                manuscriptId,
                userId,
                roleType,
                assignedBy
        );
        return paperRoleId;
    }

    public Optional<PlatformPaperRoleRow> findPaperRole(long paperRoleId) {
        List<PlatformPaperRoleRow> rows = jdbcTemplate.query(
                """
                SELECT PAPER_ROLE_ID, CONFERENCE_ID, MANUSCRIPT_ID, USER_ID, ROLE_TYPE
                FROM PAPER_ROLE_ASSIGNMENT
                WHERE PAPER_ROLE_ID = ?
                """,
                (rs, rowNum) -> new PlatformPaperRoleRow(
                        rs.getLong("PAPER_ROLE_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("USER_ID"),
                        rs.getString("ROLE_TYPE")
                ),
                paperRoleId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertImportBatch(
            long conferenceId,
            long submittedBy,
            int rowCount,
            int validRowCount,
            int errorCount,
            TagImportPreviewDocument previewDocument
    ) {
        long batchId = jdbcTemplate.queryForObject("SELECT SEQ_IMPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO IMPORT_BATCH (
                  IMPORT_BATCH_ID, CONFERENCE_ID, IMPORT_TYPE, SUBMITTED_BY, BATCH_STATUS,
                  ROW_COUNT, VALID_ROW_COUNT, ERROR_COUNT, PREVIEW_JSON, CREATED_AT, APPLIED_AT
                ) VALUES (?, ?, 'TAGS', ?, 'PREVIEWED', ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                batchId,
                conferenceId,
                submittedBy,
                rowCount,
                validRowCount,
                errorCount,
                toJson(previewDocument)
        );
        return batchId;
    }

    public Optional<PlatformImportBatchRow> findImportBatch(long batchId) {
        List<PlatformImportBatchRow> rows = jdbcTemplate.query(
                """
                SELECT IMPORT_BATCH_ID, CONFERENCE_ID, IMPORT_TYPE, SUBMITTED_BY, BATCH_STATUS, PREVIEW_JSON
                FROM IMPORT_BATCH
                WHERE IMPORT_BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformImportBatchRow(
                        rs.getLong("IMPORT_BATCH_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("IMPORT_TYPE"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("BATCH_STATUS"),
                        fromJson(rs.getString("PREVIEW_JSON"), TagImportPreviewDocument.class)
                ),
                batchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void markImportApplied(long batchId) {
        jdbcTemplate.update(
                "UPDATE IMPORT_BATCH SET BATCH_STATUS = 'APPLIED', APPLIED_AT = CURRENT_TIMESTAMP WHERE IMPORT_BATCH_ID = ?",
                batchId
        );
    }

    private PlatformAuthorFeedbackRow mapAuthorFeedback(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformAuthorFeedbackRow(
                rs.getLong("FEEDBACK_ID"),
                rs.getLong("MANUSCRIPT_ID"),
                rs.getLong("SUBMITTED_BY"),
                rs.getString("FEEDBACK_TYPE"),
                rs.getString("FEEDBACK_TEXT"),
                rs.getTimestamp("CREATED_AT")
        );
    }
}
