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
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       C.ORGANIZER_USER_ID
                FROM MANUSCRIPT M
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new PlatformManuscriptRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("SUBMITTER_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("CURRENT_VERSION_ID", Long.class),
                        rs.getString("CURRENT_STATUS"),
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

    public Optional<PlatformReviewRoundRow> findReviewRound(long roundId) {
        List<PlatformReviewRoundRow> rows = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE R.ROUND_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long upsertReviewerInvitation(
            long conferenceId,
            long reviewerId,
            String invitationMessage,
            long invitedBy,
            Timestamp expiresAt
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT INVITATION_ID
                FROM REVIEWER_INVITATION
                WHERE CONFERENCE_ID = ?
                  AND REVIEWER_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("INVITATION_ID") : null,
                conferenceId,
                reviewerId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE REVIEWER_INVITATION
                    SET INVITATION_STATUS = 'PENDING',
                        INVITATION_MESSAGE = ?,
                        INVITED_BY = ?,
                        INVITED_AT = CURRENT_TIMESTAMP,
                        RESPONDED_AT = NULL,
                        EXPIRES_AT = ?
                    WHERE INVITATION_ID = ?
                    """,
                    invitationMessage,
                    invitedBy,
                    expiresAt,
                    existingId
            );
            return existingId;
        }
        long invitationId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEWER_INVITATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEWER_INVITATION (
                  INVITATION_ID, CONFERENCE_ID, REVIEWER_ID, INVITATION_STATUS,
                  INVITATION_MESSAGE, INVITED_BY, INVITED_AT, RESPONDED_AT, EXPIRES_AT
                ) VALUES (?, ?, ?, 'PENDING', ?, ?, CURRENT_TIMESTAMP, NULL, ?)
                """,
                invitationId,
                conferenceId,
                reviewerId,
                invitationMessage,
                invitedBy,
                expiresAt
        );
        return invitationId;
    }

    public Optional<PlatformReviewerInvitationRow> findReviewerInvitation(long invitationId) {
        List<PlatformReviewerInvitationRow> rows = jdbcTemplate.query(
                """
                SELECT INVITATION_ID, CONFERENCE_ID, REVIEWER_ID, INVITATION_STATUS, INVITATION_MESSAGE,
                       INVITED_BY, INVITED_AT, RESPONDED_AT, EXPIRES_AT
                FROM REVIEWER_INVITATION
                WHERE INVITATION_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewerInvitationRow(
                        rs.getLong("INVITATION_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("INVITATION_STATUS"),
                        rs.getString("INVITATION_MESSAGE"),
                        rs.getLong("INVITED_BY"),
                        rs.getTimestamp("INVITED_AT"),
                        rs.getTimestamp("RESPONDED_AT"),
                        rs.getTimestamp("EXPIRES_AT")
                ),
                invitationId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void updateReviewerInvitationStatus(long invitationId, String invitationStatus) {
        jdbcTemplate.update(
                """
                UPDATE REVIEWER_INVITATION
                SET INVITATION_STATUS = ?,
                    RESPONDED_AT = CURRENT_TIMESTAMP
                WHERE INVITATION_ID = ?
                """,
                invitationStatus,
                invitationId
        );
    }

    public void upsertConferenceReviewer(long conferenceId, long reviewerId, long invitedBy) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT CONFERENCE_REVIEWER_ID
                FROM CONFERENCE_REVIEWER
                WHERE CONFERENCE_ID = ?
                  AND REVIEWER_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("CONFERENCE_REVIEWER_ID") : null,
                conferenceId,
                reviewerId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE CONFERENCE_REVIEWER
                    SET MEMBERSHIP_STATUS = 'ACTIVE',
                        INVITED_BY = ?,
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE CONFERENCE_REVIEWER_ID = ?
                    """,
                    invitedBy,
                    existingId
            );
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_REVIEWER (
                  CONFERENCE_REVIEWER_ID, CONFERENCE_ID, REVIEWER_ID, MEMBERSHIP_STATUS,
                  MAX_LOAD, RESEARCH_AREAS_JSON, INVITED_BY, JOINED_AT, UPDATED_AT
                ) VALUES (
                  SEQ_CONFERENCE_REVIEWER.NEXTVAL, ?, ?, 'ACTIVE', 3, '[]', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                conferenceId,
                reviewerId,
                invitedBy
        );
    }

    public long insertExternalReviewerDelegation(
            PlatformAssignmentRow assignment,
            long requestedBy,
            String externalName,
            String externalEmail,
            String rationale
    ) {
        long delegationId = jdbcTemplate.queryForObject("SELECT SEQ_EXTERNAL_REVIEWER_DELEGATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EXTERNAL_REVIEWER_DELEGATION (
                  DELEGATION_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REQUESTED_BY,
                  EXTERNAL_NAME, EXTERNAL_EMAIL, RATIONALE, DELEGATION_STATUS,
                  DECISION_NOTE, DECIDED_BY, REQUESTED_AT, DECIDED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'REQUESTED', NULL, NULL, CURRENT_TIMESTAMP, NULL)
                """,
                delegationId,
                assignment.assignmentId(),
                assignment.manuscriptId(),
                requestedBy,
                externalName,
                externalEmail,
                rationale
        );
        return delegationId;
    }

    public Optional<PlatformExternalDelegationRow> findExternalDelegation(long delegationId) {
        List<PlatformExternalDelegationRow> rows = jdbcTemplate.query(
                """
                SELECT D.DELEGATION_ID,
                       D.ASSIGNMENT_ID,
                       D.MANUSCRIPT_ID,
                       D.REQUESTED_BY,
                       D.EXTERNAL_NAME,
                       D.EXTERNAL_EMAIL,
                       D.DELEGATION_STATUS,
                       D.DECISION_NOTE,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM EXTERNAL_REVIEWER_DELEGATION D
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = D.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE D.DELEGATION_ID = ?
                """,
                (rs, rowNum) -> new PlatformExternalDelegationRow(
                        rs.getLong("DELEGATION_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REQUESTED_BY"),
                        rs.getString("EXTERNAL_NAME"),
                        rs.getString("EXTERNAL_EMAIL"),
                        rs.getString("DELEGATION_STATUS"),
                        rs.getString("DECISION_NOTE"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                delegationId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void decideExternalDelegation(long delegationId, String delegationStatus, String decisionNote, long decidedBy) {
        jdbcTemplate.update(
                """
                UPDATE EXTERNAL_REVIEWER_DELEGATION
                SET DELEGATION_STATUS = ?,
                    DECISION_NOTE = ?,
                    DECIDED_BY = ?,
                    DECIDED_AT = CURRENT_TIMESTAMP
                WHERE DELEGATION_ID = ?
                """,
                delegationStatus,
                decisionNote,
                decidedBy,
                delegationId
        );
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

    public long upsertConflictRelationship(
            long conferenceId,
            long manuscriptId,
            long reviewerId,
            String conflictType,
            String conflictSource,
            String severity,
            String note,
            long createdBy
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT CONFLICT_RELATIONSHIP_ID
                FROM CONFLICT_RELATIONSHIP
                WHERE CONFERENCE_ID = ?
                  AND MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = ?
                  AND CONFLICT_TYPE = ?
                """,
                rs -> rs.next() ? rs.getLong("CONFLICT_RELATIONSHIP_ID") : null,
                conferenceId,
                manuscriptId,
                reviewerId,
                conflictType
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE CONFLICT_RELATIONSHIP
                    SET CONFLICT_SOURCE = ?,
                        SEVERITY = ?,
                        NOTE = ?,
                        CREATED_BY = ?,
                        CREATED_AT = CURRENT_TIMESTAMP
                    WHERE CONFLICT_RELATIONSHIP_ID = ?
                    """,
                    conflictSource,
                    severity,
                    note,
                    createdBy,
                    existingId
            );
            return existingId;
        }
        long relationshipId = jdbcTemplate.queryForObject("SELECT SEQ_CONFLICT_RELATIONSHIP.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_RELATIONSHIP (
                  CONFLICT_RELATIONSHIP_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  CONFLICT_TYPE, CONFLICT_SOURCE, SEVERITY, NOTE, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                relationshipId,
                conferenceId,
                manuscriptId,
                reviewerId,
                conflictType,
                conflictSource,
                severity,
                note,
                createdBy
        );
        return relationshipId;
    }

    public Optional<PlatformConflictRelationshipRow> findConflictRelationship(long relationshipId) {
        List<PlatformConflictRelationshipRow> rows = jdbcTemplate.query(
                """
                SELECT CONFLICT_RELATIONSHIP_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                       CONFLICT_TYPE, CONFLICT_SOURCE, SEVERITY, NOTE
                FROM CONFLICT_RELATIONSHIP
                WHERE CONFLICT_RELATIONSHIP_ID = ?
                """,
                (rs, rowNum) -> new PlatformConflictRelationshipRow(
                        rs.getLong("CONFLICT_RELATIONSHIP_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("CONFLICT_TYPE"),
                        rs.getString("CONFLICT_SOURCE"),
                        rs.getString("SEVERITY"),
                        rs.getString("NOTE")
                ),
                relationshipId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long upsertReviewerMatchingScore(
            long conferenceId,
            long manuscriptId,
            long reviewerId,
            String scoreSource,
            double matchingScore,
            String rationale,
            long importedBy
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT MATCHING_SCORE_ID
                FROM REVIEWER_MATCHING_SCORE
                WHERE CONFERENCE_ID = ?
                  AND MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = ?
                  AND SCORE_SOURCE = ?
                """,
                rs -> rs.next() ? rs.getLong("MATCHING_SCORE_ID") : null,
                conferenceId,
                manuscriptId,
                reviewerId,
                scoreSource
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE REVIEWER_MATCHING_SCORE
                    SET MATCHING_SCORE = ?,
                        RATIONALE = ?,
                        IMPORTED_BY = ?,
                        IMPORTED_AT = CURRENT_TIMESTAMP
                    WHERE MATCHING_SCORE_ID = ?
                    """,
                    matchingScore,
                    rationale,
                    importedBy,
                    existingId
            );
            return existingId;
        }
        long matchingScoreId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEWER_MATCHING_SCORE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEWER_MATCHING_SCORE (
                  MATCHING_SCORE_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  SCORE_SOURCE, MATCHING_SCORE, RATIONALE, IMPORTED_BY, IMPORTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                matchingScoreId,
                conferenceId,
                manuscriptId,
                reviewerId,
                scoreSource,
                matchingScore,
                rationale,
                importedBy
        );
        return matchingScoreId;
    }

    public long insertAssignmentProposalBundle(
            PlatformReviewRoundRow round,
            String proposalName,
            long createdBy
    ) {
        long bundleId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL_BUNDLE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL_BUNDLE (
                  BUNDLE_ID, ROUND_ID, CONFERENCE_ID, MANUSCRIPT_ID, PROPOSAL_NAME,
                  BUNDLE_STATUS, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, 'PROPOSED', ?, CURRENT_TIMESTAMP)
                """,
                bundleId,
                round.roundId(),
                round.conferenceId(),
                round.manuscriptId(),
                proposalName,
                createdBy
        );
        return bundleId;
    }

    public List<PlatformReviewerCandidateRow> listEligibleProposalCandidates(
            long conferenceId,
            long manuscriptId,
            long roundId,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                SELECT CR.REVIEWER_ID,
                       COALESCE(MAX(RMS.MATCHING_SCORE), 0) AS MATCHING_SCORE,
                       CASE
                         WHEN COUNT(CASE WHEN CRL.SEVERITY = 'SOFT' THEN 1 END) > 0 THEN 'SOFT_CONFLICT'
                         ELSE 'ELIGIBLE'
                       END AS ELIGIBILITY_STATUS
                FROM CONFERENCE_REVIEWER CR
                LEFT JOIN REVIEWER_MATCHING_SCORE RMS
                  ON RMS.CONFERENCE_ID = CR.CONFERENCE_ID
                 AND RMS.MANUSCRIPT_ID = ?
                 AND RMS.REVIEWER_ID = CR.REVIEWER_ID
                LEFT JOIN CONFLICT_RELATIONSHIP CRL
                  ON CRL.CONFERENCE_ID = CR.CONFERENCE_ID
                 AND CRL.MANUSCRIPT_ID = ?
                 AND CRL.REVIEWER_ID = CR.REVIEWER_ID
                WHERE CR.CONFERENCE_ID = ?
                  AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM REVIEW_ASSIGNMENT A
                    WHERE A.ROUND_ID = ?
                      AND A.REVIEWER_ID = CR.REVIEWER_ID
                      AND A.TASK_STATUS <> 'CANCELLED'
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM CONFLICT_RELATIONSHIP HARD
                    WHERE HARD.CONFERENCE_ID = CR.CONFERENCE_ID
                      AND HARD.MANUSCRIPT_ID = ?
                      AND HARD.REVIEWER_ID = CR.REVIEWER_ID
                      AND HARD.SEVERITY = 'HARD'
                  )
                GROUP BY CR.REVIEWER_ID
                ORDER BY COALESCE(MAX(RMS.MATCHING_SCORE), 0) DESC, CR.REVIEWER_ID
                FETCH FIRST ? ROWS ONLY
                """,
                (rs, rowNum) -> new PlatformReviewerCandidateRow(
                        rs.getLong("REVIEWER_ID"),
                        rs.getDouble("MATCHING_SCORE"),
                        rs.getString("ELIGIBILITY_STATUS")
                ),
                manuscriptId,
                manuscriptId,
                conferenceId,
                roundId,
                manuscriptId,
                limit
        );
    }

    public long insertAssignmentProposal(
            long bundleId,
            long reviewerId,
            int rankOrder,
            double matchingScore,
            String eligibilityStatus,
            String rationale
    ) {
        long proposalId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL (
                  PROPOSAL_ID, BUNDLE_ID, REVIEWER_ID, RANK_ORDER, MATCHING_SCORE,
                  ELIGIBILITY_STATUS, RATIONALE, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                proposalId,
                bundleId,
                reviewerId,
                rankOrder,
                matchingScore,
                eligibilityStatus,
                rationale
        );
        return proposalId;
    }

    public Optional<PlatformAssignmentProposalBundleRow> findAssignmentProposalBundle(long bundleId) {
        List<PlatformAssignmentProposalBundleRow> rows = jdbcTemplate.query(
                """
                SELECT BUNDLE_ID, ROUND_ID, CONFERENCE_ID, MANUSCRIPT_ID, PROPOSAL_NAME, BUNDLE_STATUS, CREATED_BY
                FROM ASSIGNMENT_PROPOSAL_BUNDLE
                WHERE BUNDLE_ID = ?
                """,
                (rs, rowNum) -> new PlatformAssignmentProposalBundleRow(
                        rs.getLong("BUNDLE_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("PROPOSAL_NAME"),
                        rs.getString("BUNDLE_STATUS"),
                        rs.getLong("CREATED_BY")
                ),
                bundleId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertAssignmentOverrideAudit(long bundleId, long reviewerId, String overrideReason, long overriddenBy) {
        long overrideId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_OVERRIDE_AUDIT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_OVERRIDE_AUDIT (
                  OVERRIDE_ID, BUNDLE_ID, REVIEWER_ID, OVERRIDE_REASON, OVERRIDDEN_BY, OVERRIDDEN_AT
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                overrideId,
                bundleId,
                reviewerId,
                overrideReason,
                overriddenBy
        );
        return overrideId;
    }

    public long insertEmailTemplate(long conferenceId, String templateKey, long createdBy) {
        long templateId = jdbcTemplate.queryForObject("SELECT SEQ_EMAIL_TEMPLATE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EMAIL_TEMPLATE (
                  TEMPLATE_ID, CONFERENCE_ID, TEMPLATE_KEY, ACTIVE_VERSION_ID, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
                """,
                templateId,
                conferenceId,
                templateKey,
                createdBy
        );
        return templateId;
    }

    public long insertEmailTemplateVersion(long templateId, String subjectTemplate, String bodyTemplate, long createdBy) {
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(VERSION_NO), 0) + 1 FROM EMAIL_TEMPLATE_VERSION WHERE TEMPLATE_ID = ?",
                Integer.class,
                templateId
        );
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_EMAIL_TEMPLATE_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EMAIL_TEMPLATE_VERSION (
                  TEMPLATE_VERSION_ID, TEMPLATE_ID, VERSION_NO, SUBJECT_TEMPLATE, BODY_TEMPLATE, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                versionId,
                templateId,
                nextVersion,
                subjectTemplate,
                bodyTemplate,
                createdBy
        );
        jdbcTemplate.update("UPDATE EMAIL_TEMPLATE SET ACTIVE_VERSION_ID = ? WHERE TEMPLATE_ID = ?", versionId, templateId);
        return versionId;
    }

    public Optional<PlatformEmailTemplateRow> findEmailTemplate(long templateId) {
        List<PlatformEmailTemplateRow> rows = jdbcTemplate.query(
                """
                SELECT T.TEMPLATE_ID,
                       T.CONFERENCE_ID,
                       T.TEMPLATE_KEY,
                       V.TEMPLATE_VERSION_ID,
                       V.SUBJECT_TEMPLATE,
                       V.BODY_TEMPLATE,
                       C.ORGANIZER_USER_ID
                FROM EMAIL_TEMPLATE T
                JOIN EMAIL_TEMPLATE_VERSION V ON V.TEMPLATE_VERSION_ID = T.ACTIVE_VERSION_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = T.CONFERENCE_ID
                WHERE T.TEMPLATE_ID = ?
                """,
                (rs, rowNum) -> new PlatformEmailTemplateRow(
                        rs.getLong("TEMPLATE_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("TEMPLATE_KEY"),
                        rs.getLong("TEMPLATE_VERSION_ID"),
                        rs.getString("SUBJECT_TEMPLATE"),
                        rs.getString("BODY_TEMPLATE"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                templateId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertOutboundEmailHistory(
            PlatformEmailTemplateRow template,
            String recipientEmail,
            String subjectText,
            String bodyText,
            long createdBy
    ) {
        long historyId = jdbcTemplate.queryForObject("SELECT SEQ_OUTBOUND_EMAIL_HISTORY.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OUTBOUND_EMAIL_HISTORY (
                  EMAIL_HISTORY_ID, CONFERENCE_ID, TEMPLATE_ID, TEMPLATE_VERSION_ID, RECIPIENT_EMAIL,
                  SUBJECT_TEXT, BODY_TEXT, DELIVERY_STATUS, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'RECORDED', ?, CURRENT_TIMESTAMP)
                """,
                historyId,
                template.conferenceId(),
                template.templateId(),
                template.templateVersionId(),
                recipientEmail,
                subjectText,
                bodyText,
                createdBy
        );
        return historyId;
    }

    public long insertOfflineReviewImportBatch(long assignmentId, long reviewerId, int rowCount, int validRowCount, int errorCount) {
        long batchId = jdbcTemplate.queryForObject("SELECT SEQ_OFFLINE_REVIEW_IMPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OFFLINE_REVIEW_IMPORT_BATCH (
                  BATCH_ID, ASSIGNMENT_ID, REVIEWER_ID, BATCH_STATUS, ROW_COUNT,
                  VALID_ROW_COUNT, ERROR_COUNT, CREATED_AT, APPLIED_AT
                ) VALUES (?, ?, ?, 'PREVIEWED', ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                batchId,
                assignmentId,
                reviewerId,
                rowCount,
                validRowCount,
                errorCount
        );
        return batchId;
    }

    public long insertOfflineReviewImportRow(
            long batchId,
            int rowNo,
            String rowStatus,
            Integer overallScore,
            String recommendation,
            String commentsToAuthor,
            String errorMessage
    ) {
        long rowId = jdbcTemplate.queryForObject("SELECT SEQ_OFFLINE_REVIEW_IMPORT_ROW.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OFFLINE_REVIEW_IMPORT_ROW (
                  ROW_ID, BATCH_ID, ROW_NO, ROW_STATUS, OVERALL_SCORE,
                  RECOMMENDATION, COMMENTS_TO_AUTHOR, ERROR_MESSAGE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rowId,
                batchId,
                rowNo,
                rowStatus,
                overallScore,
                recommendation,
                commentsToAuthor,
                errorMessage
        );
        return rowId;
    }

    public Optional<PlatformOfflineReviewBatchRow> findOfflineReviewImportBatch(long batchId) {
        List<PlatformOfflineReviewBatchRow> rows = jdbcTemplate.query(
                """
                SELECT B.BATCH_ID,
                       B.ASSIGNMENT_ID,
                       B.REVIEWER_ID,
                       B.BATCH_STATUS,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID
                FROM OFFLINE_REVIEW_IMPORT_BATCH B
                JOIN REVIEW_ASSIGNMENT A ON A.ASSIGNMENT_ID = B.ASSIGNMENT_ID
                WHERE B.BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformOfflineReviewBatchRow(
                        rs.getLong("BATCH_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("BATCH_STATUS"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID")
                ),
                batchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformOfflineReviewRow> listValidOfflineReviewRows(long batchId) {
        return jdbcTemplate.query(
                """
                SELECT ROW_ID, OVERALL_SCORE, RECOMMENDATION, COMMENTS_TO_AUTHOR
                FROM OFFLINE_REVIEW_IMPORT_ROW
                WHERE BATCH_ID = ?
                  AND ROW_STATUS = 'VALID'
                ORDER BY ROW_NO, ROW_ID
                """,
                (rs, rowNum) -> new PlatformOfflineReviewRow(
                        rs.getLong("ROW_ID"),
                        rs.getInt("OVERALL_SCORE"),
                        rs.getString("RECOMMENDATION"),
                        rs.getString("COMMENTS_TO_AUTHOR")
                ),
                batchId
        );
    }

    public void insertReviewReportFromOfflineRow(PlatformOfflineReviewBatchRow batch, PlatformOfflineReviewRow row) {
        long reviewId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_REPORT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_REPORT (
                  REVIEW_ID, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  NOVELTY_SCORE, METHOD_SCORE, EXPERIMENT_SCORE, WRITING_SCORE, OVERALL_SCORE,
                  CONFIDENCE_LEVEL, STRENGTHS, WEAKNESSES, COMMENTS_TO_AUTHOR, COMMENTS_TO_CHAIR,
                  RECOMMENDATION, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MEDIUM', NULL, NULL, ?, NULL, ?, CURRENT_TIMESTAMP)
                """,
                reviewId,
                batch.assignmentId(),
                batch.roundId(),
                batch.manuscriptId(),
                batch.reviewerId(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.commentsToAuthor(),
                row.recommendation()
        );
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'SUBMITTED', SUBMITTED_AT = CURRENT_TIMESTAMP WHERE ASSIGNMENT_ID = ?",
                batch.assignmentId()
        );
    }

    public void markOfflineReviewRowApplied(long rowId) {
        jdbcTemplate.update("UPDATE OFFLINE_REVIEW_IMPORT_ROW SET ROW_STATUS = 'APPLIED' WHERE ROW_ID = ?", rowId);
    }

    public void markOfflineReviewBatchApplied(long batchId) {
        jdbcTemplate.update(
                "UPDATE OFFLINE_REVIEW_IMPORT_BATCH SET BATCH_STATUS = 'APPLIED', APPLIED_AT = CURRENT_TIMESTAMP WHERE BATCH_ID = ?",
                batchId
        );
    }

    public long insertCameraReadyFile(
            long manuscriptId,
            long versionId,
            long submittedBy,
            String fileName,
            long fileSize,
            String checksumSha256,
            boolean copyrightConfirmed,
            String licenseType
    ) {
        long fileId = jdbcTemplate.queryForObject("SELECT SEQ_CAMERA_READY_FILE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CAMERA_READY_FILE (
                  CAMERA_READY_FILE_ID, MANUSCRIPT_ID, VERSION_ID, SUBMITTED_BY, FILE_NAME,
                  FILE_SIZE, CHECKSUM_SHA256, COPYRIGHT_CONFIRMED, LICENSE_TYPE, FILE_STATUS,
                  DECISION_NOTE, SUBMITTED_AT, DECIDED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', NULL, CURRENT_TIMESTAMP, NULL)
                """,
                fileId,
                manuscriptId,
                versionId,
                submittedBy,
                fileName,
                fileSize,
                checksumSha256,
                copyrightConfirmed ? 1 : 0,
                licenseType
        );
        return fileId;
    }

    public Optional<PlatformCameraReadyFileRow> findCameraReadyFile(long fileId) {
        List<PlatformCameraReadyFileRow> rows = jdbcTemplate.query(
                """
                SELECT F.CAMERA_READY_FILE_ID,
                       F.MANUSCRIPT_ID,
                       F.VERSION_ID,
                       F.SUBMITTED_BY,
                       F.FILE_STATUS,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM CAMERA_READY_FILE F
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = F.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE F.CAMERA_READY_FILE_ID = ?
                """,
                (rs, rowNum) -> new PlatformCameraReadyFileRow(
                        rs.getLong("CAMERA_READY_FILE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("FILE_STATUS"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                fileId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void decideCameraReadyFile(long fileId, String fileStatus, String decisionNote) {
        jdbcTemplate.update(
                """
                UPDATE CAMERA_READY_FILE
                SET FILE_STATUS = ?,
                    DECISION_NOTE = ?,
                    DECIDED_AT = CURRENT_TIMESTAMP
                WHERE CAMERA_READY_FILE_ID = ?
                """,
                fileStatus,
                decisionNote,
                fileId
        );
    }

    public long upsertPublicationMetadata(
            long conferenceId,
            long manuscriptId,
            String doi,
            String indexKeywords,
            String publicationStatus,
            long updatedBy
    ) {
        Long existingId = jdbcTemplate.query(
                "SELECT PUBLICATION_METADATA_ID FROM PUBLICATION_METADATA WHERE MANUSCRIPT_ID = ?",
                rs -> rs.next() ? rs.getLong("PUBLICATION_METADATA_ID") : null,
                manuscriptId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE PUBLICATION_METADATA
                    SET CONFERENCE_ID = ?,
                        DOI = ?,
                        INDEX_KEYWORDS = ?,
                        PUBLICATION_STATUS = ?,
                        UPDATED_BY = ?,
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE PUBLICATION_METADATA_ID = ?
                    """,
                    conferenceId,
                    doi,
                    indexKeywords,
                    publicationStatus,
                    updatedBy,
                    existingId
            );
            return existingId;
        }
        long metadataId = jdbcTemplate.queryForObject("SELECT SEQ_PUBLICATION_METADATA.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PUBLICATION_METADATA (
                  PUBLICATION_METADATA_ID, CONFERENCE_ID, MANUSCRIPT_ID, DOI,
                  INDEX_KEYWORDS, PUBLICATION_STATUS, UPDATED_BY, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                metadataId,
                conferenceId,
                manuscriptId,
                doi,
                indexKeywords,
                publicationStatus,
                updatedBy
        );
        return metadataId;
    }

    public int countProceedingsReadyPapers(long conferenceId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM MANUSCRIPT M
                JOIN PUBLICATION_METADATA P ON P.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                WHERE COALESCE(M.CONFERENCE_ID, 0) = ?
                  AND M.CURRENT_STATUS = 'ACCEPTED'
                  AND P.PUBLICATION_STATUS = 'READY_FOR_PROCEEDINGS'
                """,
                Integer.class,
                conferenceId
        );
        return count == null ? 0 : count;
    }

    public long insertProceedingsExportPreview(long conferenceId, String exportName, int paperCount, String previewJson, long createdBy) {
        long exportBatchId = jdbcTemplate.queryForObject("SELECT SEQ_PROCEEDINGS_EXPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PROCEEDINGS_EXPORT_BATCH (
                  EXPORT_BATCH_ID, CONFERENCE_ID, EXPORT_NAME, EXPORT_STATUS, PAPER_COUNT,
                  PREVIEW_JSON, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, 'PREVIEWED', ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                exportBatchId,
                conferenceId,
                exportName,
                paperCount,
                previewJson,
                createdBy
        );
        return exportBatchId;
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

record PlatformManuscriptRow(
        long manuscriptId,
        long submitterId,
        long conferenceId,
        Long currentVersionId,
        String currentStatus,
        Long organizerUserId
) {
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

record PlatformReviewRoundRow(long roundId, long manuscriptId, long versionId, long conferenceId, Long organizerUserId) {
}

record PlatformReviewerInvitationRow(
        long invitationId,
        long conferenceId,
        long reviewerId,
        String invitationStatus,
        String invitationMessage,
        long invitedBy,
        Timestamp invitedAt,
        Timestamp respondedAt,
        Timestamp expiresAt
) {
}

record PlatformExternalDelegationRow(
        long delegationId,
        long assignmentId,
        long manuscriptId,
        long requestedBy,
        String externalName,
        String externalEmail,
        String delegationStatus,
        String decisionNote,
        long conferenceId,
        Long organizerUserId
) {
}

record PlatformConflictRelationshipRow(
        long conflictRelationshipId,
        long conferenceId,
        long manuscriptId,
        long reviewerId,
        String conflictType,
        String conflictSource,
        String severity,
        String note
) {
}

record PlatformReviewerCandidateRow(long reviewerId, double matchingScore, String eligibilityStatus) {
}

record PlatformAssignmentProposalBundleRow(
        long bundleId,
        long roundId,
        long conferenceId,
        long manuscriptId,
        String proposalName,
        String bundleStatus,
        long createdBy
) {
}

record PlatformEmailTemplateRow(
        long templateId,
        long conferenceId,
        String templateKey,
        long templateVersionId,
        String subjectTemplate,
        String bodyTemplate,
        Long organizerUserId
) {
}

record PlatformOfflineReviewBatchRow(
        long batchId,
        long assignmentId,
        long reviewerId,
        String batchStatus,
        long roundId,
        long manuscriptId,
        long versionId
) {
}

record PlatformOfflineReviewRow(long rowId, int overallScore, String recommendation, String commentsToAuthor) {
}

record PlatformCameraReadyFileRow(
        long cameraReadyFileId,
        long manuscriptId,
        long versionId,
        long submittedBy,
        String fileStatus,
        long conferenceId,
        Long organizerUserId
) {
}
