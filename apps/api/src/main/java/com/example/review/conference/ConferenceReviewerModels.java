package com.example.review.conference;

import java.time.Instant;
import java.util.List;

record ConferenceReviewerRequest(
        Long reviewerId,
        Integer maxLoad
) {
}

record ReviewerBidRequest(
        Long manuscriptId,
        String bidValue,
        Boolean conflictDeclared,
        String conflictType,
        String conflictDescription
) {
}

record ResearchAreaSnapshot(
        String areaCode,
        String areaName
) {
}

record ConferenceReviewerResponse(
        long conferenceReviewerId,
        long conferenceId,
        long reviewerId,
        int maxLoad,
        String membershipStatus,
        List<ResearchAreaSnapshot> researchAreas,
        Instant joinedAt
) {
}

record PlatformReviewerSearchResult(
        long reviewerId,
        String realName,
        String email,
        String institution,
        List<ResearchAreaSnapshot> researchAreas
) {
}

record ReviewerBiddingItem(
        long manuscriptId,
        long versionId,
        String title,
        String abstractText,
        String keywords,
        String bidValue,
        boolean conflictDeclared
) {
}

record ReviewerBidResponse(
        long bidId,
        long conferenceId,
        long manuscriptId,
        long reviewerId,
        String bidValue,
        boolean conflictDeclared,
        Instant bidAt
) {
}

record ConferenceReviewerMembership(
        long conferenceReviewerId,
        long conferenceId,
        long reviewerId,
        int maxLoad,
        String membershipStatus,
        List<ResearchAreaSnapshot> researchAreas,
        Instant joinedAt
) {
}

record ReviewerBidDraft(
        long conferenceId,
        long manuscriptId,
        long reviewerId,
        String bidValue,
        boolean conflictDeclared,
        String conflictType,
        String conflictDescription
) {
}

record BiddingConferencePolicy(
        long conferenceId,
        long organizerUserId,
        String status,
        Instant biddingCloseAt
) {
}
