package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.application.RequestReviewerAssignmentAssistUseCase;
import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.analysis.infrastructure.ReviewerAssignmentAssistContextRepository;
import com.example.review.analysis.infrastructure.ReviewerAssignmentAssistContextRepository.AssignmentAssistContext;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestReviewerAssignmentAssistUseCaseTest {
    private static final CurrentUserPrincipal CHAIR = new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"));

    private final ReviewerAssignmentAssistContextRepository contextRepository = org.mockito.Mockito.mock(ReviewerAssignmentAssistContextRepository.class);
    private final AnalysisIntentRepository intentRepository = org.mockito.Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisProjectionRepository projectionRepository = org.mockito.Mockito.mock(AnalysisProjectionRepository.class);
    private final AnalysisOutboxPublisher outboxPublisher = org.mockito.Mockito.mock(AnalysisOutboxPublisher.class);
    private final RequestReviewerAssignmentAssistUseCase useCase = new RequestReviewerAssignmentAssistUseCase(
            contextRepository,
            intentRepository,
            projectionRepository,
            outboxPublisher
    );

    @Test
    void requestCreatesReviewerAssignmentAssistIntentAnchoredToManuscript() {
        when(contextRepository.findByRoundId(31L)).thenReturn(Optional.of(new AssignmentAssistContext(
                31L,
                11L,
                21L,
                "Assignment Seed",
                "A paper needing reviewers.",
                "nlp,systems",
                List.of(Map.of(
                        "draftId", 501L,
                        "reviewerId", 1002L,
                        "rankOrder", 1,
                        "score", 102,
                        "bidValue", "WANT_TO_REVIEW"
                ))
        )));
        when(intentRepository.createOrReuseIntent(eq(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST), any(), eq(1003L), any()))
                .thenReturn(901L);

        var response = useCase.request(CHAIR, 31L, false);

        assertThat(response.analysisType()).isEqualTo("REVIEWER_ASSIGNMENT_ASSIST");
        assertThat(response.businessStatus()).isEqualTo("REQUESTED");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(intentRepository).createOrReuseIntent(
                eq(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST),
                eq(AnalysisBusinessAnchor.manuscript(11L)),
                eq(1003L),
                any()
        );
        verify(outboxPublisher).publishRequested(
                eq(901L),
                eq(AnalysisType.REVIEWER_ASSIGNMENT_ASSIST),
                any(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).containsEntry("title", "Assignment Seed");
        assertThat(payloadCaptor.getValue()).containsKey("assignmentAssist");
        assertThat(payloadCaptor.getValue().get("candidateDrafts").toString()).contains("WANT_TO_REVIEW");
    }
}
