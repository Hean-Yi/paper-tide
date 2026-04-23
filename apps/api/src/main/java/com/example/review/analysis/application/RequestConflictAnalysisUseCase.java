package com.example.review.analysis.application;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.ConflictAnalysisContextRepository;
import com.example.review.analysis.infrastructure.ConflictAnalysisContextRepository.ConflictAnalysisContext;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestConflictAnalysisUseCase {
    private static final int REQUEST_VERSION = 1;

    private final ConflictAnalysisContextRepository contextRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisOutboxPublisher outboxPublisher;

    public RequestConflictAnalysisUseCase(
            ConflictAnalysisContextRepository contextRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisOutboxPublisher outboxPublisher
    ) {
        this.contextRepository = contextRepository;
        this.intentRepository = intentRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public AnalysisIntentResponse request(CurrentUserPrincipal principal, long roundId, boolean force) {
        RoleGuard.requireChairOrAdmin(principal);
        ConflictAnalysisContext context = contextRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        if (context.pdfFileSize() == null || context.pdfFileSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before agent analysis");
        }

        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.round(roundId);
        Map<String, Object> payload = buildPayload(context);
        int requestVersion = force
                ? Math.max(2, intentRepository.nextRequestVersion(AnalysisType.CONFLICT_ANALYSIS, anchor))
                : REQUEST_VERSION;
        String idempotencyKey = AnalysisIdempotencyKeyFactory.build(
                AnalysisType.CONFLICT_ANALYSIS,
                anchor,
                payload,
                requestVersion
        );
        long intentId = intentRepository.createOrReuseIntent(
                AnalysisType.CONFLICT_ANALYSIS,
                anchor,
                principal.userId(),
                idempotencyKey
        );
        outboxPublisher.publishRequested(intentId, AnalysisType.CONFLICT_ANALYSIS, idempotencyKey, payload);
        return new AnalysisIntentResponse(intentId, AnalysisType.CONFLICT_ANALYSIS.name(), "REQUESTED");
    }

    private Map<String, Object> buildPayload(ConflictAnalysisContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", context.title());
        payload.put("abstract", context.abstractText());
        payload.put("keywords", context.keywordList());
        payload.put("reviewReports", context.reviewReports());
        payload.put("conflictAnalysis", Map.of(
                "roundId", context.roundId(),
                "manuscriptId", context.manuscriptId(),
                "versionId", context.versionId(),
                "reviewReportCount", context.reviewReports().size()
        ));
        return payload;
    }
}
