package com.example.review.analysis.interfaces;

import com.example.review.analysis.application.RequestReviewerAssistUseCase;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.ReviewerAssistRequest;
import com.example.review.analysis.interfaces.AnalysisDtos.ReviewerAssistStateResponse;
import com.example.review.auth.CurrentUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final RequestReviewerAssistUseCase requestReviewerAssistUseCase;

    public AnalysisController(RequestReviewerAssistUseCase requestReviewerAssistUseCase) {
        this.requestReviewerAssistUseCase = requestReviewerAssistUseCase;
    }

    @PostMapping("/review-assignments/{assignmentId}/agent-assist")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisIntentResponse requestReviewerAssist(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody(required = false) ReviewerAssistRequest request
    ) {
        return requestReviewerAssistUseCase.request(
                principal,
                assignmentId,
                request != null && request.forceRequested()
        );
    }

    @GetMapping("/review-assignments/{assignmentId}/agent-assist")
    public ReviewerAssistStateResponse getReviewerAssist(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return requestReviewerAssistUseCase.get(principal, assignmentId);
    }
}
