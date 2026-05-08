package com.example.review.conference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class JdbcConferenceRepository implements ConferenceRepository {
    private static final String DETAIL_SELECT = """
            SELECT
              c.CONFERENCE_ID,
              c.NAME,
              c.ACRONYM,
              c.CONFERENCE_YEAR,
              c.ORGANIZER_USER_ID,
              c.CONFERENCE_STATUS,
              c.BLIND_MODE,
              c.CFP_TEXT,
              c.TOPIC_AREAS_JSON,
              c.TARGET_REVIEWS_PER_PAPER,
              c.DEFAULT_REVIEWER_MAX_LOAD,
              c.PUBLIC_SLUG,
              c.CFP_PUBLISHED,
              c.APPROVED_BY,
              c.APPROVED_AT,
              p.PHASE_ID,
              p.SUBMISSION_OPEN_AT,
              p.SUBMISSION_CLOSE_AT,
              p.BIDDING_OPEN_AT,
              p.BIDDING_CLOSE_AT,
              p.REVIEW_DEADLINE_AT,
              p.DECISION_RELEASE_AT
            FROM CONFERENCE c
            JOIN CONFERENCE_PHASE p ON p.CONFERENCE_ID = c.CONFERENCE_ID
            """;
    private static final String PUBLIC_STATUS_LIST =
            "'OPEN_FOR_SUBMISSION', 'SUBMISSION_CLOSED', 'BIDDING_OPEN', 'REVIEW_ASSIGNMENT', 'REVIEWING', 'DECISION', 'CLOSED'";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ConferenceDetail> detailMapper = this::mapDetail;
    private final RowMapper<ConferenceSummary> summaryMapper = this::mapSummary;

    JdbcConferenceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long createConference(ConferenceDraft draft) {
        Long id = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE (
                  CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                  CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                  TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                draft.name(),
                draft.acronym(),
                draft.year(),
                draft.organizerUserId(),
                draft.status(),
                draft.blindMode(),
                draft.cfpText(),
                toJson(draft.topicAreas()),
                draft.targetReviewsPerPaper(),
                draft.defaultReviewerMaxLoad(),
                draft.publicSlug(),
                draft.cfpPublished() ? 1 : 0
        );
        return id == null ? 0L : id;
    }

    @Override
    public void createPhase(long conferenceId, ConferencePhaseDraft draft) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_PHASE (
                  PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, SUBMISSION_CLOSE_AT,
                  BIDDING_OPEN_AT, BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                ) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)
                """,
                conferenceId,
                timestamp(draft.submissionOpenAt()),
                timestamp(draft.submissionCloseAt()),
                timestamp(draft.biddingOpenAt()),
                timestamp(draft.biddingCloseAt()),
                timestamp(draft.reviewDeadlineAt()),
                timestamp(draft.decisionReleaseAt())
        );
    }

    @Override
    public Optional<ConferenceDetail> findDetail(long conferenceId) {
        List<ConferenceDetail> results = jdbcTemplate.query(
                DETAIL_SELECT + " WHERE c.CONFERENCE_ID = ?",
                detailMapper,
                conferenceId
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<ConferenceDetail> findPublicCfpBySlug(String publicSlug) {
        List<ConferenceDetail> results = jdbcTemplate.query(
                DETAIL_SELECT + """
                 WHERE c.PUBLIC_SLUG = ?
                   AND c.CFP_PUBLISHED = 1
                   AND c.CONFERENCE_STATUS IN (%s)
                """.formatted(PUBLIC_STATUS_LIST),
                detailMapper,
                publicSlug
        );
        return results.stream().findFirst();
    }

    @Override
    public List<ConferenceSummary> listPublicCfps() {
        return jdbcTemplate.query(
                """
                SELECT
                  c.CONFERENCE_ID,
                  c.NAME,
                  c.ACRONYM,
                  c.CONFERENCE_YEAR,
                  c.CONFERENCE_STATUS,
                  c.BLIND_MODE,
                  c.PUBLIC_SLUG,
                  p.SUBMISSION_OPEN_AT,
                  p.SUBMISSION_CLOSE_AT
                FROM CONFERENCE c
                JOIN CONFERENCE_PHASE p ON p.CONFERENCE_ID = c.CONFERENCE_ID
                WHERE c.CFP_PUBLISHED = 1
                  AND c.CONFERENCE_STATUS IN (%s)
                ORDER BY p.SUBMISSION_CLOSE_AT ASC, c.CONFERENCE_ID ASC
                """.formatted(PUBLIC_STATUS_LIST),
                summaryMapper
        );
    }

    @Override
    public List<ConferenceSummary> listPendingApproval() {
        return jdbcTemplate.query(
                """
                SELECT
                  c.CONFERENCE_ID,
                  c.NAME,
                  c.ACRONYM,
                  c.CONFERENCE_YEAR,
                  c.CONFERENCE_STATUS,
                  c.BLIND_MODE,
                  c.PUBLIC_SLUG,
                  p.SUBMISSION_OPEN_AT,
                  p.SUBMISSION_CLOSE_AT
                FROM CONFERENCE c
                JOIN CONFERENCE_PHASE p ON p.CONFERENCE_ID = c.CONFERENCE_ID
                WHERE c.CONFERENCE_STATUS = 'PENDING_APPROVAL'
                ORDER BY c.CREATED_AT ASC, c.CONFERENCE_ID ASC
                """,
                summaryMapper
        );
    }

    @Override
    public List<ConferenceSummary> listManageable(Long organizerUserId) {
        String ownerPredicate = organizerUserId == null ? "" : "WHERE c.ORGANIZER_USER_ID = ?";
        Object[] args = organizerUserId == null ? new Object[]{} : new Object[]{organizerUserId};
        return jdbcTemplate.query(
                """
                SELECT
                  c.CONFERENCE_ID,
                  c.NAME,
                  c.ACRONYM,
                  c.CONFERENCE_YEAR,
                  c.CONFERENCE_STATUS,
                  c.BLIND_MODE,
                  c.PUBLIC_SLUG,
                  p.SUBMISSION_OPEN_AT,
                  p.SUBMISSION_CLOSE_AT
                FROM CONFERENCE c
                JOIN CONFERENCE_PHASE p ON p.CONFERENCE_ID = c.CONFERENCE_ID
                %s
                ORDER BY c.CONFERENCE_YEAR DESC, c.CONFERENCE_ID DESC
                """.formatted(ownerPredicate),
                summaryMapper,
                args
        );
    }

    @Override
    public boolean publicSlugExists(String publicSlug) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CONFERENCE WHERE PUBLIC_SLUG = ?",
                Integer.class,
                publicSlug
        );
        return count != null && count > 0;
    }

    @Override
    public void updateStatus(long conferenceId, String status, boolean cfpPublished, Long approvedBy, Instant approvedAt) {
        jdbcTemplate.update(
                """
                UPDATE CONFERENCE
                SET CONFERENCE_STATUS = ?,
                    CFP_PUBLISHED = ?,
                    APPROVED_BY = ?,
                    APPROVED_AT = ?,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE CONFERENCE_ID = ?
                """,
                status,
                cfpPublished ? 1 : 0,
                approvedBy,
                timestamp(approvedAt),
                conferenceId
        );
    }

    private ConferenceDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        long conferenceId = rs.getLong("CONFERENCE_ID");
        ConferencePhase phase = new ConferencePhase(
                rs.getLong("PHASE_ID"),
                conferenceId,
                instant(rs.getTimestamp("SUBMISSION_OPEN_AT")),
                instant(rs.getTimestamp("SUBMISSION_CLOSE_AT")),
                instant(rs.getTimestamp("BIDDING_OPEN_AT")),
                instant(rs.getTimestamp("BIDDING_CLOSE_AT")),
                instant(rs.getTimestamp("REVIEW_DEADLINE_AT")),
                instant(rs.getTimestamp("DECISION_RELEASE_AT"))
        );
        return new ConferenceDetail(
                conferenceId,
                rs.getString("NAME"),
                rs.getString("ACRONYM"),
                rs.getInt("CONFERENCE_YEAR"),
                rs.getLong("ORGANIZER_USER_ID"),
                rs.getString("CONFERENCE_STATUS"),
                rs.getString("BLIND_MODE"),
                rs.getString("CFP_TEXT"),
                fromJsonList(rs.getString("TOPIC_AREAS_JSON")),
                rs.getInt("TARGET_REVIEWS_PER_PAPER"),
                rs.getInt("DEFAULT_REVIEWER_MAX_LOAD"),
                rs.getString("PUBLIC_SLUG"),
                rs.getInt("CFP_PUBLISHED") == 1,
                nullableLong(rs, "APPROVED_BY"),
                instant(rs.getTimestamp("APPROVED_AT")),
                phase
        );
    }

    private ConferenceSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ConferenceSummary(
                rs.getLong("CONFERENCE_ID"),
                rs.getString("NAME"),
                rs.getString("ACRONYM"),
                rs.getInt("CONFERENCE_YEAR"),
                rs.getString("CONFERENCE_STATUS"),
                rs.getString("BLIND_MODE"),
                rs.getString("PUBLIC_SLUG"),
                instant(rs.getTimestamp("SUBMISSION_OPEN_AT")),
                instant(rs.getTimestamp("SUBMISSION_CLOSE_AT"))
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Topic areas are invalid", ex);
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Stored topic areas are invalid", ex);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
