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

    public void markSubmitted(long assignmentId, Timestamp submittedAt) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'SUBMITTED', SUBMITTED_AT = ? WHERE ASSIGNMENT_ID = ?",
                submittedAt,
                assignmentId
        );
    }

    public void cancelOpenAssignmentsForRound(long roundId) {
        jdbcTemplate.update(
                """
                UPDATE REVIEW_ASSIGNMENT
                SET TASK_STATUS = 'CANCELLED'
                WHERE ROUND_ID = ?
                  AND TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'OVERDUE')
                """,
                roundId
        );
    }

    public Optional<AssignmentEligibilityRow> findEligibilityForAssignment(long manuscriptId, long reviewerId) {
        List<AssignmentEligibilityRow> rows = jdbcTemplate.query(
                """
                SELECT CASE WHEN CR.CONFERENCE_REVIEWER_ID IS NOT NULL THEN 1 ELSE 0 END AS ACTIVE_CONFERENCE_REVIEWER,
                       CASE WHEN MA.MANUSCRIPT_AUTHOR_ID IS NOT NULL THEN 1 ELSE 0 END AS MANUSCRIPT_AUTHOR,
                       CASE WHEN C.CONFLICT_ID IS NOT NULL THEN 1 ELSE 0 END AS HAS_CONFLICT,
                       CASE WHEN COALESCE(B.BID_VALUE, 'NEUTRAL') = 'DECLINE' THEN 1 ELSE 0 END AS DECLINED_BID,
                       COALESCE(LOADS.CURRENT_LOAD, 0) AS CURRENT_LOAD,
                       CR.MAX_LOAD AS MAX_LOAD
                FROM MANUSCRIPT M
                LEFT JOIN CONFERENCE_REVIEWER CR
                  ON CR.CONFERENCE_ID = M.CONFERENCE_ID
                 AND CR.REVIEWER_ID = ?
                 AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                LEFT JOIN MANUSCRIPT_AUTHOR MA
                  ON MA.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND MA.USER_ID = ?
                LEFT JOIN CONFLICT_CHECK_RECORD C
                  ON C.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND C.REVIEWER_ID = ?
                LEFT JOIN REVIEWER_BID B
                  ON B.CONFERENCE_ID = M.CONFERENCE_ID
                 AND B.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND B.REVIEWER_ID = ?
                LEFT JOIN (
                  SELECT REVIEWER_ID, COUNT(*) AS CURRENT_LOAD
                  FROM REVIEW_ASSIGNMENT
                  WHERE TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE')
                  GROUP BY REVIEWER_ID
                ) LOADS ON LOADS.REVIEWER_ID = ?
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new AssignmentEligibilityRow(
                        rs.getInt("ACTIVE_CONFERENCE_REVIEWER") == 1,
                        rs.getInt("MANUSCRIPT_AUTHOR") == 1,
                        rs.getInt("HAS_CONFLICT") == 1,
                        rs.getInt("DECLINED_BID") == 1,
                        rs.getInt("CURRENT_LOAD"),
                        rs.getObject("MAX_LOAD", Integer.class)
                ),
                reviewerId,
                reviewerId,
                reviewerId,
                reviewerId,
                reviewerId,
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
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

record AssignmentEligibilityRow(
        boolean activeConferenceReviewer,
        boolean manuscriptAuthor,
        boolean hasConflict,
        boolean declinedBid,
        int currentLoad,
        Integer maxLoad
) {
    boolean eligible() {
        return activeConferenceReviewer
                && !manuscriptAuthor
                && !hasConflict
                && !declinedBid
                && maxLoad != null
                && currentLoad < maxLoad;
    }
}
