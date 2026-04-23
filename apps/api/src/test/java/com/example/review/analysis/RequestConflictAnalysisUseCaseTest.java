package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.application.RequestConflictAnalysisUseCase;
import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.ConflictAnalysisContextRepository;
import com.example.review.analysis.infrastructure.ConflictAnalysisContextRepository.ConflictAnalysisContext;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestConflictAnalysisUseCaseTest {
    private static final CurrentUserPrincipal CHAIR = new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"));

    private final ConflictAnalysisContextRepository contextRepository = org.mockito.Mockito.mock(ConflictAnalysisContextRepository.class);
    private final AnalysisIntentRepository intentRepository = org.mockito.Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisOutboxPublisher outboxPublisher = org.mockito.Mockito.mock(AnalysisOutboxPublisher.class);
    private final RequestConflictAnalysisUseCase useCase = new RequestConflictAnalysisUseCase(
            contextRepository,
            intentRepository,
            outboxPublisher
    );

    @Test
    void requestCreatesConflictIntentAndOutboxMessageWithoutLegacyTaskSubmission() {
        when(contextRepository.findByRoundId(31L)).thenReturn(Optional.of(new ConflictAnalysisContext(
                31L,
                11L,
                21L,
                "Conflict Seed",
                "A paper with mixed reviews.",
                "workflow,conflict",
                512L,
                List.of(Map.of(
                        "reviewId", 501L,
                        "recommendation", "MINOR_REVISION",
                        "commentsToChair", "Borderline but promising."
                ))
        )));
        when(intentRepository.createOrReuseIntent(eq(AnalysisType.CONFLICT_ANALYSIS), any(), eq(1003L), any()))
                .thenReturn(901L);

        var response = useCase.request(CHAIR, 31L, false);

        assertThat(response.analysisType()).isEqualTo("CONFLICT_ANALYSIS");
        assertThat(response.businessStatus()).isEqualTo("REQUESTED");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(intentRepository).createOrReuseIntent(
                eq(AnalysisType.CONFLICT_ANALYSIS),
                eq(AnalysisBusinessAnchor.round(31L)),
                eq(1003L),
                any()
        );
        verify(outboxPublisher).publishRequested(
                eq(901L),
                eq(AnalysisType.CONFLICT_ANALYSIS),
                any(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).containsEntry("title", "Conflict Seed");
        assertThat(payloadCaptor.getValue()).containsKey("reviewReports");
        assertThat(payloadCaptor.getValue().get("reviewReports").toString()).contains("MINOR_REVISION");
        assertThat(payloadCaptor.getValue()).containsKey("conflictAnalysis");
    }
}
