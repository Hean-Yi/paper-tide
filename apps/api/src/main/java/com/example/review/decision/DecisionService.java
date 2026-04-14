package com.example.review.decision;

import com.example.review.audit.AuditLogService;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.example.review.manuscript.ManuscriptRepository;
import com.example.review.manuscript.ManuscriptRepository.LockedManuscriptRow;
import com.example.review.notification.NotificationService;
import com.example.review.review.ReviewAssignmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionService.class);
    private static final Set<String> DECISION_CODES = Set.of("ACCEPT", "REJECT", "MINOR_REVISION", "MAJOR_REVISION", "DESK_REJECT");
    private static final Set<String> REVIEW_DECISIONS = Set.of("ACCEPT", "REJECT", "MINOR_REVISION", "MAJOR_REVISION");
    private static final Set<String> SCREENING_STATUSES = Set.of("UNDER_SCREENING");

    private final JdbcTemplate jdbcTemplate;
    private final ManuscriptRepository manuscriptRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final DecisionRepository decisionRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public DecisionService(
            JdbcTemplate jdbcTemplate,
            ManuscriptRepository manuscriptRepository,
            ReviewAssignmentRepository reviewAssignmentRepository,
            DecisionRepository decisionRepository,
            NotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.manuscriptRepository = manuscriptRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.decisionRepository = decisionRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DecisionResponse decide(CurrentUserPrincipal principal, DecisionRequest request) {
        RoleGuard.requireChairOrAdmin(principal);
        validateRequest(request);

        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(request.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ReviewRoundDecisionRow round = findRoundForUpdate(request.roundId());
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
        jdbcTemplate.update("UPDATE REVIEW_ROUND SET ROUND_STATUS = 'COMPLETED' WHERE ROUND_ID = ?", round.roundId());
        reviewAssignmentRepository.cancelOpenAssignmentsForRound(round.roundId());
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_STATUS = ?, LAST_DECISION_CODE = ? WHERE MANUSCRIPT_ID = ?",
                nextStatus,
                request.decisionCode(),
                manuscript.manuscriptId()
        );

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
        auditLogService.recordDecision(principal.userId(), round.roundId(), manuscript.manuscriptId(), request.decisionCode());

        return new DecisionResponse(decisionId, request.decisionCode(), nextStatus, "COMPLETED");
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

    private ReviewRoundDecisionRow findRoundForUpdate(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, CREATED_BY
                FROM REVIEW_ROUND
                WHERE ROUND_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ReviewRoundDecisionRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("ROUND_STATUS"),
                        rs.getLong("CREATED_BY")
                ),
                roundId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
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

record DecisionResponse(long decisionId, String decisionCode, String currentStatus, String roundStatus) {
}

record ReviewRoundDecisionRow(
        long roundId,
        long manuscriptId,
        int roundNo,
        long versionId,
        String roundStatus,
        long createdBy
) {
}
