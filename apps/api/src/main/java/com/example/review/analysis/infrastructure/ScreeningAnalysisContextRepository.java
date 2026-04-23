package com.example.review.analysis.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScreeningAnalysisContextRepository {
    private final JdbcTemplate jdbcTemplate;

    public ScreeningAnalysisContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ScreeningAnalysisContext> findByManuscriptVersion(long manuscriptId, long versionId) {
        List<ScreeningAnalysisContext> rows = jdbcTemplate.query(
                """
                SELECT MANUSCRIPT_ID,
                       VERSION_ID,
                       TITLE,
                       ABSTRACT,
                       KEYWORDS,
                       PDF_FILE_SIZE
                FROM MANUSCRIPT_VERSION
                WHERE MANUSCRIPT_ID = ?
                  AND VERSION_ID = ?
                """,
                (rs, rowNum) -> new ScreeningAnalysisContext(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                ),
                manuscriptId,
                versionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record ScreeningAnalysisContext(
            long manuscriptId,
            long versionId,
            String title,
            String abstractText,
            String keywords,
            Long pdfFileSize
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
