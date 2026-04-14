package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-assignments")
public class ReviewerPaperController {
    private final ReviewerPaperService reviewerPaperService;

    public ReviewerPaperController(ReviewerPaperService reviewerPaperService) {
        this.reviewerPaperService = reviewerPaperService;
    }

    @GetMapping("/{assignmentId}/paper")
    public ReviewerPaperResponse getPaper(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return reviewerPaperService.getPaper(principal, assignmentId);
    }

    @GetMapping("/{assignmentId}/paper/pages/{pageNo}")
    public ResponseEntity<byte[]> getPage(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @PathVariable int pageNo
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(reviewerPaperService.renderPage(principal, assignmentId, pageNo));
    }
}
