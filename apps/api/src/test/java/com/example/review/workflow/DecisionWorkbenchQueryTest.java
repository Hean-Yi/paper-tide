package com.example.review.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisProjectionResponse;
import com.example.review.auth.CurrentUserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionWorkbenchQueryTest {

    @Test
    void listDecisionWorkbench_assemblesBulkDataCorrectly() {
        // Arrange
        DecisionWorkbenchReadRepository repository = mock(DecisionWorkbenchReadRepository.class);
        DecisionWorkbenchQueryService service = new DecisionWorkbenchQueryService(repository);
        
        CurrentUserPrincipal chair = new CurrentUserPrincipal(1L, "chair", List.of("CHAIR"));

        Timestamp now = Timestamp.from(Instant.now());
        
        DecisionWorkbenchBase round1 = new DecisionWorkbenchBase(
                101L, 1L, 11L, 1, "IN_PROGRESS", now, "UNDER_REVIEW", null, 
                "Paper 1", 1, 2, 1, 0);
                
        DecisionWorkbenchBase round2 = new DecisionWorkbenchBase(
                102L, 2L, 22L, 1, "PENDING", now, "SUBMITTED", null, 
                "Paper 2", 1, 0, 0, 0);

        when(repository.findPendingAndInProgressRounds(1L)).thenReturn(List.of(round1, round2));
        
        DecisionAssignmentItem assignment = new DecisionAssignmentItem(
                501L, 901L, "SUBMITTED", now, now, now, now, null);
                
        when(repository.findAssignmentsByRoundIds(List.of(101L, 102L)))
                .thenReturn(Map.of(101L, List.of(assignment)));
                
        AnalysisIntentResponse intent = new AnalysisIntentResponse(
                801L, AnalysisType.CONFLICT_ANALYSIS.name(), "AVAILABLE");
                
        when(repository.findConflictIntentsByRoundIds(List.of(101L, 102L)))
                .thenReturn(Map.of(101L, intent));
                
        AnalysisProjectionResponse projection = new AnalysisProjectionResponse(
                701L, AnalysisType.CONFLICT_ANALYSIS.name(), "AVAILABLE", "Sum", null, false, Instant.now());
                
        when(repository.findConflictProjectionsByRoundIds(List.of(101L, 102L)))
                .thenReturn(Map.of(101L, List.of(projection)));

        // Act
        List<DecisionWorkbenchItem> result = service.listDecisionWorkbench(chair);

        // Assert
        assertThat(result).hasSize(2);
        
        DecisionWorkbenchItem item1 = result.get(0);
        assertThat(item1.roundId()).isEqualTo(101L);
        assertThat(item1.assignments()).hasSize(1);
        assertThat(item1.assignments().get(0).assignmentId()).isEqualTo(501L);
        assertThat(item1.conflictIntent()).isNotNull();
        assertThat(item1.conflictIntent().intentId()).isEqualTo(801L);
        assertThat(item1.conflictProjections()).hasSize(1);
        
        DecisionWorkbenchItem item2 = result.get(1);
        assertThat(item2.roundId()).isEqualTo(102L);
        assertThat(item2.assignments()).isEmpty();
        assertThat(item2.conflictIntent()).isNull();
        assertThat(item2.conflictProjections()).isEmpty();
    }

    @Test
    void listDecisionWorkbench_filtersRegularChairByOrganizerScope() {
        DecisionWorkbenchReadRepository repository = mock(DecisionWorkbenchReadRepository.class);
        DecisionWorkbenchQueryService service = new DecisionWorkbenchQueryService(repository);
        CurrentUserPrincipal chair = new CurrentUserPrincipal(2003L, "new_chair", List.of("CHAIR"));

        when(repository.findPendingAndInProgressRounds(2003L)).thenReturn(List.of());

        List<DecisionWorkbenchItem> result = service.listDecisionWorkbench(chair);

        assertThat(result).isEmpty();
        verify(repository).findPendingAndInProgressRounds(2003L);
    }

    @Test
    void listDecisionWorkbench_allowsAdminGlobalScope() {
        DecisionWorkbenchReadRepository repository = mock(DecisionWorkbenchReadRepository.class);
        DecisionWorkbenchQueryService service = new DecisionWorkbenchQueryService(repository);
        CurrentUserPrincipal admin = new CurrentUserPrincipal(1004L, "admin", List.of("ADMIN"));

        when(repository.findPendingAndInProgressRounds(null)).thenReturn(List.of());

        List<DecisionWorkbenchItem> result = service.listDecisionWorkbench(admin);

        assertThat(result).isEmpty();
        verify(repository).findPendingAndInProgressRounds(null);
    }
}
