package com.example.review.workflow;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkflowQueryController {
    private final WorkflowQueryService workflowQueryService;
    private final DecisionWorkbenchQueryService decisionWorkbenchQueryService;

    public WorkflowQueryController(
            WorkflowQueryService workflowQueryService,
            DecisionWorkbenchQueryService decisionWorkbenchQueryService
    ) {
        this.workflowQueryService = workflowQueryService;
        this.decisionWorkbenchQueryService = decisionWorkbenchQueryService;
    }

    @GetMapping("/review-assignments")
    public List<ReviewerAssignmentSummary> listReviewerAssignments(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        return workflowQueryService.listReviewerAssignments(principal);
    }

    @GetMapping("/review-assignments/{assignmentId}")
    public ReviewerAssignmentDetail getReviewerAssignment(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return workflowQueryService.getReviewerAssignment(principal, assignmentId);
    }

    @GetMapping("/chair/screening-queue")
    public List<ScreeningQueueItem> listScreeningQueue(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return workflowQueryService.listScreeningQueue(principal);
    }

    @GetMapping("/chair/decision-workbench")
    public List<DecisionWorkbenchItem> listDecisionWorkbench(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return decisionWorkbenchQueryService.listDecisionWorkbench(principal);
    }

    @GetMapping("/chair/conferences/{conferenceId}/papers")
    public List<ConferencePaperItem> listConferencePapers(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId
    ) {
        return workflowQueryService.listConferencePapers(principal, conferenceId);
    }

    @GetMapping("/admin/analysis-monitor")
    public AdminAnalysisMonitorPage listAdminAnalysisMonitor(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String analysisType,
            @RequestParam(required = false) String businessStatus
    ) {
        return workflowQueryService.listAdminAnalysisMonitor(
                principal,
                new AdminAnalysisMonitorRequest(page, size, analysisType, businessStatus)
        );
    }
}
