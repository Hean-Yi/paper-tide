package com.example.review.conference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface ConferenceRepository {
    long createConference(ConferenceDraft draft);

    void createPhase(long conferenceId, ConferencePhaseDraft draft);

    Optional<ConferenceDetail> findDetail(long conferenceId);

    Optional<ConferenceDetail> findPublicCfpBySlug(String publicSlug);

    List<ConferenceSummary> listPublicCfps();

    List<ConferenceSummary> listPendingApproval();

    List<ConferenceSummary> listManageable(Long organizerUserId);

    boolean publicSlugExists(String publicSlug);

    void updateStatus(long conferenceId, String status, boolean cfpPublished, Long approvedBy, Instant approvedAt);
}
