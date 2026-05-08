package com.example.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertTrue(verification.contains("Expected 83 indexes"));

        assertTrue(applyScript.contains("009_execution_job_attempt_count.sql"));
        assertTrue(applyScript.contains("010_database_query_optimization.sql"));
    }

    @Test
    void businessOperationsClosureMigrationIsWiredAndVerified() throws IOException {
        Path migrationPath = Path.of("..", "..", "database", "oracle", "020_business_operations_closure.sql");
        assertTrue(Files.exists(migrationPath));

        String migration = Files.readString(migrationPath);
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        for (String tableName : businessOperationsTables()) {
            assertTrue(migration.contains("CREATE TABLE " + tableName), "020 migration should create " + tableName);
            assertTrue(verification.contains(tableName), "verify_schema should verify " + tableName);
            assertTrue(devUp.contains(tableName), "dev-up should detect " + tableName);
        }
        for (String indexName : businessOperationsIndexes()) {
            assertTrue(migration.contains(indexName), "020 migration should create " + indexName);
            assertTrue(verification.contains(indexName), "verify_schema should verify " + indexName);
        }
        assertTrue(applyScript.contains("020_business_operations_closure.sql"));
        assertTrue(devUp.contains("020_business_operations_closure.sql"));
        assertTrue(verification.contains("Expected business operations closure tables to exist"));
    }

    @Test
    void realPlatformWaveSixSevenMigrationIsWiredAndVerified() throws IOException {
        Path migrationPath = Path.of("..", "..", "database", "oracle", "021_real_platform_wave6_wave7.sql");
        assertTrue(Files.exists(migrationPath));

        String migration = Files.readString(migrationPath);
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        for (String tableName : realPlatformWaveSixSevenTables()) {
            assertTrue(migration.contains("CREATE TABLE " + tableName), "021 migration should create " + tableName);
            assertTrue(verification.contains(tableName), "verify_schema should verify " + tableName);
            assertTrue(devUp.contains(tableName), "dev-up should detect " + tableName);
        }
        for (String indexName : realPlatformWaveSixSevenIndexes()) {
            assertTrue(migration.contains(indexName), "021 migration should create " + indexName);
            assertTrue(verification.contains(indexName), "verify_schema should verify " + indexName);
        }
        assertTrue(applyScript.contains("021_real_platform_wave6_wave7.sql"));
        assertTrue(devUp.contains("021_real_platform_wave6_wave7.sql"));
        assertTrue(verification.contains("Expected 56 tables"));
        assertTrue(verification.contains("Expected 55 sequences"));
        assertTrue(verification.contains("Expected 58 triggers"));
        assertTrue(verification.contains("Expected 83 indexes"));
    }

    @Test
    void realPlatformWaveEightMigrationIsWiredAndVerified() throws IOException {
        Path migrationPath = Path.of("..", "..", "database", "oracle", "022_assignment_coi_maturity.sql");
        assertTrue(Files.exists(migrationPath));

        String migration = Files.readString(migrationPath);
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        for (String tableName : realPlatformWaveEightTables()) {
            assertTrue(migration.contains("CREATE TABLE " + tableName), "022 migration should create " + tableName);
            assertTrue(verification.contains(tableName), "verify_schema should verify " + tableName);
            assertTrue(devUp.contains(tableName), "dev-up should detect " + tableName);
        }
        for (String indexName : realPlatformWaveEightIndexes()) {
            assertTrue(migration.contains(indexName), "022 migration should create " + indexName);
            assertTrue(verification.contains(indexName), "verify_schema should verify " + indexName);
        }
        assertTrue(applyScript.contains("022_assignment_coi_maturity.sql"));
        assertTrue(devUp.contains("022_assignment_coi_maturity.sql"));
        assertTrue(verification.contains("Expected assignment/COI maturity tables to exist"));
    }

    @Test
    void realPlatformWaveNineMigrationIsWiredAndVerified() throws IOException {
        Path migrationPath = Path.of("..", "..", "database", "oracle", "023_publication_communication_maturity.sql");
        assertTrue(Files.exists(migrationPath));

        String migration = Files.readString(migrationPath);
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String applyScript = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        for (String tableName : realPlatformWaveNineTables()) {
            assertTrue(migration.contains("CREATE TABLE " + tableName), "023 migration should create " + tableName);
            assertTrue(verification.contains(tableName), "verify_schema should verify " + tableName);
            assertTrue(devUp.contains(tableName), "dev-up should detect " + tableName);
        }
        for (String indexName : realPlatformWaveNineIndexes()) {
            assertTrue(migration.contains(indexName), "023 migration should create " + indexName);
            assertTrue(verification.contains(indexName), "verify_schema should verify " + indexName);
        }
        assertTrue(applyScript.contains("023_publication_communication_maturity.sql"));
        assertTrue(devUp.contains("023_publication_communication_maturity.sql"));
        assertTrue(verification.contains("Expected publication/communication maturity tables to exist"));
    }

    @Test
    void retiredAgentTableDocumentationMatchesCurrentRetirementContract() throws IOException {
        String designStructure = Files.readString(Path.of("..", "..", "docs", "DESIGN_STRUCTURE.md"));

        assertFalse(designStructure.contains("仍可能出现在旧迁移或测试清理路径中"));
        assertTrue(designStructure.contains("011_retire_legacy_agent_tables.sql"));
        assertTrue(designStructure.contains("guard test"));
    }

    @Test
    void queryOptimizationMigrationIsIdempotentAndDevBootstrapChecksEveryIndex() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "010_database_query_optimization.sql"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("create_index_if_missing"));
        for (String indexName : queryOptimizationIndexes()) {
            assertTrue(migration.contains(indexName), "010 migration should manage " + indexName);
            assertTrue(devUp.contains(indexName), "dev-up should check " + indexName);
        }
        assertTrue(devUp.contains("all_query_optimization_indexes_exist"));
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
    void workflowQueryServiceKeepsPageQueriesInRepositories() throws IOException {
        String workflowService = Files.readString(Path.of("src/main/java/com/example/review/workflow/WorkflowQueryService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/example/review/workflow/WorkflowQueryController.java"));
        String adminRepository = Files.readString(Path.of("src/main/java/com/example/review/workflow/AdminAnalysisMonitorReadRepository.java"));

        assertFalse(workflowService.contains("JdbcTemplate"));
        assertFalse(workflowService.contains("SELECT A.ASSIGNMENT_ID"));
        assertFalse(workflowService.contains("FETCH FIRST 50 ROWS ONLY"));
        assertTrue(controller.contains("RequestParam"));
        assertTrue(adminRepository.contains("OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY"));
    }

    @Test
    void coreWorkflowServicesDoNotOwnJdbcDetails() throws IOException {
        String reviewWorkflowService = Files.readString(Path.of("src/main/java/com/example/review/review/ReviewWorkflowService.java"));
        String decisionService = Files.readString(Path.of("src/main/java/com/example/review/decision/DecisionService.java"));

        assertFalse(reviewWorkflowService.contains("JdbcTemplate"));
        assertFalse(reviewWorkflowService.contains("UPDATE MANUSCRIPT SET"));
        assertFalse(decisionService.contains("JdbcTemplate"));
        assertFalse(decisionService.contains("FOR UPDATE"));
        assertFalse(decisionService.contains("UPDATE REVIEW_ROUND"));
        assertFalse(decisionService.contains("UPDATE MANUSCRIPT SET"));
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

    @Test
    void authorDecisionPackageSecurityRulePrecedesChairDecisionRule() throws IOException {
        String security = Files.readString(Path.of("src/main/java/com/example/review/config/SecurityConfig.java"));
        int authorPackageRule = security.indexOf("/api/decisions/manuscripts/*/package");
        int chairDecisionRule = security.indexOf("/api/decisions/**");

        assertTrue(authorPackageRule > 0);
        assertTrue(chairDecisionRule > 0);
        assertTrue(authorPackageRule < chairDecisionRule);
    }

    private void assertSourceDoesNotContain(String path, String forbiddenText) throws IOException {
        String source = Files.readString(Path.of(path));
        assertFalse(source.contains(forbiddenText), path + " should not contain " + forbiddenText);
    }

    private List<String> queryOptimizationIndexes() {
        return List.of(
                "IDX_REVIEW_ROUND_STATUS_ID",
                "IDX_REVIEW_ASSIGNMENT_ROUND_ID",
                "IDX_REVIEW_ASSIGNMENT_ACCESS",
                "IDX_REVIEW_REPORT_ROUND",
                "IDX_CONFLICT_CHECK_MANUSCRIPT_REVIEWER",
                "IDX_MANUSCRIPT_STATUS_SUBMITTED",
                "IDX_ANALYSIS_PROJECTION_UPDATED"
        );
    }

    private List<String> businessOperationsTables() {
        return List.of(
                "REVIEW_DISCUSSION_MESSAGE",
                "CAMERA_READY_SUBMISSION",
                "COMMUNICATION_LOG"
        );
    }

    private List<String> businessOperationsIndexes() {
        return List.of(
                "IDX_REVIEW_DISCUSSION_ROUND",
                "IDX_REVIEW_DISCUSSION_ASSIGNMENT",
                "IDX_CAMERA_READY_MANUSCRIPT",
                "IDX_CAMERA_READY_STATUS",
                "IDX_COMMUNICATION_LOG_CONFERENCE",
                "IDX_COMMUNICATION_LOG_RECIPIENT"
        );
    }

    private List<String> realPlatformWaveSixSevenTables() {
        return List.of(
                "CONFERENCE_FORM_DEFINITION",
                "CONFERENCE_FORM_FIELD",
                "REVIEW_FORM_RESPONSE",
                "AUTHOR_FEEDBACK",
                "PAPER_TAG",
                "IMPORT_BATCH",
                "PAPER_ROLE_ASSIGNMENT"
        );
    }

    private List<String> realPlatformWaveSixSevenIndexes() {
        return List.of(
                "IDX_CONF_FORM_DEFINITION",
                "IDX_CONF_FORM_FIELD",
                "IDX_REVIEW_FORM_RESPONSE_ASSIGN",
                "IDX_AUTHOR_FEEDBACK_MANUSCRIPT",
                "IDX_PAPER_TAG_LOOKUP",
                "IDX_IMPORT_BATCH_CONFERENCE",
                "IDX_PAPER_ROLE_ASSIGNMENT_USER",
                "IDX_PAPER_ROLE_ASSIGNMENT_MANUSCRIPT",
                "IDX_PAPER_TAG_VALUE"
        );
    }

    private List<String> realPlatformWaveEightTables() {
        return List.of(
                "REVIEWER_INVITATION",
                "EXTERNAL_REVIEWER_DELEGATION",
                "CONFLICT_RELATIONSHIP",
                "REVIEWER_MATCHING_SCORE",
                "ASSIGNMENT_PROPOSAL_BUNDLE",
                "ASSIGNMENT_PROPOSAL",
                "ASSIGNMENT_OVERRIDE_AUDIT"
        );
    }

    private List<String> realPlatformWaveEightIndexes() {
        return List.of(
                "IDX_REVIEWER_INVITATION_STATUS",
                "IDX_EXTERNAL_DELEGATION_ASSIGN",
                "IDX_CONFLICT_REL_SUBJECT",
                "IDX_CONFLICT_REL_OBJECT",
                "IDX_REVIEWER_MATCHING_LOOKUP",
                "IDX_ASSIGNMENT_PROPOSAL_BUNDLE",
                "IDX_ASSIGNMENT_PROPOSAL_CANDIDATE",
                "IDX_ASSIGNMENT_OVERRIDE_BUNDLE"
        );
    }

    private List<String> realPlatformWaveNineTables() {
        return List.of(
                "EMAIL_TEMPLATE",
                "EMAIL_TEMPLATE_VERSION",
                "OUTBOUND_EMAIL_HISTORY",
                "OFFLINE_REVIEW_IMPORT_BATCH",
                "OFFLINE_REVIEW_IMPORT_ROW",
                "CAMERA_READY_FILE",
                "PUBLICATION_METADATA",
                "PROCEEDINGS_EXPORT_BATCH"
        );
    }

    private List<String> realPlatformWaveNineIndexes() {
        return List.of(
                "IDX_EMAIL_TEMPLATE_KEY",
                "IDX_EMAIL_TEMPLATE_VERSION",
                "IDX_OUTBOUND_EMAIL_STATUS",
                "IDX_OFFLINE_REVIEW_BATCH",
                "IDX_OFFLINE_REVIEW_ROW",
                "IDX_CAMERA_READY_FILE_STATUS",
                "IDX_PUBLICATION_METADATA_STATUS",
                "IDX_PROCEEDINGS_EXPORT_STATUS"
        );
    }
}
