package com.example.review.analysis.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewerAssignmentAssistContextRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewerAssignmentAssistContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AssignmentAssistContext> findByRoundId(long roundId) {
        List<AssignmentAssistContext> rows = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       V.TITLE,
                       V.ABSTRACT,
                       V.KEYWORDS
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = R.VERSION_ID
                WHERE R.ROUND_ID = ?
                """,
                (rs, rowNum) -> new AssignmentAssistContext(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        listCandidateDrafts(roundId)
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private List<Map<String, Object>> listCandidateDrafts(long roundId) {
        return jdbcTemplate.queryForList(
                """
                SELECT ASSIGNMENT_DRAFT_ID AS "draftId",
                       REVIEWER_ID AS "reviewerId",
                       RANK_ORDER AS "rankOrder",
                       SCORE AS "score",
                       CURRENT_LOAD AS "currentLoad",
                       MAX_LOAD AS "maxLoad",
                       BID_VALUE AS "bidValue",
                       REASON AS "reason",
                       DRAFT_STATUS AS "draftStatus"
                FROM ASSIGNMENT_DRAFT
                WHERE ROUND_ID = ?
                  AND DRAFT_STATUS = 'PROPOSED'
                ORDER BY RANK_ORDER, ASSIGNMENT_DRAFT_ID
                """,
                roundId
        );
    }

    public record AssignmentAssistContext(
            long roundId,
            long manuscriptId,
            long versionId,
            String title,
            String abstractText,
            String keywords,
            List<Map<String, Object>> candidateDrafts
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
