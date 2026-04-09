package com.example.review.review;

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
@RequestMapping("/api/review-rounds")
public class ReviewRoundController {
    private final ReviewWorkflowService reviewWorkflowService;

    public ReviewRoundController(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @PostMapping
    public ReviewRoundResponse createRound(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody CreateReviewRoundRequest request
    ) {
        return reviewWorkflowService.createRound(principal, request);
    }

    @PostMapping("/{roundId}/assignments")
    public AssignmentActionResponse assignReviewer(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId,
            @RequestBody CreateAssignmentRequest request
    ) {
        return reviewWorkflowService.assignReviewer(principal, roundId, request);
    }

    @GetMapping("/{roundId}/conflict-checks")
    public List<ConflictCheckResponse> listConflictChecks(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId
    ) {
        return reviewWorkflowService.listConflictChecks(principal, roundId);
    }
}
