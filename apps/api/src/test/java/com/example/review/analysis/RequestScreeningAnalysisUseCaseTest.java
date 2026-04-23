package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.application.RequestScreeningAnalysisUseCase;
import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisOutboxPublisher;
import com.example.review.analysis.infrastructure.ScreeningAnalysisContextRepository;
import com.example.review.analysis.infrastructure.ScreeningAnalysisContextRepository.ScreeningAnalysisContext;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestScreeningAnalysisUseCaseTest {
    private static final CurrentUserPrincipal CHAIR = new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"));

    private final ScreeningAnalysisContextRepository contextRepository =
            org.mockito.Mockito.mock(ScreeningAnalysisContextRepository.class);
    private final AnalysisIntentRepository intentRepository = org.mockito.Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisOutboxPublisher outboxPublisher = org.mockito.Mockito.mock(AnalysisOutboxPublisher.class);
    private final RequestScreeningAnalysisUseCase useCase = new RequestScreeningAnalysisUseCase(
            contextRepository,
            intentRepository,
            outboxPublisher
    );

    @Test
    void requestCreatesScreeningIntentAndOutboxMessageWithoutLegacyTaskSubmission() {
        when(contextRepository.findByManuscriptVersion(11L, 21L)).thenReturn(Optional.of(new ScreeningAnalysisContext(
                11L,
                21L,
                "Screening Seed",
                "A paper ready for chair screening.",
                "workflow,screening",
                512L
        )));
        when(intentRepository.createOrReuseIntent(eq(AnalysisType.SCREENING), any(), eq(1003L), any()))
                .thenReturn(902L);

        var response = useCase.request(CHAIR, 11L, 21L, false);

        assertThat(response.analysisType()).isEqualTo("SCREENING");
        assertThat(response.businessStatus()).isEqualTo("REQUESTED");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(intentRepository).createOrReuseIntent(
                eq(AnalysisType.SCREENING),
                eq(AnalysisBusinessAnchor.screening(11L, 21L)),
                eq(1003L),
                any()
        );
        verify(outboxPublisher).publishRequested(
                eq(902L),
                eq(AnalysisType.SCREENING),
                any(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).containsEntry("title", "Screening Seed");
        assertThat(payloadCaptor.getValue()).containsEntry("keywords", List.of("workflow", "screening"));
        assertThat(payloadCaptor.getValue()).containsKey("screening");
        assertThat(payloadCaptor.getValue().get("screening").toString()).contains("manuscriptId=11");
    }
}
