package com.example.review.workflow;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/reviewer/interface-choice")
    public ReviewerInterfaceChoiceResponse getReviewerInterfaceChoice(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        return workflowQueryService.getReviewerInterfaceChoice(principal);
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

    @GetMapping("/chair/conferences/{conferenceId}/papers/{manuscriptId}/review-detail")
    public ChairConferencePaperReviewDetail getChairConferencePaperReviewDetail(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @PathVariable long manuscriptId
    ) {
        return workflowQueryService.getChairConferencePaperReviewDetail(principal, conferenceId, manuscriptId);
    }

    @GetMapping("/chair/conferences/{conferenceId}/papers/{manuscriptId}/paper/pages/{pageNo}")
    public ResponseEntity<byte[]> getChairConferencePaperPage(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @PathVariable long manuscriptId,
            @PathVariable int pageNo
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(workflowQueryService.renderChairConferencePaperPage(principal, conferenceId, manuscriptId, pageNo));
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
