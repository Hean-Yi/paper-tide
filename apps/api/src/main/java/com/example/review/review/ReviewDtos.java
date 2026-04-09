package com.example.review.review;

import java.sql.Timestamp;
import java.time.Instant;

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
