package com.example.review.conference;

import java.util.List;
import java.util.Optional;

interface ConferenceReviewerRepository {
    boolean isActivePlatformReviewer(long reviewerId);

    List<PlatformReviewerSearchResult> searchActivePlatformReviewers(String query, int limit);

    List<ResearchAreaSnapshot> listUserResearchAreas(long userId);

    void grantReviewerRole(long userId);

    ConferenceReviewerMembership upsertConferenceReviewer(
            long conferenceId,
            long reviewerId,
            int maxLoad,
            long invitedBy,
            List<ResearchAreaSnapshot> researchAreas
    );

    Optional<ConferenceReviewerMembership> findActiveMembership(long conferenceId, long reviewerId);

    Optional<BiddingConferencePolicy> findBiddingPolicy(long conferenceId);

    boolean manuscriptBelongsToConference(long conferenceId, long manuscriptId);

    List<ReviewerBiddingItem> listOpenBiddingItems(long conferenceId, long reviewerId);

    ReviewerBidResponse saveBid(ReviewerBidDraft draft);

    boolean conflictExists(long manuscriptId, long reviewerId, String conflictType);

    void insertSelfDeclaredConflict(long manuscriptId, long reviewerId, String conflictType, String conflictDescription);
}
