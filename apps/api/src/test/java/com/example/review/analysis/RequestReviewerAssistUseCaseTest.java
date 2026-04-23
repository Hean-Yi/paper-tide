package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.application.RequestReviewerAssistUseCase;
import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.infrastructure.ReviewerAssistContextRepository;
import com.example.review.analysis.infrastructure.ReviewerAssistContextRepository.ReviewerAssistContext;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestReviewerAssistUseCaseTest {
    private static final CurrentUserPrincipal REVIEWER = new CurrentUserPrincipal(1002L, "reviewer_demo", List.of("REVIEWER"));

    private final ReviewerAssistContextRepository contextRepository = org.mockito.Mockito.mock(ReviewerAssistContextRepository.class);
    private final AnalysisIntentRepository intentRepository = org.mockito.Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisProjectionRepository projectionRepository = org.mockito.Mockito.mock(AnalysisProjectionRepository.class);
    private final AnalysisOutboxPublisher outboxPublisher = org.mockito.Mockito.mock(AnalysisOutboxPublisher.class);
    private final RequestReviewerAssistUseCase useCase = new RequestReviewerAssistUseCase(
            contextRepository,
            intentRepository,
            projectionRepository,
            outboxPublisher
    );

    @Test
    void forceRequestsUseNextIntentVersionInsteadOfReusingFixedForceKey() {
        when(contextRepository.findByAssignmentId(77L)).thenReturn(Optional.of(new ReviewerAssistContext(
                77L,
                8L,
                9L,
                10L,
                1002L,
                "ACCEPTED",
                "Durable Agent Design",
                "A paper about task boundaries.",
                "agent,design",
                128L
        )));
        when(intentRepository.nextRequestVersion(eq(AnalysisType.REVIEWER_ASSIST), eq(AnalysisBusinessAnchor.assignment(77L))))
                .thenReturn(2)
                .thenReturn(3);
        when(intentRepository.createOrReuseIntent(eq(AnalysisType.REVIEWER_ASSIST), any(), eq(1002L), any()))
                .thenReturn(101L)
                .thenReturn(102L);

        useCase.request(REVIEWER, 77L, true);
        useCase.request(REVIEWER, 77L, true);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(intentRepository, org.mockito.Mockito.times(2)).createOrReuseIntent(
                eq(AnalysisType.REVIEWER_ASSIST),
                eq(AnalysisBusinessAnchor.assignment(77L)),
                eq(1002L),
                keyCaptor.capture()
        );
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }
}
