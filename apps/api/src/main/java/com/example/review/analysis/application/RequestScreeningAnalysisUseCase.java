package com.example.review.analysis.application;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.ScreeningAnalysisContextRepository;
import com.example.review.analysis.infrastructure.ScreeningAnalysisContextRepository.ScreeningAnalysisContext;
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
public class RequestScreeningAnalysisUseCase {
    private static final int REQUEST_VERSION = 1;

    private final ScreeningAnalysisContextRepository contextRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisOutboxPublisher outboxPublisher;

    public RequestScreeningAnalysisUseCase(
            ScreeningAnalysisContextRepository contextRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisOutboxPublisher outboxPublisher
    ) {
        this.contextRepository = contextRepository;
        this.intentRepository = intentRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public AnalysisIntentResponse request(
            CurrentUserPrincipal principal,
            long manuscriptId,
            long versionId,
            boolean force
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        ScreeningAnalysisContext context = contextRepository.findByManuscriptVersion(manuscriptId, versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript version not found"));
        if (context.pdfFileSize() == null || context.pdfFileSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before agent analysis");
        }

        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.screening(manuscriptId, versionId);
        Map<String, Object> payload = buildPayload(context);
        int requestVersion = force
                ? Math.max(2, intentRepository.nextRequestVersion(AnalysisType.SCREENING, anchor))
                : REQUEST_VERSION;
        String idempotencyKey = AnalysisIdempotencyKeyFactory.build(
                AnalysisType.SCREENING,
                anchor,
                payload,
                requestVersion
        );
        long intentId = intentRepository.createOrReuseIntent(
                AnalysisType.SCREENING,
                anchor,
                principal.userId(),
                idempotencyKey
        );
        outboxPublisher.publishRequested(intentId, AnalysisType.SCREENING, idempotencyKey, payload);
        return new AnalysisIntentResponse(intentId, AnalysisType.SCREENING.name(), "REQUESTED");
    }

    private Map<String, Object> buildPayload(ScreeningAnalysisContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", context.title());
        payload.put("abstract", context.abstractText());
        payload.put("keywords", context.keywordList());
        payload.put("screening", Map.of(
                "manuscriptId", context.manuscriptId(),
                "versionId", context.versionId(),
                "pdfFileSize", context.pdfFileSize()
        ));
        return payload;
    }
}
