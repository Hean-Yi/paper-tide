package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewWorkflowService {
    private static final Set<String> ROUND_CREATION_ALLOWED_MANUSCRIPT_STATUSES = Set.of("SUBMITTED", "REVISED_SUBMITTED");
    private static final Set<String> ROUND_STRATEGIES = Set.of("REUSE_REVIEWERS", "REALLOCATE_REVIEWERS");

    private final JdbcTemplate jdbcTemplate;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ConflictCheckService conflictCheckService;

    public ReviewWorkflowService(
            JdbcTemplate jdbcTemplate,
            ReviewRoundRepository reviewRoundRepository,
            ReviewAssignmentRepository reviewAssignmentRepository,
            ConflictCheckService conflictCheckService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.reviewRoundRepository = reviewRoundRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.conflictCheckService = conflictCheckService;
    }

    @Transactional
    public ReviewRoundResponse createRound(CurrentUserPrincipal principal, CreateReviewRoundRequest request) {
        requireChairOrAdmin(principal);
        validateRoundRequest(request);

        ManuscriptReviewRow manuscript = findManuscriptForUpdate(request.manuscriptId());
        if (!ROUND_CREATION_ALLOWED_MANUSCRIPT_STATUSES.contains(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manuscript state does not allow round creation");
        }
        if (manuscript.currentVersionId() == null || manuscript.currentVersionId() != request.versionId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round must target the manuscript current version");
        }

        long roundId = reviewRoundRepository.nextRoundId();
        int roundNo = reviewRoundRepository.nextRoundNo(request.manuscriptId());
        Timestamp deadlineAt = request.deadlineAt() == null ? null : Timestamp.from(request.deadlineAt());
        reviewRoundRepository.insert(
                roundId,
                request.manuscriptId(),
                roundNo,
                request.versionId(),
                "PENDING",
                request.assignmentStrategy(),
                request.screeningRequired(),
                deadlineAt,
                principal.userId()
        );
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_STATUS = 'UNDER_REVIEW', CURRENT_ROUND_NO = ? WHERE MANUSCRIPT_ID = ?",
                roundNo,
                request.manuscriptId()
        );

        return toRoundResponse(reviewRoundRepository.findById(roundId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse assignReviewer(CurrentUserPrincipal principal, long roundId, CreateAssignmentRequest request) {
        requireChairOrAdmin(principal);

        ReviewRoundRow round = reviewRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));

        long assignmentId = reviewAssignmentRepository.nextAssignmentId();
        reviewAssignmentRepository.insert(
                assignmentId,
                round.roundId(),
                round.manuscriptId(),
                round.versionId(),
                request.reviewerId(),
                "ASSIGNED",
                request.deadlineAt() == null ? null : Timestamp.from(request.deadlineAt()),
                null
        );
        if ("PENDING".equals(round.roundStatus())) {
            reviewRoundRepository.updateStatus(round.roundId(), "IN_PROGRESS");
        }
        conflictCheckService.detectSameInstitutionConflict(assignmentId, round.manuscriptId(), round.versionId(), request.reviewerId());

        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse acceptAssignment(CurrentUserPrincipal principal, long assignmentId) {
        ReviewAssignmentRow assignment = findOwnedAssignmentForReviewUser(principal, assignmentId);
        if (!"ASSIGNED".equals(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ASSIGNED tasks can be accepted");
        }
        reviewAssignmentRepository.markAccepted(assignmentId, Timestamp.from(Instant.now()));
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse declineAssignment(CurrentUserPrincipal principal, long assignmentId, DeclineAssignmentRequest request) {
        ReviewAssignmentRow assignment = findOwnedAssignmentForReviewUser(principal, assignmentId);
        if (!Set.of("ASSIGNED", "ACCEPTED").contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment cannot be declined from its current state");
        }
        reviewAssignmentRepository.markDeclined(assignmentId, Timestamp.from(Instant.now()), request.reason());
        if (request.conflictDeclared()) {
            conflictCheckService.recordSelfDeclaredConflict(
                    assignment.assignmentId(),
                    assignment.manuscriptId(),
                    assignment.reviewerId(),
                    principal.userId(),
                    request.reason()
            );
        }
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse markOverdue(CurrentUserPrincipal principal, long assignmentId) {
        requireChairOrAdmin(principal);
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!Set.of("ASSIGNED", "ACCEPTED").contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment cannot be marked overdue from its current state");
        }
        reviewAssignmentRepository.updateStatus(assignmentId, "OVERDUE");
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse reassign(CurrentUserPrincipal principal, long assignmentId, CreateAssignmentRequest request) {
        requireChairOrAdmin(principal);
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!"OVERDUE".equals(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OVERDUE assignments can be reassigned");
        }
        reviewAssignmentRepository.updateStatus(assignmentId, "REASSIGNED");
        long newAssignmentId = reviewAssignmentRepository.nextAssignmentId();
        reviewAssignmentRepository.insert(
                newAssignmentId,
                assignment.roundId(),
                assignment.manuscriptId(),
                assignment.versionId(),
                request.reviewerId(),
                "ASSIGNED",
                request.deadlineAt() == null ? null : Timestamp.from(request.deadlineAt()),
                assignment.assignmentId()
        );
        conflictCheckService.detectSameInstitutionConflict(newAssignmentId, assignment.manuscriptId(), assignment.versionId(), request.reviewerId());
        return toAssignmentResponse(reviewAssignmentRepository.findById(newAssignmentId).orElseThrow());
    }

    public java.util.List<ConflictCheckResponse> listConflictChecks(CurrentUserPrincipal principal, long roundId) {
        requireChairOrAdmin(principal);
        reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        return conflictCheckService.listByRound(roundId);
    }

    private AssignmentActionResponse toAssignmentResponse(ReviewAssignmentRow assignment) {
        return new AssignmentActionResponse(
                assignment.assignmentId(),
                assignment.taskStatus(),
                assignment.reviewerId(),
                assignment.reassignedFromId()
        );
    }

    private ReviewRoundResponse toRoundResponse(ReviewRoundRow round) {
        return new ReviewRoundResponse(
                round.roundId(),
                round.roundNo(),
                round.roundStatus(),
                round.manuscriptId(),
                round.versionId(),
                round.assignmentStrategy(),
                round.screeningRequired(),
                round.deadlineAt()
        );
    }

    private void validateRoundRequest(CreateReviewRoundRequest request) {
        if (!ROUND_STRATEGIES.contains(request.assignmentStrategy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid assignment strategy");
        }
    }

    private ManuscriptReviewRow findManuscriptForUpdate(long manuscriptId) {
        return jdbcTemplate.query(
                """
                SELECT MANUSCRIPT_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO
                FROM MANUSCRIPT
                WHERE MANUSCRIPT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ManuscriptReviewRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getObject("CURRENT_VERSION_ID", Long.class),
                        rs.getString("CURRENT_STATUS"),
                        rs.getInt("CURRENT_ROUND_NO")
                ),
                manuscriptId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
    }

    private ReviewAssignmentRow findOwnedAssignmentForReviewUser(CurrentUserPrincipal principal, long assignmentId) {
        requireReviewer(principal);
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment does not belong to current reviewer");
        }
        return assignment;
    }

    private void requireChairOrAdmin(CurrentUserPrincipal principal) {
        if (principal == null || principal.roles().stream().noneMatch(role -> "CHAIR".equals(role) || "ADMIN".equals(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chair or admin role required");
        }
    }

    private void requireReviewer(CurrentUserPrincipal principal) {
        if (principal == null || principal.roles().stream().noneMatch("REVIEWER"::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer role required");
        }
    }
}

record ManuscriptReviewRow(
        long manuscriptId,
        Long currentVersionId,
        String currentStatus,
        int currentRoundNo
) {
}
