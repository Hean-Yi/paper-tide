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

    private void assertSourceDoesNotContain(String path, String forbiddenText) throws IOException {
        String source = Files.readString(Path.of(path));
        assertFalse(source.contains(forbiddenText), path + " should not contain " + forbiddenText);
    }
}
