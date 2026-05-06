package com.example.review.conference;

import java.time.Instant;
import java.util.List;

record ConferenceDraftRequest(
        String name,
        String acronym,
        Integer year,
        String blindMode,
        String cfpText,
        List<String> topicAreas,
        Integer targetReviewsPerPaper,
        Integer defaultReviewerMaxLoad,
        String publicSlug,
        ConferencePhaseRequest phase
) {
}

record ConferencePhaseRequest(
        Instant submissionOpenAt,
        Instant submissionCloseAt,
        Instant biddingOpenAt,
        Instant biddingCloseAt,
        Instant reviewDeadlineAt,
        Instant decisionReleaseAt
) {
}

record ConferenceStatusTransitionRequest(String status) {
}

record ConferenceDraft(
        String name,
        String acronym,
        int year,
        long organizerUserId,
        String status,
        String blindMode,
        String cfpText,
        List<String> topicAreas,
        int targetReviewsPerPaper,
        int defaultReviewerMaxLoad,
        String publicSlug,
        boolean cfpPublished
) {
}

record ConferencePhaseDraft(
        Instant submissionOpenAt,
        Instant submissionCloseAt,
        Instant biddingOpenAt,
        Instant biddingCloseAt,
        Instant reviewDeadlineAt,
        Instant decisionReleaseAt
) {
}

record ConferencePhase(
        long phaseId,
        long conferenceId,
        Instant submissionOpenAt,
        Instant submissionCloseAt,
        Instant biddingOpenAt,
        Instant biddingCloseAt,
        Instant reviewDeadlineAt,
        Instant decisionReleaseAt
) {
}

record ConferenceDetail(
        long conferenceId,
        String name,
        String acronym,
        int year,
        long organizerUserId,
        String status,
        String blindMode,
        String cfpText,
        List<String> topicAreas,
        int targetReviewsPerPaper,
        int defaultReviewerMaxLoad,
        String publicSlug,
        boolean cfpPublished,
        Long approvedBy,
        Instant approvedAt,
        ConferencePhase phase
) {
}

record ConferenceSummary(
        long conferenceId,
        String name,
        String acronym,
        int year,
        String status,
        String blindMode,
        String publicSlug,
        Instant submissionOpenAt,
        Instant submissionCloseAt
) {
}
