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
public class ConferenceReviewerService {
    private static final Set<String> BID_VALUES = Set.of("WANT_TO_REVIEW", "NEUTRAL", "DECLINE");
    private static final Set<String> CONFLICT_TYPES = Set.of("INSTITUTION", "DECLARED", "SELF_CITATION", "OTHER");

    private final ConferenceRepository conferenceRepository;
    private final ConferenceReviewerRepository reviewerRepository;
    private final Clock clock;

    public ConferenceReviewerService(
            ConferenceRepository conferenceRepository,
            ConferenceReviewerRepository reviewerRepository,
            Clock clock
    ) {
        this.conferenceRepository = conferenceRepository;
        this.reviewerRepository = reviewerRepository;
        this.clock = clock;
    }

    @Transactional
    public ConferenceReviewerResponse addReviewer(
            long conferenceId,
            CurrentUserPrincipal principal,
            ConferenceReviewerRequest request
    ) {
        requireChairOrAdmin(principal);
        if (request == null || request.reviewerId() == null) {
            throw new ConferenceValidationException("Reviewer id is required");
        }
        ConferenceDetail conference = getConference(conferenceId);
        requireConferenceOwnerOrAdmin(conference, principal);
        if (!reviewerRepository.isActivePlatformReviewer(request.reviewerId())) {
            throw new ConferenceValidationException("Reviewer must be an active platform reviewer");
        }
        int maxLoad = request.maxLoad() == null ? conference.defaultReviewerMaxLoad() : request.maxLoad();
        if (maxLoad < 1 || maxLoad > 20) {
            throw new ConferenceValidationException("Reviewer max load is invalid");
        }
        List<ResearchAreaSnapshot> researchAreas = reviewerRepository.listUserResearchAreas(request.reviewerId());
        return toResponse(reviewerRepository.upsertConferenceReviewer(
                conferenceId,
                request.reviewerId(),
                maxLoad,
                principal.userId(),
                researchAreas
        ));
    }

    public List<ReviewerBiddingItem> listOpenBiddingItems(long conferenceId, CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "REVIEWER");
        requireActiveMembership(conferenceId, principal.userId());
        requireBiddingOpen(conferenceId);
        return reviewerRepository.listOpenBiddingItems(conferenceId, principal.userId());
    }

    @Transactional
    public ReviewerBidResponse submitBid(long conferenceId, CurrentUserPrincipal principal, ReviewerBidRequest request) {
        RoleGuard.requireRole(principal, "REVIEWER");
        if (request == null || request.manuscriptId() == null) {
            throw new ConferenceValidationException("Manuscript id is required");
        }
        requireActiveMembership(conferenceId, principal.userId());
        requireBiddingOpen(conferenceId);
        if (!reviewerRepository.manuscriptBelongsToConference(conferenceId, request.manuscriptId())) {
            throw new ConferenceAccessException("Manuscript is not in this conference");
        }
        String bidValue = normalizeBidValue(request.bidValue());
        boolean conflictDeclared = Boolean.TRUE.equals(request.conflictDeclared());
        String conflictType = normalizeConflictType(request.conflictType(), conflictDeclared);
        String conflictDescription = normalizeConflictDescription(request.conflictDescription(), conflictDeclared);
        ReviewerBidResponse response = reviewerRepository.saveBid(new ReviewerBidDraft(
                conferenceId,
                request.manuscriptId(),
                principal.userId(),
                bidValue,
                conflictDeclared,
                conflictType,
                conflictDescription
        ));
        if (conflictDeclared && !reviewerRepository.conflictExists(request.manuscriptId(), principal.userId(), conflictType)) {
            reviewerRepository.insertSelfDeclaredConflict(
                    request.manuscriptId(),
                    principal.userId(),
                    conflictType,
                    conflictDescription
            );
        }
        return response;
    }

    private ConferenceDetail getConference(long conferenceId) {
        return conferenceRepository.findDetail(conferenceId)
                .orElseThrow(() -> new ConferenceNotFoundException("Conference was not found"));
    }

    private void requireActiveMembership(long conferenceId, long reviewerId) {
        reviewerRepository.findActiveMembership(conferenceId, reviewerId)
                .orElseThrow(() -> new ConferenceAccessException("Reviewer is not in this conference reviewer pool"));
    }

    private void requireBiddingOpen(long conferenceId) {
        BiddingConferencePolicy policy = reviewerRepository.findBiddingPolicy(conferenceId)
                .orElseThrow(() -> new ConferenceNotFoundException("Conference was not found"));
        if (!policy.status().equals("BIDDING_OPEN")) {
            throw new ConferenceStateException("Conference is not open for bidding");
        }
        Instant now = Instant.now(clock);
        if (!now.isBefore(policy.biddingCloseAt())) {
            throw new ConferenceStateException("Bidding deadline has passed");
        }
    }

    private String normalizeBidValue(String value) {
        String normalized = requireText(value, "Bid value is required").toUpperCase(Locale.ROOT);
        if (!BID_VALUES.contains(normalized)) {
            throw new ConferenceValidationException("Bid value is invalid");
        }
        return normalized;
    }

    private String normalizeConflictType(String value, boolean conflictDeclared) {
        if (!conflictDeclared) {
            return null;
        }
        String normalized = requireText(value, "Conflict type is required").toUpperCase(Locale.ROOT);
        if (!CONFLICT_TYPES.contains(normalized)) {
            throw new ConferenceValidationException("Conflict type is invalid");
        }
        return normalized;
    }

    private String normalizeConflictDescription(String value, boolean conflictDeclared) {
        if (!conflictDeclared) {
            return null;
        }
        return requireText(value, "Conflict description is required");
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ConferenceValidationException(message);
        }
        return value.trim();
    }

    private void requireConferenceOwnerOrAdmin(ConferenceDetail conference, CurrentUserPrincipal principal) {
        if (!RoleGuard.hasRole(principal, "ADMIN") && conference.organizerUserId() != principal.userId()) {
            throw new ConferenceAccessException("Only the conference organizer or an admin can manage the reviewer pool");
        }
    }

    private void requireChairOrAdmin(CurrentUserPrincipal principal) {
        if (!RoleGuard.hasRole(principal, "CHAIR") && !RoleGuard.hasRole(principal, "ADMIN")) {
            throw new ConferenceAccessException("Chair or Admin role is required");
        }
    }

    private ConferenceReviewerResponse toResponse(ConferenceReviewerMembership membership) {
        return new ConferenceReviewerResponse(
                membership.conferenceReviewerId(),
                membership.conferenceId(),
                membership.reviewerId(),
                membership.maxLoad(),
                membership.membershipStatus(),
                membership.researchAreas(),
                membership.joinedAt()
        );
    }
}
