package com.example.review.analysis.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewerAssistContextRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewerAssistContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ReviewerAssistContext> findByAssignmentId(long assignmentId) {
        List<ReviewerAssistContext> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       A.TASK_STATUS,
                       V.TITLE,
                       V.ABSTRACT,
                       V.KEYWORDS,
                       V.PDF_FILE_SIZE
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = A.VERSION_ID
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new ReviewerAssistContext(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record ReviewerAssistContext(
            long assignmentId,
            long roundId,
            long manuscriptId,
            long versionId,
            long reviewerId,
            String taskStatus,
            String title,
            String abstractText,
            String keywords,
            Long pdfFileSize
    ) {
        public List<String> keywordList() {
            if (keywords == null || keywords.isBlank()) {
                return List.of();
            }
            return List.of(keywords.split("\\s*,\\s*"));
        }
    }
}
