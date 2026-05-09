package com.example.review.decision;

import com.example.review.audit.AuditLogService;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.example.review.manuscript.ManuscriptRepository;
import com.example.review.manuscript.ManuscriptRepository.LockedManuscriptRow;
import com.example.review.notification.NotificationService;
import com.example.review.operations.BusinessOperationsService;
import com.example.review.review.ReviewAssignmentRepository;
import com.example.review.review.ReviewRoundRepository;
import com.example.review.review.ReviewRoundRepository.LockedReviewRoundRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionService.class);
    private static final Set<String> DECISION_CODES = Set.of("ACCEPT", "REJECT", "MINOR_REVISION", "MAJOR_REVISION", "DESK_REJECT");
    private static final Set<String> REVIEW_DECISIONS = Set.of("ACCEPT", "REJECT", "MINOR_REVISION", "MAJOR_REVISION");
    private static final Set<String> SCREENING_STATUSES = Set.of("UNDER_SCREENING");

    private final ManuscriptRepository manuscriptRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final DecisionRepository decisionRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final BusinessOperationsService businessOperationsService;

    public DecisionService(
            ManuscriptRepository manuscriptRepository,
            ReviewRoundRepository reviewRoundRepository,
            ReviewAssignmentRepository reviewAssignmentRepository,
            DecisionRepository decisionRepository,
            NotificationService notificationService,
            AuditLogService auditLogService,
            BusinessOperationsService businessOperationsService
    ) {
        this.manuscriptRepository = manuscriptRepository;
        this.reviewRoundRepository = reviewRoundRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.decisionRepository = decisionRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.businessOperationsService = businessOperationsService;
    }

    @Transactional
    public DecisionResponse decide(CurrentUserPrincipal principal, DecisionRequest request) {
        RoleGuard.requireChairOrAdmin(principal);
        validateRequest(request);

        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(request.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        LockedReviewRoundRow round = reviewRoundRepository.findLockedDecisionTarget(request.roundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        if (round.manuscriptId() != manuscript.manuscriptId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round does not belong to manuscript");
        }
        if (round.versionId() != request.versionId() || manuscript.currentVersionId() == null || manuscript.currentVersionId() != request.versionId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Decision must target the manuscript current version");
        }
        if (decisionRepository.findByRoundId(round.roundId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Decision already exists for this round");
        }

        String nextStatus = resolveNextStatus(manuscript.currentStatus(), request.decisionCode());
        Timestamp decidedAt = Timestamp.from(Instant.now());
        reviewRoundRepository.updateStatus(round.roundId(), "COMPLETED");
        reviewAssignmentRepository.cancelOpenAssignmentsForRound(round.roundId());
        manuscriptRepository.updateStatusAndDecision(manuscript.manuscriptId(), nextStatus, request.decisionCode());

        long decisionId = decisionRepository.nextDecisionId();
        decisionRepository.insert(
                decisionId,
                manuscript.manuscriptId(),
                round.roundId(),
                request.versionId(),
                request.decisionCode(),
                request.decisionReason(),
                principal.userId(),
                decidedAt
        );
        try {
            notificationService.notifyDecision(manuscript.submitterId(), manuscript.manuscriptId(), request.decisionCode());
        } catch (Exception ex) {
            LOGGER.warn("Decision notification failed for manuscript {}", manuscript.manuscriptId(), ex);
        }
        try {
            businessOperationsService.recordDecisionCommunication(
                    manuscript.conferenceId(),
                    manuscript.manuscriptId(),
                    manuscript.submitterId(),
                    decisionId,
                    request.decisionCode()
            );
        } catch (Exception ex) {
            LOGGER.warn("Decision communication log failed for manuscript {}", manuscript.manuscriptId(), ex);
        }
        auditLogService.recordDecision(principal.userId(), round.roundId(), manuscript.manuscriptId(), request.decisionCode());

        return new DecisionResponse(decisionId, request.decisionCode(), nextStatus, "COMPLETED");
    }

    @Transactional
    public DecisionResponse screeningDeskReject(CurrentUserPrincipal principal, ScreeningDeskRejectRequest request) {
        RoleGuard.requireChairOrAdmin(principal);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision request is required");
        }
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(request.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (manuscript.currentVersionId() == null || manuscript.currentVersionId() != request.versionId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Decision must target the manuscript current version");
        }
        String nextStatus = resolveNextStatus(manuscript.currentStatus(), "DESK_REJECT");
        long roundId = reviewRoundRepository.nextRoundId();
        int roundNo = reviewRoundRepository.nextRoundNo(request.manuscriptId());
        Timestamp decidedAt = Timestamp.from(Instant.now());
        reviewRoundRepository.insert(
                roundId,
                request.manuscriptId(),
                roundNo,
                request.versionId(),
                "COMPLETED",
                "REUSE_REVIEWERS",
                true,
                decidedAt,
                principal.userId()
        );
        manuscriptRepository.updateStatusAndRoundNo(request.manuscriptId(), nextStatus, roundNo);
        manuscriptRepository.updateLastDecision(request.manuscriptId(), "DESK_REJECT");

        long decisionId = decisionRepository.nextDecisionId();
        decisionRepository.insert(
                decisionId,
                manuscript.manuscriptId(),
                roundId,
                request.versionId(),
                "DESK_REJECT",
                request.decisionReason(),
                principal.userId(),
                decidedAt
        );
        try {
            notificationService.notifyDecision(manuscript.submitterId(), manuscript.manuscriptId(), "DESK_REJECT");
        } catch (Exception ex) {
            LOGGER.warn("Decision notification failed for manuscript {}", manuscript.manuscriptId(), ex);
        }
        try {
            businessOperationsService.recordDecisionCommunication(
                    manuscript.conferenceId(),
                    manuscript.manuscriptId(),
                    manuscript.submitterId(),
                    decisionId,
                    "DESK_REJECT"
            );
        } catch (Exception ex) {
            LOGGER.warn("Decision communication log failed for manuscript {}", manuscript.manuscriptId(), ex);
        }
        auditLogService.recordDecision(principal.userId(), roundId, manuscript.manuscriptId(), "DESK_REJECT");

        return new DecisionResponse(decisionId, "DESK_REJECT", nextStatus, "COMPLETED");
    }

    private String resolveNextStatus(String manuscriptStatus, String decisionCode) {
        if ("DESK_REJECT".equals(decisionCode)) {
            if (!SCREENING_STATUSES.contains(manuscriptStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Desk reject requires manuscript to be under screening");
            }
            return "DESK_REJECTED";
        }

        if (!REVIEW_DECISIONS.contains(decisionCode) || !"UNDER_REVIEW".equals(manuscriptStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Decision is not allowed from the manuscript current state");
        }

        return switch (decisionCode) {
            case "ACCEPT" -> "ACCEPTED";
            case "REJECT" -> "REJECTED";
            case "MINOR_REVISION", "MAJOR_REVISION" -> "REVISION_REQUIRED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid decision code");
        };
    }

    private void validateRequest(DecisionRequest request) {
        if (!DECISION_CODES.contains(request.decisionCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid decision code");
        }
    }

}

record DecisionRequest(
        long manuscriptId,
        long roundId,
        long versionId,
        String decisionCode,
        String decisionReason
) {
}

record ScreeningDeskRejectRequest(
        long manuscriptId,
        long versionId,
        String decisionReason
) {
}

record DecisionResponse(long decisionId, String decisionCode, String currentStatus, String roundStatus) {
}
