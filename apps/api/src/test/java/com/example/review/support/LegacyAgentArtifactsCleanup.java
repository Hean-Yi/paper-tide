package com.example.review.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class LegacyAgentArtifactsCleanup {
    private LegacyAgentArtifactsCleanup() {
    }

    public static void deleteLegacyAgentArtifacts(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM AGENT_FEEDBACK");
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_RESULT");
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_TASK");
    }
}
