package com.example.review.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReviewReportPolicyTest {
    private static final CurrentUserPrincipal REVIEWER = new CurrentUserPrincipal(1002L, "reviewer", List.of("REVIEWER"));

    private final ReviewAssignmentRepository reviewAssignmentRepository = org.mockito.Mockito.mock(ReviewAssignmentRepository.class);
    private final ReviewRoundRepository reviewRoundRepository = org.mockito.Mockito.mock(ReviewRoundRepository.class);
    private final ReviewReportRepository reviewReportRepository = org.mockito.Mockito.mock(ReviewReportRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final ReviewReportService service = new ReviewReportService(
            reviewAssignmentRepository,
            reviewRoundRepository,
            reviewReportRepository,
            notificationService
    );

    @Test
    void submitRejectsAfterAssignmentDeadline() {
        ReviewAssignmentRow assignment = new ReviewAssignmentRow(
                501L,
                301L,
                201L,
                101L,
                1002L,
                "ACCEPTED",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")),
                null,
                null,
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                null,
                null
        );
        when(reviewAssignmentRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submit(REVIEWER, 501L, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(reviewReportRepository, never()).insert(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void submitRejectsAfterRoundDeadlineWhenAssignmentHasNoDeadline() {
        ReviewAssignmentRow assignment = new ReviewAssignmentRow(
                501L,
                301L,
                201L,
                101L,
                1002L,
                "ACCEPTED",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")),
                null,
                null,
                null,
                null,
                null
        );
        when(reviewAssignmentRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(assignment));
        when(reviewRoundRepository.findById(301L)).thenReturn(Optional.of(new ReviewRoundRow(
                301L,
                201L,
                1,
                101L,
                "IN_PROGRESS",
                "REALLOCATE_REVIEWERS",
                true,
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                1003L
        )));

        assertThatThrownBy(() -> service.submit(REVIEWER, 501L, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private SubmitReviewReportRequest validRequest() {
        return new SubmitReviewReportRequest(
                4,
                4,
                4,
                4,
                4,
                "HIGH",
                "clear contribution",
                "needs more ablation",
                "please clarify evaluation",
                "acceptable after minor revision",
                "MINOR_REVISION"
        );
    }
}
