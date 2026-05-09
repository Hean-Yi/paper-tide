package com.example.review.manuscript;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ManuscriptConferenceRepository {
    private final JdbcTemplate jdbcTemplate;

    ManuscriptConferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<ConferenceSubmissionTarget> findSubmissionTarget(long conferenceId) {
        List<ConferenceSubmissionTarget> rows = jdbcTemplate.query(
                """
                SELECT
                  c.CONFERENCE_ID,
                  c.CONFERENCE_STATUS,
                  c.BLIND_MODE,
                  p.ABSTRACT_SUBMISSION_CLOSE_AT,
                  p.SUBMISSION_CLOSE_AT
                FROM CONFERENCE c
                JOIN CONFERENCE_PHASE p ON p.CONFERENCE_ID = c.CONFERENCE_ID
                WHERE c.CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new ConferenceSubmissionTarget(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("CONFERENCE_STATUS"),
                        rs.getString("BLIND_MODE"),
                        toInstant(rs.getTimestamp("ABSTRACT_SUBMISSION_CLOSE_AT")),
                        toInstant(rs.getTimestamp("SUBMISSION_CLOSE_AT"))
                ),
                conferenceId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

record ConferenceSubmissionTarget(
        long conferenceId,
        String status,
        String blindMode,
        Instant abstractSubmissionCloseAt,
        Instant submissionCloseAt
) {
}
