package com.example.review.analysis.interfaces;

import com.example.review.analysis.application.RequestConflictAnalysisUseCase;
import com.example.review.analysis.application.RequestReviewerAssistUseCase;
import com.example.review.analysis.application.RequestScreeningAnalysisUseCase;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.ConflictAnalysisRequest;
import com.example.review.analysis.interfaces.AnalysisDtos.ReviewerAssistRequest;
import com.example.review.analysis.interfaces.AnalysisDtos.ReviewerAssistStateResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.ScreeningAnalysisRequest;
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
    private final RequestConflictAnalysisUseCase requestConflictAnalysisUseCase;
    private final RequestScreeningAnalysisUseCase requestScreeningAnalysisUseCase;

    public AnalysisController(
            RequestReviewerAssistUseCase requestReviewerAssistUseCase,
            RequestConflictAnalysisUseCase requestConflictAnalysisUseCase,
            RequestScreeningAnalysisUseCase requestScreeningAnalysisUseCase
    ) {
        this.requestReviewerAssistUseCase = requestReviewerAssistUseCase;
        this.requestConflictAnalysisUseCase = requestConflictAnalysisUseCase;
        this.requestScreeningAnalysisUseCase = requestScreeningAnalysisUseCase;
    }

    @PostMapping("/manuscripts/{manuscriptId}/versions/{versionId}/screening-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisIntentResponse requestScreeningAnalysis(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @PathVariable long versionId,
            @RequestBody(required = false) ScreeningAnalysisRequest request
    ) {
        return requestScreeningAnalysisUseCase.request(
                principal,
                manuscriptId,
                versionId,
                request != null && request.forceRequested()
        );
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

    @PostMapping("/review-rounds/{roundId}/conflict-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisIntentResponse requestConflictAnalysis(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId,
            @RequestBody(required = false) ConflictAnalysisRequest request
    ) {
        return requestConflictAnalysisUseCase.request(
                principal,
                roundId,
                request != null && request.forceRequested()
        );
    }
}
