package com.example.review.analysis.application;

import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.infrastructure.ReviewerAssistContextRepository;
import com.example.review.analysis.infrastructure.ReviewerAssistContextRepository.ReviewerAssistContext;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.ReviewerAssistStateResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestReviewerAssistUseCase {
    private static final List<String> ALLOWED_CREATE_STATUSES = List.of("ACCEPTED", "IN_REVIEW", "OVERDUE");
    private static final List<String> DENIED_QUERY_STATUSES = List.of("DECLINED", "REASSIGNED", "CANCELLED");
    private static final int REQUEST_VERSION = 1;
    private static final int MAX_PDF_TEXT_CHARS = 120_000;

    private final ReviewerAssistContextRepository contextRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;
    private final AnalysisOutboxPublisher outboxPublisher;

    public RequestReviewerAssistUseCase(
            ReviewerAssistContextRepository contextRepository,
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
    public AnalysisIntentResponse request(CurrentUserPrincipal principal, long assignmentId, boolean force) {
        ReviewerAssistContext context = loadAllowedContext(principal, assignmentId);
        if (!ALLOWED_CREATE_STATUSES.contains(context.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment state does not allow reviewer assist creation");
        }
        if (context.pdfFileSize() == null || context.pdfFileSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before agent analysis");
        }

        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.assignment(assignmentId);
        Map<String, Object> payload = buildPayload(context);
        int requestVersion = force
                ? Math.max(2, intentRepository.nextRequestVersion(AnalysisType.REVIEWER_ASSIST, anchor))
                : REQUEST_VERSION;
        String idempotencyKey = AnalysisIdempotencyKeyFactory.build(
                AnalysisType.REVIEWER_ASSIST,
                anchor,
                payload,
                requestVersion
        );
        long intentId = intentRepository.createOrReuseIntent(
                AnalysisType.REVIEWER_ASSIST,
                anchor,
                principal.userId(),
                idempotencyKey
        );
        outboxPublisher.publishRequested(intentId, AnalysisType.REVIEWER_ASSIST, idempotencyKey, payload);
        return new AnalysisIntentResponse(intentId, AnalysisType.REVIEWER_ASSIST.name(), "REQUESTED");
    }

    @Transactional(readOnly = true)
    public ReviewerAssistStateResponse get(CurrentUserPrincipal principal, long assignmentId) {
        ReviewerAssistContext context = loadAllowedContext(principal, assignmentId);
        if (DENIED_QUERY_STATUSES.contains(context.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment state does not allow reviewer assist access");
        }

        AnalysisBusinessAnchor anchor = AnalysisBusinessAnchor.assignment(assignmentId);
        AnalysisIntentResponse intent = intentRepository.findLatestIntent(AnalysisType.REVIEWER_ASSIST, anchor)
                .map(summary -> new AnalysisIntentResponse(summary.intentId(), summary.analysisType(), summary.businessStatus()))
                .orElse(null);
        return new ReviewerAssistStateResponse(
                intent,
                projectionRepository.listForAnchor(AnalysisType.REVIEWER_ASSIST, anchor)
        );
    }

    private ReviewerAssistContext loadAllowedContext(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        ReviewerAssistContext context = contextRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review assignment not found"));
        if (context.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access reviewer assist");
        }
        return context;
    }

    private Map<String, Object> buildPayload(ReviewerAssistContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", context.title());
        payload.put("abstract", context.abstractText());
        payload.put("keywords", context.keywordList());
        String pdfText = extractPaperText(context.pdfFile());
        if (!pdfText.isBlank()) {
            payload.put("pdfText", pdfText);
            payload.put("sections", Map.of("fullText", pdfText));
        }
        payload.put("reviewerAssist", Map.of(
                "assignmentId", context.assignmentId(),
                "roundId", context.roundId(),
                "manuscriptId", context.manuscriptId(),
                "versionId", context.versionId(),
                "allowedOutput", "checklist_only",
                "forbiddenOutput", List.of("recommendation", "score", "fullReviewText")
        ));
        return payload;
    }

    private String extractPaperText(byte[] pdfFile) {
        if (pdfFile == null || pdfFile.length == 0) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return normalizeAndLimit(new PDFTextStripper().getText(document));
        } catch (IOException | RuntimeException ex) {
            return normalizeAndLimit(new String(pdfFile, StandardCharsets.UTF_8));
        }
    }

    private String normalizeAndLimit(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_PDF_TEXT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_PDF_TEXT_CHARS) + "... [truncated "
                + (normalized.length() - MAX_PDF_TEXT_CHARS) + " chars]";
    }
}
