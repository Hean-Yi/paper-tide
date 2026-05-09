package com.example.review.review;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

record CreateReviewRoundRequest(
        long manuscriptId,
        long versionId,
        String assignmentStrategy,
        boolean screeningRequired,
        Instant deadlineAt
) {
}

record ReviewRoundResponse(
        long roundId,
        int roundNo,
        String roundStatus,
        long manuscriptId,
        long versionId,
        String assignmentStrategy,
        boolean screeningRequired,
        Timestamp deadlineAt
) {
}

record CreateAssignmentRequest(
        long reviewerId,
        Instant deadlineAt
) {
}

record AssignmentActionResponse(
        long assignmentId,
        String taskStatus,
        long reviewerId,
        Long reassignedFromId
) {
}

record AssignmentCandidateResponse(
        long reviewerId,
        String reviewerName,
        String institution,
        int currentLoad,
        int maxLoad,
        String bidValue,
        int score,
        String reason
) {
}

record AutoAssignRequest(
        Integer reviewsPerPaper,
        Instant deadlineAt
) {
}

record RandomAssignmentPreviewRequest(
        Integer reviewsPerPaper,
        Instant deadlineAt
) {
}

record AutoAssignResponse(
        long conferenceId,
        int requestedReviewsPerPaper,
        int createdCount,
        List<AssignmentActionResponse> assignments
) {
}

record RandomAssignmentPreviewResponse(
        long conferenceId,
        int requestedReviewsPerPaper,
        int createdDraftCount,
        List<AssignmentDraftResponse> drafts
) {
}

record ConfirmAssignmentPreviewRequest(
        Instant deadlineAt
) {
}

record ConfirmAssignmentPreviewResponse(
        long conferenceId,
        int createdCount,
        List<AssignmentActionResponse> assignments
) {
}

record DeclineAssignmentRequest(
        String reason,
        boolean conflictDeclared
) {
}

record ConflictCheckResponse(
        long conflictId,
        long assignmentId,
        long reviewerId,
        String conflictType,
        String conflictDesc,
        String source
) {
}

record GenerateAssignmentDraftRequest(Integer limit) {
}

record ConfirmAssignmentDraftRequest(
        List<Long> draftIds,
        Instant deadlineAt
) {
}

record AssignmentDraftResponse(
        long draftId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int rankOrder,
        int score,
        int currentLoad,
        int maxLoad,
        String bidValue,
        String reason,
        String draftStatus
) {
}
