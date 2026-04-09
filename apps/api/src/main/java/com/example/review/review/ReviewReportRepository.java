package com.example.review.review;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextReviewId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_REPORT.NEXTVAL FROM DUAL", Long.class);
    }

    public void insert(
            long reviewId,
            long assignmentId,
            long roundId,
            long manuscriptId,
            long reviewerId,
            SubmitReviewReportRequest request,
            Timestamp submittedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_REPORT (
                  REVIEW_ID,
                  ASSIGNMENT_ID,
                  ROUND_ID,
                  MANUSCRIPT_ID,
                  REVIEWER_ID,
                  NOVELTY_SCORE,
                  METHOD_SCORE,
                  EXPERIMENT_SCORE,
                  WRITING_SCORE,
                  OVERALL_SCORE,
                  CONFIDENCE_LEVEL,
                  STRENGTHS,
                  WEAKNESSES,
                  COMMENTS_TO_AUTHOR,
                  COMMENTS_TO_CHAIR,
                  RECOMMENDATION,
                  SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reviewId,
                assignmentId,
                roundId,
                manuscriptId,
                reviewerId,
                request.noveltyScore(),
                request.methodScore(),
                request.experimentScore(),
                request.writingScore(),
                request.overallScore(),
                request.confidenceLevel(),
                request.strengths(),
                request.weaknesses(),
                request.commentsToAuthor(),
                request.commentsToChair(),
                request.recommendation(),
                submittedAt
        );
    }

    public Optional<ReviewReportRow> findByAssignmentId(long assignmentId) {
        List<ReviewReportRow> rows = jdbcTemplate.query(
                """
                SELECT REVIEW_ID, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID, OVERALL_SCORE, CONFIDENCE_LEVEL, RECOMMENDATION, SUBMITTED_AT
                FROM REVIEW_REPORT
                WHERE ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new ReviewReportRow(
                        rs.getLong("REVIEW_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("OVERALL_SCORE"),
                        rs.getString("CONFIDENCE_LEVEL"),
                        rs.getString("RECOMMENDATION"),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}

record ReviewReportRow(
        long reviewId,
        long assignmentId,
        long roundId,
        long manuscriptId,
        long reviewerId,
        int overallScore,
        String confidenceLevel,
        String recommendation,
        Timestamp submittedAt
) {
}
