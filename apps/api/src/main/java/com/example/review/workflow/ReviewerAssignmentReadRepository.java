package com.example.review.workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewerAssignmentReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewerAssignmentReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReviewerAssignmentSummary> findSummariesByReviewerId(long reviewerId) {
        return jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       V.VERSION_NO,
                       V.TITLE,
                       A.TASK_STATUS,
                       A.ASSIGNED_AT,
                       A.ACCEPTED_AT,
                       A.DECLINED_AT,
                       A.DEADLINE_AT,
                       A.SUBMITTED_AT,
                       R.RECOMMENDATION
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = A.VERSION_ID
                LEFT JOIN REVIEW_REPORT R ON R.ASSIGNMENT_ID = A.ASSIGNMENT_ID
                WHERE A.REVIEWER_ID = ?
                ORDER BY A.ASSIGNMENT_ID
                """,
                (rs, rowNum) -> new ReviewerAssignmentSummary(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        rs.getString("TITLE"),
                        rs.getString("TASK_STATUS"),
                        rs.getTimestamp("ASSIGNED_AT"),
                        rs.getTimestamp("ACCEPTED_AT"),
                        rs.getTimestamp("DECLINED_AT"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getString("RECOMMENDATION")
                ),
                reviewerId
        );
    }

    public ReviewerInterfaceChoiceState findInterfaceChoiceState(long reviewerId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS ACTIVE_ASSIGNMENT_COUNT
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                JOIN CONFERENCE C ON C.CONFERENCE_ID = M.CONFERENCE_ID
                WHERE A.REVIEWER_ID = ?
                  AND A.TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'OVERDUE')
                  AND C.CONFERENCE_STATUS <> 'CLOSED'
                """,
                (rs, rowNum) -> new ReviewerInterfaceChoiceState(rs.getInt("ACTIVE_ASSIGNMENT_COUNT")),
                reviewerId
        );
    }

    public List<ReviewerAssignmentDetail> findDetailByAssignmentId(long assignmentId) {
        return jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       V.VERSION_NO,
                       V.TITLE,
                       V.ABSTRACT AS ABSTRACT_TEXT,
                       V.KEYWORDS,
                       V.PDF_FILE_NAME,
                       V.PDF_FILE_SIZE,
                       A.TASK_STATUS,
                       A.ASSIGNED_AT,
                       A.ACCEPTED_AT,
                       A.DECLINED_AT,
                       A.DEADLINE_AT,
                       A.SUBMITTED_AT,
                       R.RECOMMENDATION
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = A.VERSION_ID
                LEFT JOIN REVIEW_REPORT R ON R.ASSIGNMENT_ID = A.ASSIGNMENT_ID
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> mapAssignmentDetail(rs),
                assignmentId
        );
    }

    private ReviewerAssignmentDetail mapAssignmentDetail(ResultSet rs) throws SQLException {
        return new ReviewerAssignmentDetail(
                rs.getLong("ASSIGNMENT_ID"),
                rs.getLong("ROUND_ID"),
                rs.getLong("MANUSCRIPT_ID"),
                rs.getLong("VERSION_ID"),
                rs.getLong("REVIEWER_ID"),
                rs.getInt("VERSION_NO"),
                rs.getString("TITLE"),
                rs.getString("ABSTRACT_TEXT"),
                rs.getString("KEYWORDS"),
                rs.getString("PDF_FILE_NAME"),
                rs.getObject("PDF_FILE_SIZE", Long.class),
                rs.getString("TASK_STATUS"),
                rs.getTimestamp("ASSIGNED_AT"),
                rs.getTimestamp("ACCEPTED_AT"),
                rs.getTimestamp("DECLINED_AT"),
                rs.getTimestamp("DEADLINE_AT"),
                rs.getTimestamp("SUBMITTED_AT"),
                rs.getString("RECOMMENDATION")
        );
    }
}

record ReviewerInterfaceChoiceState(int activeAssignmentCount) {
    boolean shouldPrompt() {
        return activeAssignmentCount > 0;
    }
}
