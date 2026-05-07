package com.example.review.decision;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DecisionPackageService {
    private final DecisionRepository decisionRepository;

    public DecisionPackageService(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    public DecisionPackageResponse getAuthorDecisionPackage(CurrentUserPrincipal principal, long manuscriptId) {
        RoleGuard.requireRole(principal, "AUTHOR");
        DecisionPackageRow decision = decisionRepository.findAuthorDecisionPackage(principal.userId(), manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision package not found"));
        AtomicInteger reviewerNo = new AtomicInteger(1);
        List<DecisionPackageReviewResponse> reviews = decisionRepository.findAuthorVisibleReviews(decision.roundId()).stream()
                .map(row -> new DecisionPackageReviewResponse(
                        row.reviewId(),
                        "Reviewer " + reviewerNo.getAndIncrement(),
                        row.overallScore(),
                        row.confidenceLevel(),
                        row.strengths(),
                        row.weaknesses(),
                        row.commentsToAuthor(),
                        row.recommendation()
                ))
                .toList();
        return new DecisionPackageResponse(
                decision.manuscriptId(),
                decision.roundId(),
                decision.versionId(),
                decision.versionNo(),
                decision.title(),
                decision.decisionCode(),
                decision.decisionReason(),
                decision.decidedAt(),
                reviews
        );
    }
}

record DecisionPackageResponse(
        long manuscriptId,
        long roundId,
        long versionId,
        int versionNo,
        String title,
        String decisionCode,
        String decisionReason,
        Timestamp decidedAt,
        List<DecisionPackageReviewResponse> reviews
) {
}

record DecisionPackageReviewResponse(
        long reviewId,
        String reviewerLabel,
        int overallScore,
        String confidenceLevel,
        String strengths,
        String weaknesses,
        String commentsToAuthor,
        String recommendation
) {
}
