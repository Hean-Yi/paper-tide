package com.example.review.workflow;

import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowQueryService {
    private final ReviewerAssignmentReadRepository reviewerAssignmentReadRepository;
    private final ScreeningQueueReadRepository screeningQueueReadRepository;
    private final AdminAnalysisMonitorReadRepository adminAnalysisMonitorReadRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;

    public WorkflowQueryService(
            ReviewerAssignmentReadRepository reviewerAssignmentReadRepository,
            ScreeningQueueReadRepository screeningQueueReadRepository,
            AdminAnalysisMonitorReadRepository adminAnalysisMonitorReadRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisProjectionRepository projectionRepository
    ) {
        this.reviewerAssignmentReadRepository = reviewerAssignmentReadRepository;
        this.screeningQueueReadRepository = screeningQueueReadRepository;
        this.adminAnalysisMonitorReadRepository = adminAnalysisMonitorReadRepository;
        this.intentRepository = intentRepository;
        this.projectionRepository = projectionRepository;
    }

    public List<ReviewerAssignmentSummary> listReviewerAssignments(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "REVIEWER");
        return reviewerAssignmentReadRepository.findSummariesByReviewerId(principal.userId());
    }

    public ReviewerAssignmentDetail getReviewerAssignment(CurrentUserPrincipal principal, long assignmentId) {
        List<ReviewerAssignmentDetail> rows = reviewerAssignmentReadRepository.findDetailByAssignmentId(assignmentId);
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
        return screeningQueueReadRepository.findOpenScreeningItems();
    }

    public AdminAnalysisMonitorPage listAdminAnalysisMonitor(
            CurrentUserPrincipal principal,
            AdminAnalysisMonitorRequest request
    ) {
        RoleGuard.requireRole(principal, "ADMIN");
        return adminAnalysisMonitorReadRepository.findPage(request.normalized());
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

record AdminAnalysisMonitorRequest(
        int page,
        int size,
        String analysisType,
        String businessStatus
) {
    AdminAnalysisMonitorRequest normalized() {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return new AdminAnalysisMonitorRequest(
                normalizedPage,
                normalizedSize,
                blankToNull(analysisType),
                blankToNull(businessStatus)
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

record AdminAnalysisMonitorPage(
        List<AdminAnalysisMonitorItem> items,
        int page,
        int size,
        long total
) {
}
