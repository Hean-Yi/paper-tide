package com.example.review.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformRepository {
    private static final TypeReference<Map<String, Object>> ANSWERS_TYPE = new TypeReference<>() {
    };

    protected final JdbcTemplate jdbcTemplate;
    protected final ObjectMapper objectMapper;

    public RealPlatformRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<PlatformConferenceRow> findConference(long conferenceId) {
        List<PlatformConferenceRow> rows = jdbcTemplate.query(
                """
                SELECT CONFERENCE_ID, ORGANIZER_USER_ID
                FROM CONFERENCE
                WHERE CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new PlatformConferenceRow(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                conferenceId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformManuscriptRow> findManuscript(long manuscriptId) {
        List<PlatformManuscriptRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.SUBMITTER_ID,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       C.ORGANIZER_USER_ID
                FROM MANUSCRIPT M
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new PlatformManuscriptRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("SUBMITTER_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("CURRENT_VERSION_ID", Long.class),
                        rs.getString("CURRENT_STATUS"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public boolean userExists(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SYS_USER WHERE USER_ID = ?",
                Integer.class,
                userId
        );
        return count != null && count > 0;
    }

    public Optional<PlatformAssignmentRow> findAssignment(long assignmentId) {
        List<PlatformAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       A.TASK_STATUS,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new PlatformAssignmentRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformReviewRoundRow> findReviewRound(long roundId) {
        List<PlatformReviewRoundRow> rows = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE R.ROUND_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Payload cannot be serialized");
        }
    }

    protected Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, ANSWERS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored answers payload is invalid");
        }
    }

    protected <T> T fromJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored import payload is invalid");
        }
    }
}
