package com.example.review.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.review.auth.CurrentUserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DecisionPackagePolicyTest {
    private static final CurrentUserPrincipal AUTHOR = new CurrentUserPrincipal(1001L, "author", List.of("AUTHOR"));
    private static final CurrentUserPrincipal OTHER_AUTHOR = new CurrentUserPrincipal(1011L, "other_author", List.of("AUTHOR"));

    private final DecisionRepository decisionRepository = org.mockito.Mockito.mock(DecisionRepository.class);
    private final DecisionPackageService service = new DecisionPackageService(decisionRepository);

    @Test
    void authorDecisionPackageReturnsDecisionAndAnonymousAuthorVisibleReviewsOnly() {
        when(decisionRepository.findAuthorDecisionPackage(1001L, 200L)).thenReturn(Optional.of(new DecisionPackageRow(
                200L,
                300L,
                400L,
                1,
                "Paper Title",
                "ACCEPT",
                "accepted with minor edits",
                Timestamp.from(Instant.parse("2026-09-01T00:00:00Z"))
        )));
        when(decisionRepository.findAuthorVisibleReviews(300L)).thenReturn(List.of(
                new DecisionPackageReviewRow(
                        901L,
                        1002L,
                        4,
                        "HIGH",
                        "strong method",
                        "small dataset",
                        "please clarify dataset collection",
                        "accept",
                        "ACCEPT"
                )
        ));

        DecisionPackageResponse response = service.getAuthorDecisionPackage(AUTHOR, 200L);

        assertThat(response.decisionCode()).isEqualTo("ACCEPT");
        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().getFirst().reviewerLabel()).isEqualTo("Reviewer 1");
        assertThat(response.reviews().getFirst().commentsToAuthor()).isEqualTo("please clarify dataset collection");
    }

    @Test
    void authorDecisionPackageRejectsNonOwner() {
        assertThatThrownBy(() -> service.getAuthorDecisionPackage(OTHER_AUTHOR, 200L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
