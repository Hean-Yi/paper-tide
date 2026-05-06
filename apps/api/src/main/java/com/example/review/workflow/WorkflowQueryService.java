package com.example.review.workflow;

import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
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
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;

    public WorkflowQueryService(
            JdbcTemplate jdbcTemplate,
            AnalysisIntentRepository intentRepository,
            AnalysisProjectionRepository projectionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.intentRepository = intentRepository;
        this.projectionRepository = projectionRepository;
    }

    public List<ReviewerAssignmentSummary> listReviewerAssignments(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "REVIEWER");
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
        if (!RoleGuard.hasRole(principal, "CHAIR") && !RoleGuard.hasRole(principal, "ADMIN")
                && (!RoleGuard.hasRole(principal, "REVIEWER") || detail.reviewerId() != principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view review assignment");
        }
        return detail;
    }

    public List<ScreeningQueueItem> listScreeningQueue(CurrentUserPrincipal principal) {
        RoleGuard.requireChairOrAdmin(principal);
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

    public List<AdminAnalysisMonitorItem> listAdminAnalysisMonitor(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "ADMIN");
        return jdbcTemplate.query(
                """
                SELECT I.INTENT_ID,
                       I.ANALYSIS_TYPE,
                       I.BUSINESS_STATUS,
                       I.EXECUTION_JOB_ID,
                       I.BUSINESS_ANCHOR_TYPE,
                       CASE
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ASSIGNMENT' THEN 'Assignment #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ROUND' THEN 'Round #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'MANUSCRIPT_VERSION' THEN 'Manuscript #' || I.BUSINESS_ANCHOR_ID || ' / Version #' || I.BUSINESS_ANCHOR_VERSION_ID
                         ELSE 'Anchor #' || I.BUSINESS_ANCHOR_ID
                       END AS ANCHOR_LABEL,
                       P.SUMMARY_TEXT,
                       P.UPDATED_AT AS PROJECTION_UPDATED_AT
                FROM ANALYSIS_INTENT I
                LEFT JOIN ANALYSIS_PROJECTION P ON P.INTENT_ID = I.INTENT_ID
                ORDER BY I.INTENT_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new AdminAnalysisMonitorItem(
                        rs.getLong("INTENT_ID"),
                        rs.getString("ANALYSIS_TYPE"),
                        rs.getString("BUSINESS_STATUS"),
                        rs.getString("EXECUTION_JOB_ID"),
                        rs.getString("BUSINESS_ANCHOR_TYPE"),
                        rs.getString("ANCHOR_LABEL"),
                        rs.getString("SUMMARY_TEXT"),
                        rs.getTimestamp("PROJECTION_UPDATED_AT")
                )
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
        List<DecisionAssignmentItem> assignments,
        AnalysisIntentResponse conflictIntent,
        List<AnalysisProjectionResponse> conflictProjections
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

record AdminAnalysisMonitorItem(
        long intentId,
        String analysisType,
        String businessStatus,
        String jobId,
        String anchorType,
        String anchorLabel,
        String summaryText,
        Timestamp projectionUpdatedAt
) {
}
