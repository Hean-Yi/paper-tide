package com.example.review.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.review.analysis.infrastructure.AnalysisIntentRepository;
import com.example.review.analysis.infrastructure.AnalysisProjectionRepository;
import com.example.review.auth.CurrentUserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class AdminAnalysisMonitorQueryTest {
    private final ReviewerAssignmentReadRepository reviewerAssignmentReadRepository = Mockito.mock(ReviewerAssignmentReadRepository.class);
    private final ScreeningQueueReadRepository screeningQueueReadRepository = Mockito.mock(ScreeningQueueReadRepository.class);
    private final NamedParameterJdbcTemplate namedJdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
    private final AnalysisIntentRepository intentRepository = Mockito.mock(AnalysisIntentRepository.class);
    private final AnalysisProjectionRepository projectionRepository = Mockito.mock(AnalysisProjectionRepository.class);
    private final AdminAnalysisMonitorReadRepository repository = new AdminAnalysisMonitorReadRepository(namedJdbcTemplate);
    private final WorkflowQueryService service = new WorkflowQueryService(
            reviewerAssignmentReadRepository,
            screeningQueueReadRepository,
            repository,
            intentRepository,
            projectionRepository
    );

    @Test
    void adminListsAnalysisMonitorRows() {
        when(namedJdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(AdminAnalysisMonitorReadRepository.AdminMonitorItemRowMapper.class))).thenReturn(List.of(
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
        when(namedJdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), any(Class.class))).thenReturn(1L);

        AdminAnalysisMonitorPage page = service.listAdminAnalysisMonitor(
                new CurrentUserPrincipal(1004L, "admin_demo", List.of("ADMIN")),
                new AdminAnalysisMonitorRequest(1, 20, "REVIEWER_ASSIST", "AVAILABLE")
        );

        List<AdminAnalysisMonitorItem> rows = page.items();
        assertThat(rows).hasSize(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(rows.getFirst().intentId()).isEqualTo(101L);
        assertThat(rows.getFirst().jobId()).isEqualTo("job-1");
        assertThat(rows.getFirst().anchorLabel()).isEqualTo("Assignment #77");
        assertThat(rows.getFirst().summaryText()).isEqualTo("Checklist ready.");
        verify(namedJdbcTemplate).query(
                eq("""
                SELECT I.INTENT_ID,
                       I.ANALYSIS_TYPE,
                       I.BUSINESS_STATUS,
                       I.EXECUTION_JOB_ID,
                       I.BUSINESS_ANCHOR_TYPE,
                       CASE
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ASSIGNMENT' THEN 'Assignment #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ROUND' THEN 'Round #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'MANUSCRIPT_VERSION' THEN 'Manuscript #' || I.BUSINESS_ANCHOR_ID || ' / Version #' || I.BUSINESS_ANCHOR_VERSION_ID
                         ELSE 'Anchor #' || I.BUSINESS_ANCHOR_ID
                       END AS ANCHOR_LABEL,
                       P.SUMMARY_TEXT,
                       P.UPDATED_AT AS PROJECTION_UPDATED_AT
                FROM ANALYSIS_INTENT I
                LEFT JOIN ANALYSIS_PROJECTION P ON P.INTENT_ID = I.INTENT_ID
                WHERE I.ANALYSIS_TYPE = :analysisType AND I.BUSINESS_STATUS = :businessStatus
                ORDER BY I.INTENT_ID DESC
                OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                """),
                org.mockito.ArgumentMatchers.<MapSqlParameterSource>argThat(params ->
                        params.getValue("offset").equals(0)
                                && params.getValue("limit").equals(20)
                                && params.getValue("analysisType").equals("REVIEWER_ASSIST")
                                && params.getValue("businessStatus").equals("AVAILABLE")
                ),
                any(AdminAnalysisMonitorReadRepository.AdminMonitorItemRowMapper.class)
        );
    }

    @Test
    void nonAdminCannotListAnalysisMonitorRows() {
        assertThatThrownBy(() -> service.listAdminAnalysisMonitor(
                new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR")),
                new AdminAnalysisMonitorRequest(1, 20, null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ADMIN role is required");
    }
}
