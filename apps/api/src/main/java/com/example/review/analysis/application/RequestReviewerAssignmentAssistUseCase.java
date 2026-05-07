package com.example.review.analysis.application;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.infrastructure.ReviewerAssignmentAssistContextRepository;
import com.example.review.analysis.infrastructure.ReviewerAssignmentAssistContextRepository.AssignmentAssistContext;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AssignmentAssistStateResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestReviewerAssignmentAssistUseCase {
    private static final int REQUEST_VERSION = 1;

    private final ReviewerAssignmentAssistContextRepository contextRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;
    private final AnalysisOutboxPublisher outboxPublisher;

    public RequestReviewerAssignmentAssistUseCase(
            ReviewerAssignmentAssistContextRepository contextRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisProjectionRepository projectionRepository,
            AnalysisOutboxPublisher outboxPublisher
    ) {
        this.contextRepository = contextRepository;
        this.intentRepository = intentRepository;
        this.projectionRepository = projectionRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public AnalysisIntentResponse request(CurrentUserPrincipal principal, long roundId, boolean force) {
        RoleGuard.requireChairOrAdmin(principal);
        AssignmentAssistContext context = loadContext(roundId);
        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.manuscript(context.manuscriptId());
        Map<String, Object> payload = buildPayload(context);
        int requestVersion = force
                ? Math.max(2, intentRepository.nextRequestVersion(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST, anchor))
                : REQUEST_VERSION;
        String idempotencyKey = AnalysisIdempotencyKeyFactory.build(
                AnalysisType.REVIEWER_ASSIGNMENT_ASSIST,
                anchor,
                payload,
                requestVersion
        );
        long intentId = intentRepository.createOrReuseIntent(
                AnalysisType.REVIEWER_ASSIGNMENT_ASSIST,
                anchor,
                principal.userId(),
                idempotencyKey
        );
        outboxPublisher.publishRequested(intentId, AnalysisType.REVIEWER_ASSIGNMENT_ASSIST, idempotencyKey, payload);
        return new AnalysisIntentResponse(intentId, AnalysisType.REVIEWER_ASSIGNMENT_ASSIST.name(), "REQUESTED");
    }

    @Transactional(readOnly = true)
    public AssignmentAssistStateResponse get(CurrentUserPrincipal principal, long roundId) {
        RoleGuard.requireChairOrAdmin(principal);
        AssignmentAssistContext context = loadContext(roundId);
        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.manuscript(context.manuscriptId());
        AnalysisIntentResponse intent = intentRepository.findLatestIntent(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST, anchor)
                .map(summary -> new AnalysisIntentResponse(summary.intentId(), summary.analysisType(), summary.businessStatus()))
                .orElse(null);
        return new AssignmentAssistStateResponse(
                intent,
                projectionRepository.listForAnchor(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST, anchor)
        );
    }

    private AssignmentAssistContext loadContext(long roundId) {
        return contextRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
    }

    private Map<String, Object> buildPayload(AssignmentAssistContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", context.title());
        payload.put("abstract", context.abstractText());
        payload.put("keywords", context.keywordList());
        payload.put("candidateDrafts", context.candidateDrafts());
        payload.put("assignmentAssist", Map.of(
                "roundId", context.roundId(),
                "manuscriptId", context.manuscriptId(),
                "versionId", context.versionId(),
                "candidateCount", context.candidateDrafts().size()
        ));
        return payload;
    }
}
