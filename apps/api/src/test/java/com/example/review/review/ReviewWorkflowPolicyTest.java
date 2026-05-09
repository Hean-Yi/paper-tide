package com.example.review.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.manuscript.ManuscriptRepository;
import com.example.review.manuscript.ManuscriptRepository.LockedManuscriptRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReviewWorkflowPolicyTest {
    private static final CurrentUserPrincipal CHAIR = new CurrentUserPrincipal(1003L, "chair", List.of("CHAIR"));
    private static final CurrentUserPrincipal OTHER_CHAIR = new CurrentUserPrincipal(1013L, "other_chair", List.of("CHAIR"));
    private static final CurrentUserPrincipal ADMIN = new CurrentUserPrincipal(1004L, "admin", List.of("ADMIN"));

    private final ManuscriptRepository manuscriptRepository = org.mockito.Mockito.mock(ManuscriptRepository.class);
    private final ReviewRoundRepository reviewRoundRepository = org.mockito.Mockito.mock(ReviewRoundRepository.class);
    private final ReviewAssignmentRepository reviewAssignmentRepository = org.mockito.Mockito.mock(ReviewAssignmentRepository.class);
    private final AssignmentDraftRepository assignmentDraftRepository = org.mockito.Mockito.mock(AssignmentDraftRepository.class);
    private final ConflictCheckService conflictCheckService = org.mockito.Mockito.mock(ConflictCheckService.class);
    private final ReviewWorkflowService service = new ReviewWorkflowService(
            manuscriptRepository,
            reviewRoundRepository,
            reviewAssignmentRepository,
            assignmentDraftRepository,
            conflictCheckService
    );

    @Test
    void createRoundRejectsChairWhoDoesNotOrganizeManuscriptConference() {
        CreateReviewRoundRequest request = new CreateReviewRoundRequest(
                10L,
                20L,
                "REALLOCATE_REVIEWERS",
                true,
                Instant.parse("2026-08-01T00:00:00Z")
        );
        when(manuscriptRepository.findLockedById(10L))
                .thenReturn(Optional.of(new LockedManuscriptRow(10L, 1001L, 20L, "SUBMITTED", 0, 77L, 1003L)));

        assertThatThrownBy(() -> service.createRound(OTHER_CHAIR, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRoundRepository, never()).insert(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void adminCanCreateRoundAcrossConferenceScopes() {
        CreateReviewRoundRequest request = new CreateReviewRoundRequest(
                10L,
                20L,
                "REALLOCATE_REVIEWERS",
                true,
                Instant.parse("2026-08-01T00:00:00Z")
        );
        when(manuscriptRepository.findLockedById(10L))
                .thenReturn(Optional.of(new LockedManuscriptRow(10L, 1001L, 20L, "SUBMITTED", 0, 77L, 1003L)));
        when(reviewRoundRepository.nextRoundId()).thenReturn(30L);
        when(reviewRoundRepository.nextRoundNo(10L)).thenReturn(1);
        when(reviewRoundRepository.findById(30L)).thenReturn(Optional.of(new ReviewRoundRow(
                30L,
                10L,
                1,
                20L,
                "PENDING",
                "REALLOCATE_REVIEWERS",
                true,
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")),
                1004L
        )));

        ReviewRoundResponse response = service.createRound(ADMIN, request);

        org.assertj.core.api.Assertions.assertThat(response.roundId()).isEqualTo(30L);
    }

    @Test
    void directAssignmentRejectsReviewerWhoIsNotEligibleForTheConferencePaper() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                1002L,
                Instant.parse("2026-08-01T00:00:00Z")
        );
        when(reviewRoundRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(new ReviewRoundRow(
                30L,
                10L,
                1,
                20L,
                "PENDING",
                "REALLOCATE_REVIEWERS",
                true,
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")),
                1003L
        )));
        when(manuscriptRepository.findLockedById(10L))
                .thenReturn(Optional.of(new LockedManuscriptRow(10L, 1001L, 20L, "UNDER_REVIEW", 1, 77L, 1003L)));
        when(reviewAssignmentRepository.findEligibilityForAssignment(10L, 1002L))
                .thenReturn(Optional.of(new AssignmentEligibilityRow(
                        false,
                        false,
                        true,
                        true,
                        2,
                        2
                )));

        assertThatThrownBy(() -> service.assignReviewer(CHAIR, 30L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(reviewAssignmentRepository, never()).insert(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
