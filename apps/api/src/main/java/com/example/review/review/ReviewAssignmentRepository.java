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

    public int countActiveAssignmentsForRound(long roundId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM REVIEW_ASSIGNMENT
                WHERE ROUND_ID = ?
                  AND TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE')
                """,
                Integer.class,
                roundId
        );
        return count == null ? 0 : count;
    }

    public List<AssignmentCandidateDetailRow> listEligibleCandidates(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       CR.REVIEWER_ID,
                       U.REAL_NAME AS REVIEWER_NAME,
                       U.INSTITUTION,
                       CR.MAX_LOAD,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM REVIEW_ASSIGNMENT A
                         WHERE A.REVIEWER_ID = CR.REVIEWER_ID
                           AND A.TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE')
                       ), 0) AS CURRENT_LOAD,
                       COALESCE(B.BID_VALUE, 'NEUTRAL') AS BID_VALUE
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                JOIN CONFERENCE_REVIEWER CR
                  ON CR.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                 AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                JOIN SYS_USER U ON U.USER_ID = CR.REVIEWER_ID
                LEFT JOIN REVIEWER_BID B
                  ON B.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                 AND B.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND B.REVIEWER_ID = CR.REVIEWER_ID
                WHERE R.ROUND_ID = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM MANUSCRIPT_AUTHOR MA
                    WHERE MA.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                      AND MA.USER_ID = CR.REVIEWER_ID
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM CONFLICT_CHECK_RECORD C
                    WHERE C.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                      AND C.REVIEWER_ID = CR.REVIEWER_ID
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM REVIEW_ASSIGNMENT RA
                    WHERE RA.ROUND_ID = R.ROUND_ID
                      AND RA.REVIEWER_ID = CR.REVIEWER_ID
                      AND RA.TASK_STATUS <> 'CANCELLED'
                  )
                  AND COALESCE(B.BID_VALUE, 'NEUTRAL') <> 'DECLINE'
                  AND (
                    SELECT COUNT(*)
                    FROM REVIEW_ASSIGNMENT A
                    WHERE A.REVIEWER_ID = CR.REVIEWER_ID
                      AND A.TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE')
                  ) < CR.MAX_LOAD
                ORDER BY
                  CASE COALESCE(B.BID_VALUE, 'NEUTRAL')
                    WHEN 'WANT_TO_REVIEW' THEN 1
                    WHEN 'NEUTRAL' THEN 2
                    ELSE 3
                  END,
                  CURRENT_LOAD ASC,
                  CR.REVIEWER_ID ASC
                """,
                (rs, rowNum) -> new AssignmentCandidateDetailRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("REVIEWER_NAME"),
                        rs.getString("INSTITUTION"),
                        rs.getInt("CURRENT_LOAD"),
                        rs.getInt("MAX_LOAD"),
                        rs.getString("BID_VALUE")
                ),
                roundId
        );
    }

    public List<ReviewRoundRow> listAssignableRoundsForConference(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT R.ROUND_ID, R.MANUSCRIPT_ID, R.ROUND_NO, R.VERSION_ID, R.ROUND_STATUS,
                       R.ASSIGNMENT_STRATEGY, R.SCREENING_REQUIRED, R.DEADLINE_AT, R.CREATED_BY
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                WHERE COALESCE(M.CONFERENCE_ID, 0) = ?
                  AND R.ROUND_STATUS IN ('PENDING', 'IN_PROGRESS')
                ORDER BY R.ROUND_ID
                """,
                (rs, rowNum) -> new ReviewRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("ROUND_STATUS"),
                        rs.getString("ASSIGNMENT_STRATEGY"),
                        rs.getInt("SCREENING_REQUIRED") == 1,
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getLong("CREATED_BY")
                ),
                conferenceId
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

record AssignmentCandidateDetailRow(
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        String reviewerName,
        String institution,
        int currentLoad,
        int maxLoad,
        String bidValue
) {
}
