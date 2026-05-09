package com.example.review.workflow;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConferencePaperReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConferencePaperReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConferencePaperItem> findByConferenceId(long conferenceId) {
        List<ConferencePaperBaseRow> papers = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       V.VERSION_NO,
                       R.ROUND_ID,
                       R.ROUND_NO,
                       V.TITLE,
                       M.CURRENT_STATUS,
                       R.ROUND_STATUS,
                       M.LAST_DECISION_CODE,
                       M.SUBMITTED_AT,
                       (SELECT COUNT(*) FROM REVIEW_ASSIGNMENT A WHERE A.ROUND_ID = R.ROUND_ID) AS ASSIGNMENT_COUNT,
                       (SELECT COUNT(*) FROM REVIEW_REPORT RP WHERE RP.ROUND_ID = R.ROUND_ID) AS SUBMITTED_REVIEW_COUNT
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                LEFT JOIN REVIEW_ROUND R
                  ON R.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND R.VERSION_ID = M.CURRENT_VERSION_ID
                 AND R.ROUND_ID = (
                    SELECT MAX(R2.ROUND_ID)
                    FROM REVIEW_ROUND R2
                    WHERE R2.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                      AND R2.VERSION_ID = M.CURRENT_VERSION_ID
                 )
                WHERE M.CONFERENCE_ID = ?
                ORDER BY M.SUBMITTED_AT DESC NULLS LAST, M.MANUSCRIPT_ID DESC
                """,
                (rs, rowNum) -> new ConferencePaperBaseRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        nullableLong(rs, "ROUND_ID"),
                        nullableInteger(rs, "ROUND_NO"),
                        rs.getString("TITLE"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getString("ROUND_STATUS"),
                        rs.getInt("ASSIGNMENT_COUNT"),
                        rs.getInt("SUBMITTED_REVIEW_COUNT"),
                        rs.getString("LAST_DECISION_CODE"),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                conferenceId
        );
        Map<Long, List<ConferencePaperReviewerScore>> scoresByManuscript = findReviewerScoresByConferenceId(conferenceId);
        return papers.stream()
                .map(row -> {
                    List<ConferencePaperReviewerScore> scores = scoresByManuscript.getOrDefault(row.manuscriptId(), List.of());
                    return new ConferencePaperItem(
                            row.manuscriptId(),
                            row.versionId(),
                            row.versionNo(),
                            row.roundId(),
                            row.roundNo(),
                            row.title(),
                            row.currentStatus(),
                            row.roundStatus(),
                            row.assignmentCount(),
                            row.submittedReviewCount(),
                            row.lastDecisionCode(),
                            row.submittedAt(),
                            averageOverallScore(scores),
                            scores
                    );
                })
                .toList();
    }

    public Optional<ChairConferencePaperReviewDetail> findReviewDetail(long conferenceId, long manuscriptId) {
        List<ChairConferencePaperBase> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       V.VERSION_NO,
                       R.ROUND_ID,
                       R.ROUND_NO,
                       V.TITLE,
                       V.ABSTRACT,
                       V.KEYWORDS,
                       V.PDF_FILE,
                       V.PDF_FILE_NAME,
                       M.CURRENT_STATUS,
                       R.ROUND_STATUS,
                       M.SUBMITTED_AT
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                LEFT JOIN REVIEW_ROUND R
                  ON R.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND R.VERSION_ID = M.CURRENT_VERSION_ID
                 AND R.ROUND_ID = (
                    SELECT MAX(R2.ROUND_ID)
                    FROM REVIEW_ROUND R2
                    WHERE R2.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                      AND R2.VERSION_ID = M.CURRENT_VERSION_ID
                 )
                WHERE M.CONFERENCE_ID = ?
                  AND M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new ChairConferencePaperBase(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        nullableLong(rs, "ROUND_ID"),
                        nullableInteger(rs, "ROUND_NO"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getBytes("PDF_FILE"),
                        rs.getString("PDF_FILE_NAME"),
                        rs.getString("CURRENT_STATUS"),
                        rs.getString("ROUND_STATUS"),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                conferenceId,
                manuscriptId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ChairConferencePaperBase row = rows.getFirst();
        List<ChairConferencePaperReview> reviews = findReviewsByManuscript(conferenceId, manuscriptId);
        return Optional.of(new ChairConferencePaperReviewDetail(
                row.manuscriptId(),
                row.versionId(),
                row.versionNo(),
                row.roundId(),
                row.roundNo(),
                row.title(),
                row.abstractText(),
                row.keywords(),
                row.pdfFileName(),
                row.currentStatus(),
                row.roundStatus(),
                row.submittedAt(),
                0,
                averageReviewScore(reviews),
                reviews,
                row.pdfFile()
        ));
    }

    public Optional<ChairConferencePaperFile> findPaperFile(long conferenceId, long manuscriptId) {
        List<ChairConferencePaperFile> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.CURRENT_VERSION_ID AS VERSION_ID,
                       V.TITLE,
                       V.PDF_FILE,
                       V.PDF_FILE_NAME
                FROM MANUSCRIPT M
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                WHERE M.CONFERENCE_ID = ?
                  AND M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new ChairConferencePaperFile(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getBytes("PDF_FILE"),
                        rs.getString("PDF_FILE_NAME")
                ),
                conferenceId,
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private Map<Long, List<ConferencePaperReviewerScore>> findReviewerScoresByConferenceId(long conferenceId) {
        List<ConferencePaperReviewerScoreRow> rows = jdbcTemplate.query(
                """
                SELECT A.MANUSCRIPT_ID,
                       A.ASSIGNMENT_ID,
                       A.REVIEWER_ID,
                       U.REAL_NAME AS REVIEWER_NAME,
                       A.TASK_STATUS,
                       RP.OVERALL_SCORE,
                       RP.RECOMMENDATION,
                       RP.SUBMITTED_AT
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                JOIN SYS_USER U ON U.USER_ID = A.REVIEWER_ID
                LEFT JOIN REVIEW_REPORT RP ON RP.ASSIGNMENT_ID = A.ASSIGNMENT_ID
                WHERE M.CONFERENCE_ID = ?
                  AND A.ROUND_ID = (
                    SELECT MAX(R2.ROUND_ID)
                    FROM REVIEW_ROUND R2
                    WHERE R2.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                      AND R2.VERSION_ID = A.VERSION_ID
                  )
                ORDER BY A.MANUSCRIPT_ID, U.REAL_NAME, A.ASSIGNMENT_ID
                """,
                (rs, rowNum) -> new ConferencePaperReviewerScoreRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        new ConferencePaperReviewerScore(
                                rs.getLong("ASSIGNMENT_ID"),
                                rs.getLong("REVIEWER_ID"),
                                rs.getString("REVIEWER_NAME"),
                                rs.getString("TASK_STATUS"),
                                nullableInteger(rs, "OVERALL_SCORE"),
                                rs.getString("RECOMMENDATION"),
                                rs.getTimestamp("SUBMITTED_AT")
                        )
                ),
                conferenceId
        );
        Map<Long, List<ConferencePaperReviewerScore>> grouped = new LinkedHashMap<>();
        for (ConferencePaperReviewerScoreRow row : rows) {
            grouped.computeIfAbsent(row.manuscriptId(), ignored -> new ArrayList<>()).add(row.score());
        }
        return grouped;
    }

    private List<ChairConferencePaperReview> findReviewsByManuscript(long conferenceId, long manuscriptId) {
        return jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.REVIEWER_ID,
                       U.REAL_NAME AS REVIEWER_NAME,
                       U.INSTITUTION,
                       A.TASK_STATUS,
                       A.ASSIGNED_AT,
                       A.DEADLINE_AT,
                       A.SUBMITTED_AT AS ASSIGNMENT_SUBMITTED_AT,
                       RP.REVIEW_ID,
                       RP.NOVELTY_SCORE,
                       RP.METHOD_SCORE,
                       RP.EXPERIMENT_SCORE,
                       RP.WRITING_SCORE,
                       RP.OVERALL_SCORE,
                       RP.CONFIDENCE_LEVEL,
                       RP.STRENGTHS,
                       RP.WEAKNESSES,
                       RP.COMMENTS_TO_AUTHOR,
                       RP.COMMENTS_TO_CHAIR,
                       RP.RECOMMENDATION,
                       RP.SUBMITTED_AT AS REVIEW_SUBMITTED_AT
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                JOIN SYS_USER U ON U.USER_ID = A.REVIEWER_ID
                LEFT JOIN REVIEW_REPORT RP ON RP.ASSIGNMENT_ID = A.ASSIGNMENT_ID
                WHERE M.CONFERENCE_ID = ?
                  AND A.MANUSCRIPT_ID = ?
                  AND A.ROUND_ID = (
                    SELECT MAX(R2.ROUND_ID)
                    FROM REVIEW_ROUND R2
                    WHERE R2.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                      AND R2.VERSION_ID = A.VERSION_ID
                  )
                ORDER BY U.REAL_NAME, A.ASSIGNMENT_ID
                """,
                (rs, rowNum) -> new ChairConferencePaperReview(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("REVIEWER_NAME"),
                        rs.getString("INSTITUTION"),
                        rs.getString("TASK_STATUS"),
                        rs.getTimestamp("ASSIGNED_AT"),
                        rs.getTimestamp("DEADLINE_AT"),
                        rs.getTimestamp("ASSIGNMENT_SUBMITTED_AT"),
                        nullableLong(rs, "REVIEW_ID"),
                        nullableInteger(rs, "NOVELTY_SCORE"),
                        nullableInteger(rs, "METHOD_SCORE"),
                        nullableInteger(rs, "EXPERIMENT_SCORE"),
                        nullableInteger(rs, "WRITING_SCORE"),
                        nullableInteger(rs, "OVERALL_SCORE"),
                        rs.getString("CONFIDENCE_LEVEL"),
                        rs.getString("STRENGTHS"),
                        rs.getString("WEAKNESSES"),
                        rs.getString("COMMENTS_TO_AUTHOR"),
                        rs.getString("COMMENTS_TO_CHAIR"),
                        rs.getString("RECOMMENDATION"),
                        rs.getTimestamp("REVIEW_SUBMITTED_AT")
                ),
                conferenceId,
                manuscriptId
        );
    }

    private Double averageOverallScore(List<ConferencePaperReviewerScore> scores) {
        double average = scores.stream()
                .map(ConferencePaperReviewerScore::overallScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(average) ? null : average;
    }

    private Double averageReviewScore(List<ChairConferencePaperReview> reviews) {
        double average = reviews.stream()
                .map(ChairConferencePaperReview::overallScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(average) ? null : average;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}

record ConferencePaperBaseRow(
        long manuscriptId,
        long versionId,
        int versionNo,
        Long roundId,
        Integer roundNo,
        String title,
        String currentStatus,
        String roundStatus,
        int assignmentCount,
        int submittedReviewCount,
        String lastDecisionCode,
        java.sql.Timestamp submittedAt
) {
}

record ConferencePaperReviewerScoreRow(
        long manuscriptId,
        ConferencePaperReviewerScore score
) {
}

record ChairConferencePaperBase(
        long manuscriptId,
        long versionId,
        int versionNo,
        Long roundId,
        Integer roundNo,
        String title,
        String abstractText,
        String keywords,
        byte[] pdfFile,
        String pdfFileName,
        String currentStatus,
        String roundStatus,
        java.sql.Timestamp submittedAt
) {
}
