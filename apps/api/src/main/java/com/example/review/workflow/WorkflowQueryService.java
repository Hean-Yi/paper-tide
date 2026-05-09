package com.example.review.workflow;

import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.example.review.conference.ConferenceService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowQueryService {
    private static final int RENDER_DPI = 144;

    private final ReviewerAssignmentReadRepository reviewerAssignmentReadRepository;
    private final ScreeningQueueReadRepository screeningQueueReadRepository;
    private final ConferencePaperReadRepository conferencePaperReadRepository;
    private final ConferenceService conferenceService;
    private final AdminAnalysisMonitorReadRepository adminAnalysisMonitorReadRepository;
    private final AnalysisIntentRepository intentRepository;
    private final AnalysisProjectionRepository projectionRepository;

    public WorkflowQueryService(
            ReviewerAssignmentReadRepository reviewerAssignmentReadRepository,
            ScreeningQueueReadRepository screeningQueueReadRepository,
            ConferencePaperReadRepository conferencePaperReadRepository,
            ConferenceService conferenceService,
            AdminAnalysisMonitorReadRepository adminAnalysisMonitorReadRepository,
            AnalysisIntentRepository intentRepository,
            AnalysisProjectionRepository projectionRepository
    ) {
        this.reviewerAssignmentReadRepository = reviewerAssignmentReadRepository;
        this.screeningQueueReadRepository = screeningQueueReadRepository;
        this.conferencePaperReadRepository = conferencePaperReadRepository;
        this.conferenceService = conferenceService;
        this.adminAnalysisMonitorReadRepository = adminAnalysisMonitorReadRepository;
        this.intentRepository = intentRepository;
        this.projectionRepository = projectionRepository;
    }

    public List<ReviewerAssignmentSummary> listReviewerAssignments(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "REVIEWER");
        return reviewerAssignmentReadRepository.findSummariesByReviewerId(principal.userId());
    }

    public ReviewerInterfaceChoiceResponse getReviewerInterfaceChoice(CurrentUserPrincipal principal) {
        if (!RoleGuard.hasRole(principal, "AUTHOR") || !RoleGuard.hasRole(principal, "REVIEWER")) {
            return new ReviewerInterfaceChoiceResponse(false, 0);
        }
        ReviewerInterfaceChoiceState state = reviewerAssignmentReadRepository.findInterfaceChoiceState(principal.userId());
        return new ReviewerInterfaceChoiceResponse(state.shouldPrompt(), state.activeAssignmentCount());
    }

    public ReviewerAssignmentDetail getReviewerAssignment(CurrentUserPrincipal principal, long assignmentId) {
        List<ReviewerAssignmentDetail> rows = reviewerAssignmentReadRepository.findDetailByAssignmentId(assignmentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review assignment not found");
        }
        ReviewerAssignmentDetail detail = rows.getFirst();
        if (!RoleGuard.hasRole(principal, "CHAIR") && !RoleGuard.hasRole(principal, "ADMIN")
                && (!RoleGuard.hasRole(principal, "REVIEWER") || detail.reviewerId() != principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view review assignment");
        }
        return detail;
    }

    public List<ScreeningQueueItem> listScreeningQueue(CurrentUserPrincipal principal) {
        RoleGuard.requireChairOrAdmin(principal);
        if (RoleGuard.hasRole(principal, "ADMIN")) {
            return screeningQueueReadRepository.findOpenScreeningItems();
        }
        return screeningQueueReadRepository.findOpenScreeningItemsByOrganizer(principal.userId());
    }

    public List<ConferencePaperItem> listConferencePapers(CurrentUserPrincipal principal, long conferenceId) {
        RoleGuard.requireChairOrAdmin(principal);
        conferenceService.getManageableConference(conferenceId, principal);
        return conferencePaperReadRepository.findByConferenceId(conferenceId);
    }

    public ChairConferencePaperReviewDetail getChairConferencePaperReviewDetail(
            CurrentUserPrincipal principal,
            long conferenceId,
            long manuscriptId
    ) {
        RoleGuard.requireRole(principal, "CHAIR");
        conferenceService.getManageableConference(conferenceId, principal);
        ChairConferencePaperReviewDetail detail = conferencePaperReadRepository.findReviewDetail(conferenceId, manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference paper was not found"));
        return new ChairConferencePaperReviewDetail(
                detail.manuscriptId(),
                detail.versionId(),
                detail.versionNo(),
                detail.roundId(),
                detail.roundNo(),
                detail.title(),
                detail.abstractText(),
                detail.keywords(),
                detail.pdfFileName(),
                detail.currentStatus(),
                detail.roundStatus(),
                detail.submittedAt(),
                pageCount(detail.pdfFile()),
                detail.averageOverallScore(),
                detail.reviews(),
                null
        );
    }

    public byte[] renderChairConferencePaperPage(
            CurrentUserPrincipal principal,
            long conferenceId,
            long manuscriptId,
            int pageNo
    ) {
        RoleGuard.requireRole(principal, "CHAIR");
        conferenceService.getManageableConference(conferenceId, principal);
        ChairConferencePaperFile paper = conferencePaperReadRepository.findPaperFile(conferenceId, manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference paper was not found"));
        byte[] pdfFile = paper.pdfFile();
        if (pdfFile == null || pdfFile.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found");
        }
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            if (pageNo < 1 || pageNo > pageCount) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper page not found");
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageNo - 1, RENDER_DPI);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to render paper page", ex);
        }
    }

    public AdminAnalysisMonitorPage listAdminAnalysisMonitor(
            CurrentUserPrincipal principal,
            AdminAnalysisMonitorRequest request
    ) {
        RoleGuard.requireRole(principal, "ADMIN");
        return adminAnalysisMonitorReadRepository.findPage(request.normalized());
    }

    private int pageCount(byte[] pdfFile) {
        if (pdfFile == null || pdfFile.length == 0) {
            return 0;
        }
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to inspect paper PDF", ex);
        }
    }

}

record ReviewerAssignmentSummary(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        int versionNo,
        String title,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp declinedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        String recommendation
) {
}

record ReviewerInterfaceChoiceResponse(
        boolean shouldPrompt,
        int activeAssignmentCount
) {
}

record ReviewerAssignmentDetail(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int versionNo,
        String title,
        String abstractText,
        String keywords,
        String pdfFileName,
        Long pdfFileSize,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp declinedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        String recommendation
) {
}

record ScreeningQueueItem(
        long manuscriptId,
        long versionId,
        int versionNo,
        String title,
        String abstractText,
        String keywords,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String pdfFileName,
        Long pdfFileSize,
        AnalysisIntentResponse screeningIntent
) {
}

record ConferencePaperItem(
        long manuscriptId,
        long versionId,
        int versionNo,
        Long roundId,
        Integer roundNo,
        String title,
        String currentStatus,
        String roundStatus,
        int assignmentCount,
        int submittedReviewCount,
        String lastDecisionCode,
        Timestamp submittedAt,
        Double averageOverallScore,
        List<ConferencePaperReviewerScore> reviewerScores
) {
}

record ConferencePaperReviewerScore(
        long assignmentId,
        long reviewerId,
        String reviewerName,
        String taskStatus,
        Integer overallScore,
        String recommendation,
        Timestamp submittedAt
) {
}

record ChairConferencePaperReviewDetail(
        long manuscriptId,
        long versionId,
        int versionNo,
        Long roundId,
        Integer roundNo,
        String title,
        String abstractText,
        String keywords,
        String pdfFileName,
        String currentStatus,
        String roundStatus,
        Timestamp submittedAt,
        int pageCount,
        Double averageOverallScore,
        List<ChairConferencePaperReview> reviews,
        @JsonIgnore
        byte[] pdfFile
) {
}

record ChairConferencePaperReview(
        long assignmentId,
        long reviewerId,
        String reviewerName,
        String institution,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp deadlineAt,
        Timestamp assignmentSubmittedAt,
        Long reviewId,
        Integer noveltyScore,
        Integer methodScore,
        Integer experimentScore,
        Integer writingScore,
        Integer overallScore,
        String confidenceLevel,
        String strengths,
        String weaknesses,
        String commentsToAuthor,
        String commentsToChair,
        String recommendation,
        Timestamp reviewSubmittedAt
) {
}

record ChairConferencePaperFile(
        long manuscriptId,
        long versionId,
        String title,
        byte[] pdfFile,
        String pdfFileName
) {
}

record DecisionWorkbenchItem(
        long roundId,
        long manuscriptId,
        long versionId,
        int versionNo,
        int roundNo,
        String title,
        String currentStatus,
        String roundStatus,
        Timestamp deadlineAt,
        int assignmentCount,
        int submittedReviewCount,
        int conflictCount,
        String lastDecisionCode,
        List<DecisionAssignmentItem> assignments,
        AnalysisIntentResponse conflictIntent,
        List<AnalysisProjectionResponse> conflictProjections
) {
}

record DecisionAssignmentItem(
        long assignmentId,
        long reviewerId,
        String taskStatus,
        Timestamp assignedAt,
        Timestamp acceptedAt,
        Timestamp deadlineAt,
        Timestamp submittedAt,
        Long reassignedFromId
) {
}

record DecisionWorkbenchBase(
        long roundId,
        long manuscriptId,
        long versionId,
        int roundNo,
        String roundStatus,
        Timestamp deadlineAt,
        String currentStatus,
        String lastDecisionCode,
        String title,
        int versionNo,
        int assignmentCount,
        int submittedReviewCount,
        int conflictCount
) {
}

record AdminAnalysisMonitorItem(
        long intentId,
        String analysisType,
        String businessStatus,
        String jobId,
        String anchorType,
        String anchorLabel,
        String summaryText,
        Timestamp projectionUpdatedAt
) {
}

record AdminAnalysisMonitorRequest(
        int page,
        int size,
        String analysisType,
        String businessStatus
) {
    AdminAnalysisMonitorRequest normalized() {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return new AdminAnalysisMonitorRequest(
                normalizedPage,
                normalizedSize,
                blankToNull(analysisType),
                blankToNull(businessStatus)
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

record AdminAnalysisMonitorPage(
        List<AdminAnalysisMonitorItem> items,
        int page,
        int size,
        long total
) {
}
