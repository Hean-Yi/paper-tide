package com.example.review.analysis.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConflictAnalysisContextRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConflictAnalysisContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ConflictAnalysisContext> findByRoundId(long roundId) {
        List<ConflictAnalysisContext> rows = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       V.TITLE,
                       V.ABSTRACT,
                       V.KEYWORDS,
                       V.PDF_FILE_SIZE
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = R.VERSION_ID
                WHERE R.ROUND_ID = ?
                """,
                (rs, rowNum) -> new ConflictAnalysisContext(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getObject("PDF_FILE_SIZE", Long.class),
                        listReviewReports(roundId)
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private List<Map<String, Object>> listReviewReports(long roundId) {
        return jdbcTemplate.queryForList(
                """
                SELECT REVIEW_ID AS "reviewId",
                       NOVELTY_SCORE AS "noveltyScore",
                       METHOD_SCORE AS "methodScore",
                       EXPERIMENT_SCORE AS "experimentScore",
                       WRITING_SCORE AS "writingScore",
                       OVERALL_SCORE AS "overallScore",
                       CONFIDENCE_LEVEL AS "confidenceLevel",
                       STRENGTHS AS "strengths",
                       WEAKNESSES AS "weaknesses",
                       COMMENTS_TO_AUTHOR AS "commentsToAuthor",
                       COMMENTS_TO_CHAIR AS "commentsToChair",
                       RECOMMENDATION AS "recommendation"
                FROM REVIEW_REPORT
                WHERE ROUND_ID = ?
                ORDER BY REVIEW_ID
                """,
                roundId
        );
    }

    public record ConflictAnalysisContext(
            long roundId,
            long manuscriptId,
            long versionId,
            String title,
            String abstractText,
            String keywords,
            Long pdfFileSize,
            List<Map<String, Object>> reviewReports
    ) {
        public List<String> keywordList() {
            if (keywords == null || keywords.isBlank()) {
                return List.of();
            }
            return Arrays.stream(keywords.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
    }
}
