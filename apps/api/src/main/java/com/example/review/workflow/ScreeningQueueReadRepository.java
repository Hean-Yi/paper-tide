package com.example.review.workflow;

import com.example.review.analysis.interfaces.AnalysisDtos.AnalysisIntentResponse;
import java.sql.PreparedStatement;
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
        return findOpenScreeningItems(null);
    }

    public List<ScreeningQueueItem> findOpenScreeningItemsByOrganizer(long organizerUserId) {
        return findOpenScreeningItems(organizerUserId);
    }

    private List<ScreeningQueueItem> findOpenScreeningItems(Long organizerUserId) {
        String ownerPredicate = organizerUserId == null ? "" : "  AND C.ORGANIZER_USER_ID = ?\n";
        return jdbcTemplate.query(
                ("""
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       M.CURRENT_STATUS,
                       M.CURRENT_ROUND_NO,
                       M.BLIND_MODE,
                       M.SUBMITTED_AT,
                       V.VERSION_NO,
                       V.TITLE,
                    V.ABSTRACT AS ABSTRACT_TEXT,
                    V.KEYWORDS,
                       V.PDF_FILE_NAME,
                       V.PDF_FILE_SIZE,
                       SI.INTENT_ID AS SCREENING_INTENT_ID,
                       SI.ANALYSIS_TYPE AS SCREENING_ANALYSIS_TYPE,
                       SI.BUSINESS_STATUS AS SCREENING_BUSINESS_STATUS
                FROM MANUSCRIPT M
                JOIN CONFERENCE C ON C.CONFERENCE_ID = M.CONFERENCE_ID
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                LEFT JOIN (
                  SELECT INTENT_ID, ANALYSIS_TYPE, BUSINESS_STATUS, BUSINESS_ANCHOR_ID, BUSINESS_ANCHOR_VERSION_ID
                  FROM (
                    SELECT I.INTENT_ID,
                           I.ANALYSIS_TYPE,
                           I.BUSINESS_STATUS,
                           I.BUSINESS_ANCHOR_ID,
                           I.BUSINESS_ANCHOR_VERSION_ID,
                           ROW_NUMBER() OVER (
                             PARTITION BY I.BUSINESS_ANCHOR_ID, I.BUSINESS_ANCHOR_VERSION_ID
                             ORDER BY I.INTENT_ID DESC
                           ) AS RN
                    FROM ANALYSIS_INTENT I
                    WHERE I.ANALYSIS_TYPE = 'SCREENING'
                      AND I.BUSINESS_ANCHOR_TYPE = 'MANUSCRIPT_VERSION'
                  )
                  WHERE RN = 1
                ) SI ON SI.BUSINESS_ANCHOR_ID = M.MANUSCRIPT_ID
                    AND SI.BUSINESS_ANCHOR_VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.CURRENT_STATUS IN ('SUBMITTED', 'REVISED_SUBMITTED', 'UNDER_SCREENING')
                %s
                ORDER BY M.SUBMITTED_AT NULLS LAST, M.MANUSCRIPT_ID
                """).formatted(ownerPredicate),
                ps -> bindOrganizer(ps, organizerUserId),
                (rs, rowNum) -> {
                    Long intentId = rs.getObject("SCREENING_INTENT_ID", Long.class);
                    AnalysisIntentResponse screeningIntent = intentId == null
                            ? null
                            : new AnalysisIntentResponse(
                                    intentId,
                                    rs.getString("SCREENING_ANALYSIS_TYPE"),
                                    rs.getString("SCREENING_BUSINESS_STATUS")
                            );
                    return new ScreeningQueueItem(
                            rs.getLong("MANUSCRIPT_ID"),
                            rs.getLong("VERSION_ID"),
                            rs.getInt("VERSION_NO"),
                            rs.getString("TITLE"),
                            rs.getString("ABSTRACT_TEXT"),
                            rs.getString("KEYWORDS"),
                            rs.getString("CURRENT_STATUS"),
                            rs.getInt("CURRENT_ROUND_NO"),
                            rs.getString("BLIND_MODE"),
                            rs.getTimestamp("SUBMITTED_AT"),
                            rs.getString("PDF_FILE_NAME"),
                            rs.getObject("PDF_FILE_SIZE", Long.class),
                            screeningIntent
                    );
                }
        );
    }

    private void bindOrganizer(PreparedStatement ps, Long organizerUserId) throws java.sql.SQLException {
        if (organizerUserId != null) {
            ps.setLong(1, organizerUserId);
        }
    }
}
