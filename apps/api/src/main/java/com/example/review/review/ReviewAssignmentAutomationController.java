package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewAssignmentAutomationController {
    private final ReviewWorkflowService reviewWorkflowService;

    public ReviewAssignmentAutomationController(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @PostMapping("/conferences/{conferenceId}/auto-assignments")
    public AutoAssignResponse autoAssignConferenceReviewers(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody AutoAssignRequest request
    ) {
        return reviewWorkflowService.autoAssignConferenceReviewers(principal, conferenceId, request);
    }
}
