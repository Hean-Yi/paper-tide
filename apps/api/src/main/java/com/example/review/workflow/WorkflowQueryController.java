package com.example.review.workflow;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkflowQueryController {
    private final WorkflowQueryService workflowQueryService;

    public WorkflowQueryController(WorkflowQueryService workflowQueryService) {
        this.workflowQueryService = workflowQueryService;
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
        return workflowQueryService.listDecisionWorkbench(principal);
    }
}
