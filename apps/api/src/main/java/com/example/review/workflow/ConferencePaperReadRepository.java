package com.example.review.workflow;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConferencePaperReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConferencePaperReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConferencePaperItem> findByConferenceId(long conferenceId) {
        return jdbcTemplate.query(
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
                (rs, rowNum) -> new ConferencePaperItem(
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
