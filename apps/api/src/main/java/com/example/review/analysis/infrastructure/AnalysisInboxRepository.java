package com.example.review.analysis.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisInboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public AnalysisInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean alreadyProcessed(String messageKey) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ANALYSIS_INBOX
                WHERE MESSAGE_KEY = ?
                  AND PROCESS_STATUS = 'PROCESSED'
                """,
                Integer.class,
                messageKey
        );
        return count != null && count > 0;
    }
}
