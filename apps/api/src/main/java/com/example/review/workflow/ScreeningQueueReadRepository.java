package com.example.review.workflow;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScreeningQueueReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ScreeningQueueReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScreeningQueueItem> findOpenScreeningItems() {
        return jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       V.VERSION_NO,
                       V.TITLE,
                       V.PDF_FILE_NAME,
                       V.PDF_FILE_SIZE
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.CURRENT_STATUS IN ('SUBMITTED', 'REVISED_SUBMITTED', 'UNDER_SCREENING')
                ORDER BY M.SUBMITTED_AT NULLS LAST, M.MANUSCRIPT_ID
                """,
                (rs, rowNum) -> new ScreeningQueueItem(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        rs.getString("TITLE"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getInt("CURRENT_ROUND_NO"),
                        rs.getString("BLIND_MODE"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getString("PDF_FILE_NAME"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                )
        );
    }
}
