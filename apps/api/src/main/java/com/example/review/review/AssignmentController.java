package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-assignments")
public class AssignmentController {
    private final ReviewWorkflowService reviewWorkflowService;

    public AssignmentController(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @PostMapping("/{assignmentId}/accept")
    public AssignmentActionResponse accept(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return reviewWorkflowService.acceptAssignment(principal, assignmentId);
    }

    @PostMapping("/{assignmentId}/decline")
    public AssignmentActionResponse decline(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody DeclineAssignmentRequest request
    ) {
        return reviewWorkflowService.declineAssignment(principal, assignmentId, request);
    }

    @PostMapping("/{assignmentId}/mark-overdue")
    public AssignmentActionResponse markOverdue(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return reviewWorkflowService.markOverdue(principal, assignmentId);
    }

    @PostMapping("/{assignmentId}/reassign")
    public AssignmentActionResponse reassign(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody CreateAssignmentRequest request
    ) {
        return reviewWorkflowService.reassign(principal, assignmentId, request);
    }
}
