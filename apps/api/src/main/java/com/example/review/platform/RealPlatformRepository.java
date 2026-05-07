package com.example.review.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class RealPlatformRepository {
    private static final TypeReference<Map<String, Object>> ANSWERS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RealPlatformRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<PlatformConferenceRow> findConference(long conferenceId) {
        List<PlatformConferenceRow> rows = jdbcTemplate.query(
                """
                SELECT CONFERENCE_ID, ORGANIZER_USER_ID
                FROM CONFERENCE
                WHERE CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new PlatformConferenceRow(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                conferenceId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformManuscriptRow> findManuscript(long manuscriptId) {
        List<PlatformManuscriptRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.SUBMITTER_ID,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM MANUSCRIPT M
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new PlatformManuscriptRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("SUBMITTER_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformAssignmentRow> findAssignment(long assignmentId) {
        List<PlatformAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       A.TASK_STATUS,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new PlatformAssignmentRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Payload cannot be serialized");
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, ANSWERS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored answers payload is invalid");
        }
    }

    private <T> T fromJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored import payload is invalid");
        }
    }
}

record PlatformConferenceRow(long conferenceId, Long organizerUserId) {
}

record PlatformManuscriptRow(long manuscriptId, long submitterId, long conferenceId, Long organizerUserId) {
}

record PlatformAssignmentRow(
        long assignmentId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        String taskStatus,
        long conferenceId,
        Long organizerUserId
) {
}

record PlatformFormRow(long formId, long conferenceId, String formType, String formName) {
}

record PlatformFormFieldRow(
        long fieldId,
        String fieldKey,
        String fieldLabel,
        String fieldType,
        boolean required,
        String visibility,
        int displayOrder
) {
}

record PlatformReviewFormResponseRow(
        long responseId,
        long formId,
        long assignmentId,
        String responseStatus,
        Map<String, Object> answers,
        Timestamp submittedAt
) {
}

record PlatformAuthorFeedbackRow(
        long feedbackId,
        long manuscriptId,
        long submittedBy,
        String feedbackType,
        String feedbackText,
        Timestamp createdAt
) {
}

record PlatformPaperTagRow(long paperTagId, long conferenceId, long manuscriptId, String tagName, String tagValue) {
}

record PlatformPaperRoleRow(long paperRoleId, long conferenceId, long manuscriptId, long userId, String roleType) {
}

record PlatformImportBatchRow(
        long batchId,
        long conferenceId,
        String importType,
        long submittedBy,
        String batchStatus,
        TagImportPreviewDocument previewDocument
) {
}
