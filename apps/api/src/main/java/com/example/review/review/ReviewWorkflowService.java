package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.example.review.manuscript.ManuscriptRepository;
import com.example.review.manuscript.ManuscriptRepository.LockedManuscriptRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewWorkflowService {
    private static final Set<String> ROUND_CREATION_ALLOWED_MANUSCRIPT_STATUSES = Set.of(
            "SUBMITTED",
            "REVISED_SUBMITTED",
            "UNDER_SCREENING"
    );
    private static final Set<String> ROUND_STRATEGIES = Set.of("REUSE_REVIEWERS", "REALLOCATE_REVIEWERS");

    private final ManuscriptRepository manuscriptRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final AssignmentDraftRepository assignmentDraftRepository;
    private final ConflictCheckService conflictCheckService;

    public ReviewWorkflowService(
            ManuscriptRepository manuscriptRepository,
            ReviewRoundRepository reviewRoundRepository,
            ReviewAssignmentRepository reviewAssignmentRepository,
            AssignmentDraftRepository assignmentDraftRepository,
            ConflictCheckService conflictCheckService
    ) {
        this.manuscriptRepository = manuscriptRepository;
        this.reviewRoundRepository = reviewRoundRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.assignmentDraftRepository = assignmentDraftRepository;
        this.conflictCheckService = conflictCheckService;
    }

    @Transactional
    public ReviewRoundResponse createRound(CurrentUserPrincipal principal, CreateReviewRoundRequest request) {
        RoleGuard.requireChairOrAdmin(principal);
        validateRoundRequest(request);

        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(request.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
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
        manuscriptRepository.updateStatusAndRoundNo(request.manuscriptId(), "UNDER_REVIEW", roundNo);

        return toRoundResponse(reviewRoundRepository.findById(roundId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse assignReviewer(CurrentUserPrincipal principal, long roundId, CreateAssignmentRequest request) {
        RoleGuard.requireChairOrAdmin(principal);

        ReviewRoundRow round = reviewRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
        return createAssignment(
                round,
                request.reviewerId(),
                request.deadlineAt() == null ? null : Timestamp.from(request.deadlineAt())
        );
    }

    public List<AssignmentCandidateResponse> listAssignmentCandidates(CurrentUserPrincipal principal, long roundId) {
        RoleGuard.requireChairOrAdmin(principal);
        ReviewRoundRow round = reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
        return reviewAssignmentRepository.listEligibleCandidates(roundId).stream()
                .map(this::toCandidateResponse)
                .toList();
    }

    @Transactional
    public AutoAssignResponse autoAssignConferenceReviewers(
            CurrentUserPrincipal principal,
            long conferenceId,
            AutoAssignRequest request
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        int reviewsPerPaper = normalizeReviewsPerPaper(request == null ? null : request.reviewsPerPaper());
        List<AssignmentActionResponse> created = new ArrayList<>();
        for (ReviewRoundRow round : reviewAssignmentRepository.listAssignableRoundsForConference(conferenceId)) {
            LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
            ensureConferenceChairOrAdmin(principal, manuscript);
            int remaining = reviewsPerPaper - reviewAssignmentRepository.countActiveAssignmentsForRound(round.roundId());
            if (remaining <= 0) {
                continue;
            }
            Timestamp deadline = request == null || request.deadlineAt() == null
                    ? round.deadlineAt()
                    : Timestamp.from(request.deadlineAt());
            List<AssignmentCandidateDetailRow> candidates = reviewAssignmentRepository.listEligibleCandidates(round.roundId());
            for (AssignmentCandidateDetailRow candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                AssignmentActionResponse assignment = createAssignment(round, candidate.reviewerId(), deadline);
                created.add(assignment);
                remaining--;
            }
        }
        return new AutoAssignResponse(conferenceId, reviewsPerPaper, created.size(), created);
    }

    @Transactional
    public RandomAssignmentPreviewResponse previewRandomConferenceAssignments(
            CurrentUserPrincipal principal,
            long conferenceId,
            RandomAssignmentPreviewRequest request
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        int reviewsPerPaper = normalizeReviewsPerPaper(request == null ? null : request.reviewsPerPaper());
        for (ReviewRoundRow round : reviewAssignmentRepository.listAssignableRoundsForConference(conferenceId)) {
            LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
            ensureConferenceChairOrAdmin(principal, manuscript);
            assignmentDraftRepository.clearOpenDrafts(round.roundId());
            int remaining = reviewsPerPaper - reviewAssignmentRepository.countActiveAssignmentsForRound(round.roundId());
            if (remaining <= 0) {
                continue;
            }
            List<AssignmentCandidateDetailRow> candidates = new ArrayList<>(reviewAssignmentRepository.listEligibleCandidates(round.roundId()));
            Collections.shuffle(candidates);
            int rank = 1;
            for (AssignmentCandidateDetailRow candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                assignmentDraftRepository.insertDraft(new AssignmentDraftInsert(
                        candidate.roundId(),
                        candidate.manuscriptId(),
                        candidate.versionId(),
                        candidate.reviewerId(),
                        rank,
                        candidateScore(candidate.bidValue(), candidate.currentLoad(), candidate.maxLoad()),
                        candidate.currentLoad(),
                        candidate.maxLoad(),
                        candidate.bidValue(),
                        "Random preview; bid=" + candidate.bidValue() + "; load=" + candidate.currentLoad() + "/" + candidate.maxLoad(),
                        principal.userId()
                ));
                rank++;
                remaining--;
            }
        }
        List<AssignmentDraftResponse> drafts = assignmentDraftRepository.listOpenByConference(conferenceId).stream()
                .map(this::toDraftResponse)
                .toList();
        return new RandomAssignmentPreviewResponse(conferenceId, reviewsPerPaper, drafts.size(), drafts);
    }

    @Transactional
    public ConfirmAssignmentPreviewResponse confirmRandomConferenceAssignmentPreview(
            CurrentUserPrincipal principal,
            long conferenceId,
            ConfirmAssignmentPreviewRequest request
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        List<AssignmentActionResponse> created = new ArrayList<>();
        for (AssignmentDraftRow draft : assignmentDraftRepository.findOpenByConferenceForUpdate(conferenceId)) {
            ReviewRoundRow round = reviewRoundRepository.findByIdForUpdate(draft.roundId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
            LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
            ensureConferenceChairOrAdmin(principal, manuscript);
            Timestamp deadline = request == null || request.deadlineAt() == null
                    ? round.deadlineAt()
                    : Timestamp.from(request.deadlineAt());
            created.add(createAssignment(round, draft.reviewerId(), deadline));
            assignmentDraftRepository.markConfirmed(draft.draftId());
        }
        return new ConfirmAssignmentPreviewResponse(conferenceId, created.size(), created);
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
        RoleGuard.requireChairOrAdmin(principal);
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(assignment.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
        if (!Set.of("ASSIGNED", "ACCEPTED").contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment cannot be marked overdue from its current state");
        }
        reviewAssignmentRepository.updateStatus(assignmentId, "OVERDUE");
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    @Transactional
    public AssignmentActionResponse reassign(CurrentUserPrincipal principal, long assignmentId, CreateAssignmentRequest request) {
        RoleGuard.requireChairOrAdmin(principal);
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(assignment.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
        if (!"OVERDUE".equals(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OVERDUE assignments can be reassigned");
        }
        ensureReviewerCanBeAssigned(assignment.manuscriptId(), request.reviewerId());
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
        RoleGuard.requireChairOrAdmin(principal);
        ReviewRoundRow round = reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        LockedManuscriptRow manuscript = manuscriptRepository.findLockedById(round.manuscriptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureConferenceChairOrAdmin(principal, manuscript);
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

    private AssignmentCandidateResponse toCandidateResponse(AssignmentCandidateDetailRow row) {
        return new AssignmentCandidateResponse(
                row.reviewerId(),
                row.reviewerName(),
                row.institution(),
                row.currentLoad(),
                row.maxLoad(),
                row.bidValue(),
                candidateScore(row.bidValue(), row.currentLoad(), row.maxLoad()),
                "bid=" + row.bidValue() + "; load=" + row.currentLoad() + "/" + row.maxLoad()
        );
    }

    private AssignmentDraftResponse toDraftResponse(AssignmentDraftRow row) {
        return new AssignmentDraftResponse(
                row.draftId(),
                row.roundId(),
                row.manuscriptId(),
                row.versionId(),
                row.reviewerId(),
                row.rankOrder(),
                row.score(),
                row.currentLoad(),
                row.maxLoad(),
                row.bidValue(),
                row.reason(),
                row.draftStatus()
        );
    }

    private int normalizeReviewsPerPaper(Integer requested) {
        int reviewsPerPaper = requested == null ? 3 : requested;
        if (reviewsPerPaper < 1 || reviewsPerPaper > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reviews per paper must be between 1 and 10");
        }
        return reviewsPerPaper;
    }

    private int candidateScore(String bidValue, int currentLoad, int maxLoad) {
        int bidScore = switch (bidValue == null ? "NEUTRAL" : bidValue) {
            case "WANT_TO_REVIEW" -> 100;
            case "NEUTRAL" -> 50;
            default -> 0;
        };
        return bidScore + Math.max(0, maxLoad - currentLoad);
    }

    private AssignmentActionResponse createAssignment(ReviewRoundRow round, long reviewerId, Timestamp deadline) {
        ensureReviewerCanBeAssigned(round.manuscriptId(), reviewerId);
        long assignmentId = reviewAssignmentRepository.nextAssignmentId();
        reviewAssignmentRepository.insert(
                assignmentId,
                round.roundId(),
                round.manuscriptId(),
                round.versionId(),
                reviewerId,
                "ASSIGNED",
                deadline,
                null
        );
        if ("PENDING".equals(round.roundStatus())) {
            reviewRoundRepository.updateStatus(round.roundId(), "IN_PROGRESS");
        }
        conflictCheckService.detectSameInstitutionConflict(assignmentId, round.manuscriptId(), round.versionId(), reviewerId);
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
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

    private void ensureConferenceChairOrAdmin(CurrentUserPrincipal principal, LockedManuscriptRow manuscript) {
        if (RoleGuard.hasRole(principal, "ADMIN")) {
            return;
        }
        if (manuscript.organizerUserId() == null || !manuscript.organizerUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chair is not assigned to this manuscript conference");
        }
    }

    private void ensureReviewerCanBeAssigned(long manuscriptId, long reviewerId) {
        AssignmentEligibilityRow eligibility = reviewAssignmentRepository.findEligibilityForAssignment(manuscriptId, reviewerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (!eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewer is not eligible for this manuscript assignment");
        }
    }

    private ReviewAssignmentRow findOwnedAssignmentForReviewUser(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment does not belong to current reviewer");
        }
        return assignment;
    }

}
