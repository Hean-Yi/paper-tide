package com.example.review.workflow;

import com.example.review.auth.CurrentUserPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowQueryService {
    private final JdbcTemplate jdbcTemplate;

    public WorkflowQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReviewerAssignmentSummary> listReviewerAssignments(CurrentUserPrincipal principal) {
        requireRole(principal, "REVIEWER");
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
                principal.userId()
        );
    }

    public ReviewerAssignmentDetail getReviewerAssignment(CurrentUserPrincipal principal, long assignmentId) {
        List<ReviewerAssignmentDetail> rows = jdbcTemplate.query(
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
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review assignment not found");
        }
        ReviewerAssignmentDetail detail = rows.getFirst();
        if (!hasRole(principal, "CHAIR") && !hasRole(principal, "ADMIN")
                && (!hasRole(principal, "REVIEWER") || detail.reviewerId() != principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view review assignment");
        }
        return detail;
    }

    public List<ScreeningQueueItem> listScreeningQueue(CurrentUserPrincipal principal) {
        requireChairOrAdmin(principal);
        return jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       V.VERSION_NO,
                       V.TITLE,
                       V.PDF_FILE_NAME,
                       V.PDF_FILE_SIZE
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.CURRENT_STATUS IN ('SUBMITTED', 'REVISED_SUBMITTED', 'UNDER_SCREENING')
                ORDER BY M.SUBMITTED_AT NULLS LAST, M.MANUSCRIPT_ID
                """,
                (rs, rowNum) -> new ScreeningQueueItem(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        rs.getString("TITLE"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getInt("CURRENT_ROUND_NO"),
                        rs.getString("BLIND_MODE"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getString("PDF_FILE_NAME"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                )
        );
    }

    public List<DecisionWorkbenchItem> listDecisionWorkbench(CurrentUserPrincipal principal) {
        requireChairOrAdmin(principal);
        List<DecisionWorkbenchBase> rounds = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       R.ROUND_NO,
                       R.ROUND_STATUS,
                       R.DEADLINE_AT,
                       M.CURRENT_STATUS,
                       M.LAST_DECISION_CODE,
                       V.TITLE,
                       V.VERSION_NO,
                       (SELECT COUNT(*) FROM REVIEW_ASSIGNMENT A WHERE A.ROUND_ID = R.ROUND_ID) AS ASSIGNMENT_COUNT,
                       (SELECT COUNT(*) FROM REVIEW_REPORT RP WHERE RP.ROUND_ID = R.ROUND_ID) AS SUBMITTED_REVIEW_COUNT,
                       (SELECT COUNT(*)
                        FROM CONFLICT_CHECK_RECORD C
                        WHERE C.ASSIGNMENT_ID IN (
                          SELECT A.ASSIGNMENT_ID FROM REVIEW_ASSIGNMENT A WHERE A.ROUND_ID = R.ROUND_ID
                        )) AS CONFLICT_COUNT
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = R.VERSION_ID
                WHERE R.ROUND_STATUS IN ('PENDING', 'IN_PROGRESS')
                ORDER BY R.ROUND_ID
                """,
                (rs, rowNum) -> new DecisionWorkbenchBase(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getString("ROUND_STATUS"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getString("LAST_DECISION_CODE"),
                        rs.getString("TITLE"),
                        rs.getInt("VERSION_NO"),
                        rs.getInt("ASSIGNMENT_COUNT"),
                        rs.getInt("SUBMITTED_REVIEW_COUNT"),
                        rs.getInt("CONFLICT_COUNT")
                )
        );
        return rounds.stream()
                .map(round -> new DecisionWorkbenchItem(
                        round.roundId(),
                        round.manuscriptId(),
                        round.versionId(),
                        round.versionNo(),
                        round.roundNo(),
                        round.title(),
                        round.currentStatus(),
                        round.roundStatus(),
                        round.deadlineAt(),
                        round.assignmentCount(),
                        round.submittedReviewCount(),
                        round.conflictCount(),
                        round.lastDecisionCode(),
                        listAssignmentsForRound(round.roundId())
                ))
                .toList();
    }

    private List<DecisionAssignmentItem> listAssignmentsForRound(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT ASSIGNMENT_ID,
                       REVIEWER_ID,
                       TASK_STATUS,
                       ASSIGNED_AT,
                       ACCEPTED_AT,
                       DEADLINE_AT,
                       SUBMITTED_AT,
                       REASSIGNED_FROM_ID
                FROM REVIEW_ASSIGNMENT
                WHERE ROUND_ID = ?
                ORDER BY ASSIGNMENT_ID
                """,
                (rs, rowNum) -> new DecisionAssignmentItem(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getTimestamp("ASSIGNED_AT"),
                        rs.getTimestamp("ACCEPTED_AT"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getObject("REASSIGNED_FROM_ID", Long.class)
                ),
                roundId
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

    private void requireChairOrAdmin(CurrentUserPrincipal principal) {
        if (!hasRole(principal, "CHAIR") && !hasRole(principal, "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chair or admin role is required");
        }
    }

    private void requireRole(CurrentUserPrincipal principal, String role) {
        if (!hasRole(principal, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role + " role is required");
        }
    }

    private boolean hasRole(CurrentUserPrincipal principal, String role) {
        return principal != null && principal.roles().contains(role);
    }
}

record ReviewerAssignmentSummary(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        int versionNo,
        String title,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp declinedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        String recommendation
) {
}

record ReviewerAssignmentDetail(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int versionNo,
        String title,
        String abstractText,
        String keywords,
        String pdfFileName,
        Long pdfFileSize,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp declinedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        String recommendation
) {
}

record ScreeningQueueItem(
        long manuscriptId,
        long versionId,
        int versionNo,
        String title,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String pdfFileName,
        Long pdfFileSize
) {
}

record DecisionWorkbenchItem(
        long roundId,
        long manuscriptId,
        long versionId,
        int versionNo,
        int roundNo,
        String title,
        String currentStatus,
        String roundStatus,
        Timestamp deadlineAt,
        int assignmentCount,
        int submittedReviewCount,
        int conflictCount,
        String lastDecisionCode,
        List<DecisionAssignmentItem> assignments
) {
}

record DecisionAssignmentItem(
        long assignmentId,
        long reviewerId,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        Long reassignedFromId
) {
}

record DecisionWorkbenchBase(
        long roundId,
        long manuscriptId,
        long versionId,
        int roundNo,
        String roundStatus,
        Timestamp deadlineAt,
        String currentStatus,
        String lastDecisionCode,
        String title,
        int versionNo,
        int assignmentCount,
        int submittedReviewCount,
        int conflictCount
) {
}
