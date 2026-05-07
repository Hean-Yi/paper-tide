package com.example.review.operations;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BusinessOperationsController {
    private final BusinessOperationsService service;

    public BusinessOperationsController(BusinessOperationsService service) {
        this.service = service;
    }

    @GetMapping("/review-rounds/{roundId}/discussion")
    public List<DiscussionMessageResponse> listRoundDiscussion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId
    ) {
        return service.listRoundDiscussion(principal, roundId);
    }

    @PostMapping("/review-rounds/{roundId}/discussion")
    public DiscussionMessageResponse postRoundDiscussion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId,
            @RequestBody DiscussionMessageRequest request
    ) {
        return service.postRoundDiscussion(principal, roundId, request);
    }

    @GetMapping("/review-assignments/{assignmentId}/discussion")
    public List<DiscussionMessageResponse> listAssignmentDiscussion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return service.listAssignmentDiscussion(principal, assignmentId);
    }

    @PostMapping("/review-assignments/{assignmentId}/discussion")
    public DiscussionMessageResponse postAssignmentDiscussion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody DiscussionMessageRequest request
    ) {
        return service.postAssignmentDiscussion(principal, assignmentId, request);
    }

    @GetMapping("/manuscripts/{manuscriptId}/camera-ready")
    public CameraReadyResponse getCameraReady(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId
    ) {
        return service.getCameraReady(principal, manuscriptId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/camera-ready")
    public CameraReadyResponse submitCameraReady(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody CameraReadyRequest request
    ) {
        return service.submitCameraReady(principal, manuscriptId, request);
    }

    @GetMapping("/conferences/{conferenceId}/communication-log")
    public List<CommunicationLogResponse> listCommunicationLogs(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId
    ) {
        return service.listCommunicationLogs(principal, conferenceId);
    }

    @GetMapping("/conferences/{conferenceId}/governance-report")
    public GovernanceReportResponse governanceReport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId
    ) {
        return service.governanceReport(principal, conferenceId);
    }
}
