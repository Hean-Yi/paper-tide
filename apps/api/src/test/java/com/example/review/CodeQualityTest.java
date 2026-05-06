package com.example.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CodeQualityTest {
    @Test
    void roleSpecificPrivateGuardsAreCentralizedInRoleGuard() throws IOException {
        assertSourceDoesNotContain(
                "src/main/java/com/example/review/manuscript/ManuscriptService.java",
                "private void requireAuthor"
        );
        assertSourceDoesNotContain(
                "src/main/java/com/example/review/review/ReviewWorkflowService.java",
                "private void requireReviewer"
        );
        assertSourceDoesNotContain(
                "src/main/java/com/example/review/review/ReviewReportService.java",
                "private void requireReviewer"
        );
    }

    @Test
    void manuscriptForUpdateQueryIsCentralizedInManuscriptRepository() throws IOException {
        assertSourceDoesNotContain(
                "src/main/java/com/example/review/decision/DecisionService.java",
                "private ManuscriptDecisionRow findManuscriptForUpdate"
        );
        assertSourceDoesNotContain(
                "src/main/java/com/example/review/review/ReviewWorkflowService.java",
                "private ManuscriptReviewRow findManuscriptForUpdate"
        );
    }

    @Test
    void legacyMirroredAgentTaskInfrastructureIsRemoved() {
        assertFalse(Files.exists(Path.of("src/main/java/com/example/review/agent/AgentPollingScheduler.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/example/review/agent/HttpAgentServiceClient.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/example/review/agent/AgentServiceClient.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/example/review/agent/AgentServiceException.java")));
    }

    @Test
    void migratedAnalysisTestsDoNotDependOnLegacyMirroredTaskArtifacts() throws IOException {
        assertSourceDoesNotContain(
                "src/test/java/com/example/review/analysis/AnalysisIntentFlowTest.java",
                "AGENT_ANALYSIS_TASK"
        );
        assertSourceDoesNotContain(
                "src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java",
                "AGENT_ANALYSIS_TASK"
        );
        assertSourceDoesNotContain(
                "src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java",
                "AGENT_ANALYSIS_TASK"
        );
        assertSourceDoesNotContain(
                "../web/src/tests/workflow.spec.ts",
                "/agent-results"
        );
    }

    @Test
    void legacyAgentTablesAreRetiredFromActiveSchemaAndTestCleanup() throws IOException {
        String[] activeSchemaFiles = {
                "../../database/oracle/001_init.sql",
                "../../database/oracle/003_indexes.sql",
                "../../database/oracle/004_procedures.sql",
                "../../database/oracle/005_triggers.sql",
                "../../database/oracle/007_seed_demo_workflow.sql",
                "../../database/oracle/verify_schema.sql"
        };
        String[] retiredObjects = {
                "AGENT_ANALYSIS_TASK",
                "AGENT_ANALYSIS_RESULT",
                "AGENT_FEEDBACK",
                "SEQ_AGENT_ANALYSIS_TASK",
                "SEQ_AGENT_ANALYSIS_RESULT",
                "SEQ_AGENT_FEEDBACK",
                "TRG_AGENT_ANALYSIS_TASK_BI",
                "TRG_AGENT_ANALYSIS_RESULT_BI",
                "TRG_AGENT_FEEDBACK_BI",
                "PRC_AGENT_TASK_STATUS_SUMMARY"
        };

        for (String path : activeSchemaFiles) {
            for (String retiredObject : retiredObjects) {
                assertSourceDoesNotContain(path, retiredObject);
            }
        }

        assertFalse(Files.exists(Path.of("src/test/java/com/example/review/support/LegacyAgentArtifactsCleanup.java")));

        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        assertTrue(applyScript.contains("011_retire_legacy_agent_tables.sql"));
    }

    @Test
    void databaseQueryHotspotsHaveSchemaIndexesAndVerificationCoverage() throws IOException {
        String indexes = Files.readString(Path.of("..", "..", "database", "oracle", "003_indexes.sql"))
                + Files.readString(Path.of("..", "..", "database", "oracle", "010_database_query_optimization.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));

        assertTrue(indexes.contains("IDX_REVIEW_ROUND_STATUS_ID"));
        assertTrue(indexes.contains("IDX_REVIEW_ASSIGNMENT_ROUND_ID"));
        assertTrue(indexes.contains("IDX_REVIEW_ASSIGNMENT_ACCESS"));
        assertTrue(indexes.contains("IDX_REVIEW_REPORT_ROUND"));
        assertTrue(indexes.contains("IDX_CONFLICT_CHECK_MANUSCRIPT_REVIEWER"));
        assertTrue(indexes.contains("IDX_MANUSCRIPT_STATUS_SUBMITTED"));
        assertTrue(indexes.contains("IDX_ANALYSIS_PROJECTION_UPDATED"));

        assertTrue(verification.contains("IDX_REVIEW_ROUND_STATUS_ID"));
        assertTrue(verification.contains("IDX_REVIEW_ASSIGNMENT_ROUND_ID"));
        assertTrue(verification.contains("IDX_REVIEW_REPORT_ROUND"));
        assertTrue(verification.contains("Expected 43 indexes"));

        assertTrue(applyScript.contains("009_execution_job_attempt_count.sql"));
        assertTrue(applyScript.contains("010_database_query_optimization.sql"));
    }

    @Test
    void decisionWorkbenchEndpointUsesBulkReadModel() throws IOException {
        String controller = Files.readString(Path.of("src/main/java/com/example/review/workflow/WorkflowQueryController.java"));
        String workflowService = Files.readString(Path.of("src/main/java/com/example/review/workflow/WorkflowQueryService.java"));

        assertTrue(controller.contains("DecisionWorkbenchQueryService"));
        assertFalse(workflowService.contains("private DecisionWorkbenchItem toDecisionWorkbenchItem"));
        assertFalse(workflowService.contains("private List<DecisionAssignmentItem> listAssignmentsForRound"));
    }

    @Test
    void conferenceCfpPublicReadsAndMigrationNumberAreExplicitlyWired() throws IOException {
        String security = Files.readString(Path.of("src/main/java/com/example/review/config/SecurityConfig.java"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(security.contains("HttpMethod.GET"));
        assertTrue(security.contains("/api/conferences/cfp"));
        assertTrue(security.contains("/api/conferences/cfp/**"));
        assertTrue(applyScript.contains("014_conference_cfp_lifecycle.sql"));
        assertTrue(devUp.contains("014_conference_cfp_lifecycle.sql"));
        assertFalse(applyScript.contains("010_conference_onboarding.sql"));
        assertFalse(devUp.contains("010_conference_onboarding.sql"));
    }

    private void assertSourceDoesNotContain(String path, String forbiddenText) throws IOException {
        String source = Files.readString(Path.of(path));
        assertFalse(source.contains(forbiddenText), path + " should not contain " + forbiddenText);
    }
}
