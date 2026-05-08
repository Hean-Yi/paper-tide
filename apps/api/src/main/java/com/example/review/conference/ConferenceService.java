package com.example.review.conference;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConferenceService {
    private static final Set<String> BLIND_MODES = Set.of("DOUBLE_BLIND", "SINGLE_BLIND", "OPEN");
    private static final List<String> STATUS_ORDER = List.of(
            "DRAFT",
            "PENDING_APPROVAL",
            "OPEN_FOR_SUBMISSION",
            "SUBMISSION_CLOSED",
            "BIDDING_OPEN",
            "REVIEW_ASSIGNMENT",
            "REVIEWING",
            "DECISION",
            "CLOSED"
    );
    private static final Set<String> PUBLIC_CFP_STATUSES = Set.of(
            "OPEN_FOR_SUBMISSION",
            "SUBMISSION_CLOSED",
            "BIDDING_OPEN",
            "REVIEW_ASSIGNMENT",
            "REVIEWING",
            "DECISION",
            "CLOSED"
    );

    private final ConferenceRepository repository;
    private final Clock clock;

    public ConferenceService(ConferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ConferenceDetail createDraft(CurrentUserPrincipal principal, ConferenceDraftRequest request) {
        requireChairOrAdmin(principal);
        ValidatedDraft validated = validateDraft(request);
        if (repository.publicSlugExists(validated.publicSlug())) {
            throw new ConferenceValidationException("Conference public slug already exists");
        }
        long conferenceId = repository.createConference(new ConferenceDraft(
                validated.name(),
                validated.acronym(),
                validated.year(),
                principal.userId(),
                "DRAFT",
                validated.blindMode(),
                validated.cfpText(),
                validated.topicAreas(),
                validated.targetReviewsPerPaper(),
                validated.defaultReviewerMaxLoad(),
                validated.publicSlug(),
                false
        ));
        repository.createPhase(conferenceId, validated.phase());
        return getRequired(conferenceId);
    }

    @Transactional
    public ConferenceDetail submitForApproval(long conferenceId, CurrentUserPrincipal principal) {
        requireChairOrAdmin(principal);
        ConferenceDetail detail = getRequired(conferenceId);
        if (!RoleGuard.hasRole(principal, "ADMIN") && detail.organizerUserId() != principal.userId()) {
            throw new ConferenceAccessException("Only the conference organizer or an admin can submit this conference");
        }
        if (detail.status().equals("PENDING_APPROVAL")) {
            return detail;
        }
        if (!detail.status().equals("DRAFT")) {
            throw new ConferenceValidationException("Only draft conferences can be submitted for approval");
        }
        repository.updateStatus(conferenceId, "PENDING_APPROVAL", false, detail.approvedBy(), detail.approvedAt());
        return getRequired(conferenceId);
    }

    @Transactional
    public ConferenceDetail approveConference(long conferenceId, CurrentUserPrincipal principal) {
        requireAdmin(principal);
        ConferenceDetail detail = getRequired(conferenceId);
        if (detail.status().equals("OPEN_FOR_SUBMISSION")) {
            return detail;
        }
        if (!detail.status().equals("PENDING_APPROVAL")) {
            throw new ConferenceValidationException("Only pending conferences can be approved");
        }
        repository.updateStatus(conferenceId, "OPEN_FOR_SUBMISSION", true, principal.userId(), Instant.now(clock));
        return getRequired(conferenceId);
    }

    @Transactional
    public ConferenceDetail advanceStatus(long conferenceId, CurrentUserPrincipal principal, String requestedStatus) {
        requireChairOrAdmin(principal);
        ConferenceDetail detail = getRequired(conferenceId);
        if (!RoleGuard.hasRole(principal, "ADMIN") && detail.organizerUserId() != principal.userId()) {
            throw new ConferenceAccessException("Only the conference organizer or an admin can advance this conference");
        }
        String nextStatus = normalizeStatus(requestedStatus);
        if (nextStatus.equals(detail.status())) {
            return detail;
        }
        int currentIndex = STATUS_ORDER.indexOf(detail.status());
        int nextIndex = STATUS_ORDER.indexOf(nextStatus);
        if (currentIndex < 0 || nextIndex != currentIndex + 1) {
            throw new ConferenceValidationException("Conference status transition is not allowed");
        }
        if (nextStatus.equals("PENDING_APPROVAL") || nextStatus.equals("OPEN_FOR_SUBMISSION")) {
            throw new ConferenceValidationException("Use the approval workflow for this transition");
        }
        repository.updateStatus(conferenceId, nextStatus, detail.cfpPublished(), detail.approvedBy(), detail.approvedAt());
        return getRequired(conferenceId);
    }

    public List<ConferenceSummary> listPublicCfps() {
        return repository.listPublicCfps();
    }

    public ConferenceDetail getPublicCfp(String publicSlug) {
        String normalizedSlug = normalizeSlug(publicSlug);
        return repository.findPublicCfpBySlug(normalizedSlug)
                .filter(detail -> detail.cfpPublished() && PUBLIC_CFP_STATUSES.contains(detail.status()))
                .orElseThrow(() -> new ConferenceNotFoundException("Public conference CFP was not found"));
    }

    public List<ConferenceSummary> listPendingApproval(CurrentUserPrincipal principal) {
        requireAdmin(principal);
        return repository.listPendingApproval();
    }

    public List<ConferenceSummary> listManageableConferences(CurrentUserPrincipal principal) {
        requireChairOrAdmin(principal);
        Long organizerUserId = RoleGuard.hasRole(principal, "ADMIN") ? null : principal.userId();
        return repository.listManageable(organizerUserId);
    }

    public ConferenceDetail getManageableConference(long conferenceId, CurrentUserPrincipal principal) {
        requireChairOrAdmin(principal);
        ConferenceDetail detail = getRequired(conferenceId);
        if (!RoleGuard.hasRole(principal, "ADMIN") && detail.organizerUserId() != principal.userId()) {
            throw new ConferenceAccessException("Only the conference organizer or an admin can view this conference");
        }
        return detail;
    }

    private ConferenceDetail getRequired(long conferenceId) {
        return repository.findDetail(conferenceId)
                .orElseThrow(() -> new ConferenceNotFoundException("Conference was not found"));
    }

    private ValidatedDraft validateDraft(ConferenceDraftRequest request) {
        if (request == null) {
            throw new ConferenceValidationException("Conference request is required");
        }
        String name = requireText(request.name(), "Conference name is required");
        String acronym = requireText(request.acronym(), "Conference acronym is required").toUpperCase(Locale.ROOT);
        if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
            throw new ConferenceValidationException("Conference year is invalid");
        }
        String blindMode = requireText(request.blindMode(), "Blind mode is required").toUpperCase(Locale.ROOT);
        if (!BLIND_MODES.contains(blindMode)) {
            throw new ConferenceValidationException("Blind mode is invalid");
        }
        String cfpText = requireText(request.cfpText(), "CFP text is required");
        List<String> topicAreas = request.topicAreas() == null ? List.of() : request.topicAreas().stream()
                .map(area -> area == null ? "" : area.trim())
                .filter(area -> !area.isBlank())
                .distinct()
                .toList();
        if (topicAreas.isEmpty()) {
            throw new ConferenceValidationException("At least one topic area is required");
        }
        int targetReviews = requireRange(request.targetReviewsPerPaper(), 1, 10, "Target reviews per paper is invalid");
        int maxLoad = requireRange(request.defaultReviewerMaxLoad(), 1, 20, "Default reviewer max load is invalid");
        String publicSlug = normalizeSlug(request.publicSlug());
        validateSlug(publicSlug);
        ConferencePhaseDraft phase = validatePhase(request.phase());
        return new ValidatedDraft(name, acronym, request.year(), blindMode, cfpText, topicAreas,
                targetReviews, maxLoad, publicSlug, phase);
    }

    private ConferencePhaseDraft validatePhase(ConferencePhaseRequest phase) {
        if (phase == null) {
            throw new ConferenceValidationException("Conference phase schedule is required");
        }
        requireInstant(phase.submissionOpenAt(), "Submission open time is required");
        requireInstant(phase.submissionCloseAt(), "Submission close time is required");
        requireInstant(phase.biddingOpenAt(), "Bidding open time is required");
        requireInstant(phase.biddingCloseAt(), "Bidding close time is required");
        requireInstant(phase.reviewDeadlineAt(), "Review deadline is required");
        requireInstant(phase.decisionReleaseAt(), "Decision release time is required");
        if (!phase.submissionOpenAt().isBefore(phase.submissionCloseAt())
                || !phase.submissionCloseAt().isBefore(phase.biddingOpenAt())
                || !phase.biddingOpenAt().isBefore(phase.biddingCloseAt())
                || !phase.biddingCloseAt().isBefore(phase.reviewDeadlineAt())
                || !phase.reviewDeadlineAt().isBefore(phase.decisionReleaseAt())) {
            throw new ConferenceValidationException("Conference phase timestamps must be in lifecycle order");
        }
        return new ConferencePhaseDraft(phase.submissionOpenAt(), phase.submissionCloseAt(),
                phase.biddingOpenAt(), phase.biddingCloseAt(), phase.reviewDeadlineAt(), phase.decisionReleaseAt());
    }

    private String normalizeStatus(String status) {
        String normalized = requireText(status, "Conference status is required").toUpperCase(Locale.ROOT);
        if (!STATUS_ORDER.contains(normalized)) {
            throw new ConferenceValidationException("Conference status is invalid");
        }
        return normalized;
    }

    private String normalizeSlug(String value) {
        return requireText(value, "Conference public slug is required").toLowerCase(Locale.ROOT);
    }

    private void validateSlug(String slug) {
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ConferenceValidationException("Conference public slug may contain lowercase letters, numbers, and hyphens");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ConferenceValidationException(message);
        }
        return value.trim();
    }

    private void requireInstant(Instant value, String message) {
        if (value == null) {
            throw new ConferenceValidationException(message);
        }
    }

    private int requireRange(Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) {
            throw new ConferenceValidationException(message);
        }
        return value;
    }

    private void requireChairOrAdmin(CurrentUserPrincipal principal) {
        if (!RoleGuard.hasRole(principal, "CHAIR") && !RoleGuard.hasRole(principal, "ADMIN")) {
            throw new ConferenceAccessException("Chair or Admin role is required");
        }
    }

    private void requireAdmin(CurrentUserPrincipal principal) {
        if (!RoleGuard.hasRole(principal, "ADMIN")) {
            throw new ConferenceAccessException("Admin role is required");
        }
    }

    private record ValidatedDraft(
            String name,
            String acronym,
            int year,
            String blindMode,
            String cfpText,
            List<String> topicAreas,
            int targetReviewsPerPaper,
            int defaultReviewerMaxLoad,
            String publicSlug,
            ConferencePhaseDraft phase
    ) {
    }
}
