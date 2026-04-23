package com.example.review.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.auth.CurrentUserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class AdminAnalysisMonitorQueryTest {
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final AnalysisIntentRepository intentRepository = Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisProjectionRepository projectionRepository = Mockito.mock(AnalysisProjectionRepository.class);
    private final WorkflowQueryService service = new WorkflowQueryService(jdbcTemplate, intentRepository, projectionRepository);

    @Test
    void adminListsAnalysisMonitorRows() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                new AdminAnalysisMonitorItem(
                        101L,
                        "REVIEWER_ASSIST",
                        "AVAILABLE",
                        "job-1",
                        "ASSIGNMENT",
                        "Assignment #77",
                        "Checklist ready.",
                        Timestamp.from(Instant.parse("2026-04-23T08:00:00Z"))
                )
        ));

        List<AdminAnalysisMonitorItem> rows = service.listAdminAnalysisMonitor(
                new CurrentUserPrincipal(1004L, "admin_demo", List.of("ADMIN"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().intentId()).isEqualTo(101L);
        assertThat(rows.getFirst().jobId()).isEqualTo("job-1");
        assertThat(rows.getFirst().anchorLabel()).isEqualTo("Assignment #77");
        assertThat(rows.getFirst().summaryText()).isEqualTo("Checklist ready.");
    }

    @Test
    void nonAdminCannotListAnalysisMonitorRows() {
        assertThatThrownBy(() -> service.listAdminAnalysisMonitor(
                new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ADMIN role is required");
    }
}
