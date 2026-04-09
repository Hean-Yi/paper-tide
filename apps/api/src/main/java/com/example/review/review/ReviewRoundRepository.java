package com.example.review.review;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRoundRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewRoundRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextRoundId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
    }

    public int nextRoundNo(long manuscriptId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ROUND_NO), 0) FROM REVIEW_ROUND WHERE MANUSCRIPT_ID = ?",
                Integer.class,
                manuscriptId
        );
        return (max == null ? 0 : max) + 1;
    }

    public void insert(
            long roundId,
            long manuscriptId,
            int roundNo,
            long versionId,
            String roundStatus,
            String assignmentStrategy,
            boolean screeningRequired,
            Timestamp deadlineAt,
            long createdBy
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID,
                  MANUSCRIPT_ID,
                  ROUND_NO,
                  VERSION_ID,
                  ROUND_STATUS,
                  ASSIGNMENT_STRATEGY,
                  SCREENING_REQUIRED,
                  DEADLINE_AT,
                  CREATED_BY,
                  CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                roundNo,
                versionId,
                roundStatus,
                assignmentStrategy,
                screeningRequired ? 1 : 0,
                deadlineAt,
                createdBy
        );
    }

    public Optional<ReviewRoundRow> findById(long roundId) {
        List<ReviewRoundRow> rows = jdbcTemplate.query(
                """
                SELECT ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY
                FROM REVIEW_ROUND
                WHERE ROUND_ID = ?
                """,
                (rs, rowNum) -> new ReviewRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("ROUND_STATUS"),
                        rs.getString("ASSIGNMENT_STRATEGY"),
                        rs.getInt("SCREENING_REQUIRED") == 1,
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getLong("CREATED_BY")
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<ReviewRoundRow> findByIdForUpdate(long roundId) {
        List<ReviewRoundRow> rows = jdbcTemplate.query(
                """
                SELECT ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY
                FROM REVIEW_ROUND
                WHERE ROUND_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ReviewRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getInt("ROUND_NO"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("ROUND_STATUS"),
                        rs.getString("ASSIGNMENT_STRATEGY"),
                        rs.getInt("SCREENING_REQUIRED") == 1,
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getLong("CREATED_BY")
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void updateStatus(long roundId, String roundStatus) {
        jdbcTemplate.update(
                "UPDATE REVIEW_ROUND SET ROUND_STATUS = ? WHERE ROUND_ID = ?",
                roundStatus,
                roundId
        );
    }
}

record ReviewRoundRow(
        long roundId,
        long manuscriptId,
        int roundNo,
        long versionId,
        String roundStatus,
        String assignmentStrategy,
        boolean screeningRequired,
        Timestamp deadlineAt,
        long createdBy
) {
}
