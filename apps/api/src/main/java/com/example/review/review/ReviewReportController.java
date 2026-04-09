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
public class ReviewReportController {
    private final ReviewReportService reviewReportService;

    public ReviewReportController(ReviewReportService reviewReportService) {
        this.reviewReportService = reviewReportService;
    }

    @PostMapping("/{assignmentId}/review-report")
    public ReviewReportResponse submit(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody SubmitReviewReportRequest request
    ) {
        return reviewReportService.submit(principal, assignmentId, request);
    }
}
