package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssignmentDraftService {
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final AssignmentDraftRepository assignmentDraftRepository;
    private final ConflictCheckService conflictCheckService;

    public AssignmentDraftService(
            ReviewRoundRepository reviewRoundRepository,
            ReviewAssignmentRepository reviewAssignmentRepository,
            AssignmentDraftRepository assignmentDraftRepository,
            ConflictCheckService conflictCheckService
    ) {
        this.reviewRoundRepository = reviewRoundRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.assignmentDraftRepository = assignmentDraftRepository;
        this.conflictCheckService = conflictCheckService;
    }

    @Transactional
    public List<AssignmentDraftResponse> generateDrafts(
            CurrentUserPrincipal principal,
            long roundId,
            GenerateAssignmentDraftRequest request
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        ReviewRoundRow round = reviewRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        if (!List.of("PENDING", "IN_PROGRESS").contains(round.roundStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review round does not allow assignment drafts");
        }
        int limit = request == null || request.limit() == null ? 10 : request.limit();
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft limit is invalid");
        }
        assignmentDraftRepository.clearOpenDrafts(roundId);
        List<AssignmentCandidateRow> candidates = assignmentDraftRepository.listCandidates(roundId).stream()
                .limit(limit)
                .toList();
        int rank = 1;
        for (AssignmentCandidateRow candidate : candidates) {
            assignmentDraftRepository.insertDraft(new AssignmentDraftInsert(
                    candidate.roundId(),
                    candidate.manuscriptId(),
                    candidate.versionId(),
                    candidate.reviewerId(),
                    rank,
                    score(candidate),
                    candidate.currentLoad(),
                    candidate.maxLoad(),
                    candidate.bidValue(),
                    reason(candidate),
                    principal.userId()
            ));
            rank++;
        }
        return listDrafts(principal, roundId);
    }

    public List<AssignmentDraftResponse> listDrafts(CurrentUserPrincipal principal, long roundId) {
        RoleGuard.requireChairOrAdmin(principal);
        reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        return assignmentDraftRepository.listByRound(roundId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<AssignmentActionResponse> confirmDrafts(
            CurrentUserPrincipal principal,
            long roundId,
            ConfirmAssignmentDraftRequest request
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        if (request == null || request.draftIds() == null || request.draftIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one draft id is required");
        }
        if (new HashSet<>(request.draftIds()).size() != request.draftIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft ids must be unique");
        }
        ReviewRoundRow round = reviewRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        List<AssignmentDraftRow> drafts = assignmentDraftRepository.findDraftsForUpdate(roundId, request.draftIds());
        if (drafts.size() != request.draftIds().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more assignment drafts were not found");
        }
        Timestamp deadline = request.deadlineAt() == null ? round.deadlineAt() : Timestamp.from(request.deadlineAt());
        return drafts.stream()
                .map(draft -> confirmOne(round, draft, deadline))
                .toList();
    }

    private AssignmentActionResponse confirmOne(ReviewRoundRow round, AssignmentDraftRow draft, Timestamp deadline) {
        if (!"PROPOSED".equals(draft.draftStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only proposed drafts can be confirmed");
        }
        ConferenceReviewerLockRow reviewer = assignmentDraftRepository.lockConferenceReviewer(draft.manuscriptId(), draft.reviewerId());
        int currentLoad = assignmentDraftRepository.currentLoad(reviewer.reviewerId());
        if (currentLoad >= reviewer.maxLoad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewer max load would be exceeded");
        }
        if (assignmentDraftRepository.assignmentExists(round.roundId(), reviewer.reviewerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewer is already assigned to this round");
        }
        long assignmentId = reviewAssignmentRepository.nextAssignmentId();
        reviewAssignmentRepository.insert(
                assignmentId,
                round.roundId(),
                round.manuscriptId(),
                round.versionId(),
                reviewer.reviewerId(),
                "ASSIGNED",
                deadline,
                null
        );
        assignmentDraftRepository.markConfirmed(draft.draftId());
        if ("PENDING".equals(round.roundStatus())) {
            reviewRoundRepository.updateStatus(round.roundId(), "IN_PROGRESS");
        }
        conflictCheckService.detectSameInstitutionConflict(assignmentId, round.manuscriptId(), round.versionId(), reviewer.reviewerId());
        return toAssignmentResponse(reviewAssignmentRepository.findById(assignmentId).orElseThrow());
    }

    private int score(AssignmentCandidateRow candidate) {
        int bidScore = switch (candidate.bidValue()) {
            case "WANT_TO_REVIEW" -> 100;
            case "NEUTRAL" -> 50;
            default -> 0;
        };
        return bidScore + Math.max(0, candidate.maxLoad() - candidate.currentLoad());
    }

    private String reason(AssignmentCandidateRow candidate) {
        return "bid=" + candidate.bidValue() + "; load=" + candidate.currentLoad() + "/" + candidate.maxLoad();
    }

    private AssignmentDraftResponse toResponse(AssignmentDraftRow row) {
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

    private AssignmentActionResponse toAssignmentResponse(ReviewAssignmentRow assignment) {
        return new AssignmentActionResponse(
                assignment.assignmentId(),
                assignment.taskStatus(),
                assignment.reviewerId(),
                assignment.reassignedFromId()
        );
    }
}
