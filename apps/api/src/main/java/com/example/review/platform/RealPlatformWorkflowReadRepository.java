package com.example.review.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformWorkflowReadRepository {
    private static final TypeReference<Map<String, Object>> ANSWERS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RealPlatformWorkflowReadRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ReviewerFormAccessRow> findReviewerFormAccess(long assignmentId) {
        List<ReviewerFormAccessRow> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID, A.REVIEWER_ID, M.CONFERENCE_ID
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new ReviewerFormAccessRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getLong("CONFERENCE_ID")
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<FormDefinitionResponse> findActiveReviewForm(long conferenceId) {
        return findActiveForm(conferenceId, "REVIEW");
    }

    public Optional<FormDefinitionResponse> findActiveForm(long conferenceId, String formType) {
        List<FormDefinitionResponse> rows = jdbcTemplate.query(
                """
                SELECT FORM_ID, CONFERENCE_ID, FORM_TYPE, FORM_NAME
                FROM CONFERENCE_FORM_DEFINITION
                WHERE CONFERENCE_ID = ?
                  AND FORM_TYPE = ?
                  AND ACTIVE_FLAG = 1
                ORDER BY UPDATED_AT DESC, FORM_ID DESC
                FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> {
                    long formId = rs.getLong("FORM_ID");
                    return new FormDefinitionResponse(
                            formId,
                            rs.getLong("CONFERENCE_ID"),
                            rs.getString("FORM_TYPE"),
                            rs.getString("FORM_NAME"),
                            listFormFields(formId)
                    );
                },
                conferenceId,
                formType
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<ReviewFormResponse> findReviewFormResponse(long assignmentId, long formId) {
        List<ReviewFormResponse> rows = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID, FORM_ID, ASSIGNMENT_ID, RESPONSE_STATUS, ANSWERS_JSON, SUBMITTED_AT
                FROM REVIEW_FORM_RESPONSE
                WHERE ASSIGNMENT_ID = ?
                  AND FORM_ID = ?
                """,
                this::mapReviewFormResponse,
                assignmentId,
                formId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<WorkflowFormResponse> findWorkflowFormResponse(long subjectId, long formId) {
        List<WorkflowFormResponse> rows = jdbcTemplate.query(
                """
                SELECT RESPONSE_ID, FORM_ID, SUBJECT_TYPE, SUBJECT_ID, RESPONSE_STATUS, ANSWERS_JSON, SUBMITTED_AT
                FROM WORKFLOW_FORM_RESPONSE
                WHERE SUBJECT_TYPE = 'MANUSCRIPT'
                  AND SUBJECT_ID = ?
                  AND FORM_ID = ?
                """,
                (rs, rowNum) -> new WorkflowFormResponse(
                        rs.getLong("RESPONSE_ID"),
                        rs.getLong("FORM_ID"),
                        rs.getString("SUBJECT_TYPE"),
                        rs.getLong("SUBJECT_ID"),
                        rs.getString("RESPONSE_STATUS"),
                        fromAnswersJson(rs.getString("ANSWERS_JSON")),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                subjectId,
                formId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<ReviewFormRevisionResponse> listReviewFormRevisions(long assignmentId) {
        return jdbcTemplate.query(
                """
                SELECT RV.REVISION_ID, RV.REVISION_NO, RV.ANSWERS_JSON, RV.SUBMITTED_AT
                FROM REVIEW_FORM_RESPONSE_REVISION RV
                JOIN REVIEW_FORM_RESPONSE R ON R.RESPONSE_ID = RV.RESPONSE_ID
                WHERE R.ASSIGNMENT_ID = ?
                ORDER BY RV.REVISION_NO, RV.REVISION_ID
                """,
                (rs, rowNum) -> new ReviewFormRevisionResponse(
                        rs.getLong("REVISION_ID"),
                        rs.getInt("REVISION_NO"),
                        fromAnswersJson(rs.getString("ANSWERS_JSON")),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                assignmentId
        );
    }

    private List<FormFieldResponse> listFormFields(long formId) {
        return jdbcTemplate.query(
                """
                SELECT FIELD_ID, FIELD_KEY, FIELD_LABEL, FIELD_TYPE, REQUIRED_FLAG, VISIBILITY, DISPLAY_ORDER
                FROM CONFERENCE_FORM_FIELD
                WHERE FORM_ID = ?
                ORDER BY DISPLAY_ORDER, FIELD_ID
                """,
                (rs, rowNum) -> new FormFieldResponse(
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

    private ReviewFormResponse mapReviewFormResponse(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewFormResponse(
                rs.getLong("RESPONSE_ID"),
                rs.getLong("FORM_ID"),
                rs.getLong("ASSIGNMENT_ID"),
                rs.getString("RESPONSE_STATUS"),
                fromAnswersJson(rs.getString("ANSWERS_JSON")),
                rs.getTimestamp("SUBMITTED_AT")
        );
    }

    private Map<String, Object> fromAnswersJson(String json) {
        try {
            return objectMapper.readValue(json, ANSWERS_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored review form answers are invalid JSON", ex);
        }
    }
}
