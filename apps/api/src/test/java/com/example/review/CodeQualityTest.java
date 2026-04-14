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
    void agentPollingHasRuntimeSchedule() throws IOException {
        String applicationSource = Files.readString(Path.of("src/main/java/com/example/review/ReviewApplication.java"));
        String schedulerSource = Files.readString(Path.of("src/main/java/com/example/review/agent/AgentPollingScheduler.java"));
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationSource.contains("@EnableScheduling"), "ReviewApplication should enable Spring scheduling");
        assertTrue(schedulerSource.contains("@Scheduled"), "AgentPollingScheduler should have a runtime schedule");
        assertTrue(config.contains("polling-delay-ms"), "application.yml should expose review.agent.polling-delay-ms");
    }

    private void assertSourceDoesNotContain(String path, String forbiddenText) throws IOException {
        String source = Files.readString(Path.of(path));
        assertFalse(source.contains(forbiddenText), path + " should not contain " + forbiddenText);
    }
}
