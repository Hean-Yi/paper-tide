package com.example.review.review;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewAssignmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextAssignmentId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
    }

    public void insert(
            long assignmentId,
            long roundId,
            long manuscriptId,
            long versionId,
            long reviewerId,
            String taskStatus,
            Timestamp deadlineAt,
            Long reassignedFromId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (
                  ASSIGNMENT_ID,
                  ROUND_ID,
                  MANUSCRIPT_ID,
                  VERSION_ID,
                  REVIEWER_ID,
                  TASK_STATUS,
                  ASSIGNED_AT,
                  ACCEPTED_AT,
                  DECLINED_AT,
                  DECLINE_REASON,
                  DEADLINE_AT,
                  SUBMITTED_AT,
                  REASSIGNED_FROM_ID
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL, NULL, NULL, ?, NULL, ?)
                """,
                assignmentId,
                roundId,
                manuscriptId,
                versionId,
                reviewerId,
                taskStatus,
                deadlineAt,
                reassignedFromId
        );
    }

    public Optional<ReviewAssignmentRow> findById(long assignmentId) {
        List<ReviewAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS,
                       ASSIGNED_AT, ACCEPTED_AT, DECLINED_AT, DECLINE_REASON, DEADLINE_AT, SUBMITTED_AT, REASSIGNED_FROM_ID
                FROM REVIEW_ASSIGNMENT
                WHERE ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new ReviewAssignmentRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getTimestamp("ASSIGNED_AT"),
                        rs.getTimestamp("ACCEPTED_AT"),
                        rs.getTimestamp("DECLINED_AT"),
                        rs.getString("DECLINE_REASON"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getObject("REASSIGNED_FROM_ID", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<ReviewAssignmentRow> findByIdForUpdate(long assignmentId) {
        List<ReviewAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS,
                       ASSIGNED_AT, ACCEPTED_AT, DECLINED_AT, DECLINE_REASON, DEADLINE_AT, SUBMITTED_AT, REASSIGNED_FROM_ID
                FROM REVIEW_ASSIGNMENT
                WHERE ASSIGNMENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ReviewAssignmentRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getTimestamp("ASSIGNED_AT"),
                        rs.getTimestamp("ACCEPTED_AT"),
                        rs.getTimestamp("DECLINED_AT"),
                        rs.getString("DECLINE_REASON"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getObject("REASSIGNED_FROM_ID", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void markAccepted(long assignmentId, Timestamp acceptedAt) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'ACCEPTED', ACCEPTED_AT = ? WHERE ASSIGNMENT_ID = ?",
                acceptedAt,
                assignmentId
        );
    }

    public void markDeclined(long assignmentId, Timestamp declinedAt, String declineReason) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'DECLINED', DECLINED_AT = ?, DECLINE_REASON = ? WHERE ASSIGNMENT_ID = ?",
                declinedAt,
                declineReason,
                assignmentId
        );
    }

    public void updateStatus(long assignmentId, String taskStatus) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = ? WHERE ASSIGNMENT_ID = ?",
                taskStatus,
                assignmentId
        );
    }
}

record ReviewAssignmentRow(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp declinedAt,
        String declineReason,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        Long reassignedFromId
) {
}
