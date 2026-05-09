package com.example.review.conference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcConferenceReviewerRepository implements ConferenceReviewerRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcConferenceReviewerRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isActivePlatformReviewer(long reviewerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM SYS_USER U
                WHERE U.USER_ID = ?
                  AND U.STATUS = 'ACTIVE'
                  AND EXISTS (
                    SELECT 1
                    FROM SYS_USER_ROLE UR
                    JOIN SYS_ROLE R ON R.ROLE_ID = UR.ROLE_ID
                    WHERE UR.USER_ID = U.USER_ID
                      AND R.ROLE_CODE IN ('AUTHOR', 'REVIEWER')
                  )
                """,
                Integer.class,
                reviewerId
        );
        return count != null && count > 0;
    }

    @Override
    public List<PlatformReviewerSearchResult> searchActivePlatformReviewers(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        String likeQuery = "%" + normalizedQuery + "%";
        String searchPredicate = normalizedQuery.isBlank() ? "" : """
                    AND (
                      LOWER(U.REAL_NAME) LIKE ?
                      OR LOWER(U.EMAIL) LIKE ?
                      OR LOWER(U.USERNAME) LIKE ?
                      OR LOWER(NVL(U.INSTITUTION, '')) LIKE ?
                    )
                """;
        String sql = """
                SELECT *
                FROM (
                  SELECT U.USER_ID, U.REAL_NAME, U.EMAIL, U.INSTITUTION
                  FROM SYS_USER U
                  WHERE U.STATUS = 'ACTIVE'
                    AND EXISTS (
                      SELECT 1
                      FROM SYS_USER_ROLE UR
                      JOIN SYS_ROLE R ON R.ROLE_ID = UR.ROLE_ID
                      WHERE UR.USER_ID = U.USER_ID
                        AND R.ROLE_CODE IN ('AUTHOR', 'REVIEWER')
                    )
                %s
                  ORDER BY
                    CASE
                      WHEN LOWER(U.EMAIL) = ? THEN 0
                      WHEN LOWER(U.REAL_NAME) = ? THEN 1
                      WHEN LOWER(U.USERNAME) = ? THEN 2
                      ELSE 3
                    END,
                    U.REAL_NAME,
                    U.USER_ID
                )
                WHERE ROWNUM <= ?
                """.formatted(searchPredicate);
        Object[] args = normalizedQuery.isBlank()
                ? new Object[]{"", "", "", limit}
                : new Object[]{likeQuery, likeQuery, likeQuery, likeQuery, normalizedQuery, normalizedQuery, normalizedQuery, limit};
        List<PlatformReviewerUserRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PlatformReviewerUserRow(
                        rs.getLong("USER_ID"),
                        rs.getString("REAL_NAME"),
                        rs.getString("EMAIL"),
                        rs.getString("INSTITUTION")
                ),
                args
        );
        return rows.stream()
                .map(row -> new PlatformReviewerSearchResult(
                        row.userId(),
                        row.realName(),
                        row.email(),
                        row.institution(),
                        listUserResearchAreas(row.userId())
                ))
                .toList();
    }

    @Override
    public void grantReviewerRole(long userId) {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (
                  SELECT ? AS USER_ID, R.ROLE_ID
                  FROM SYS_ROLE R
                  WHERE R.ROLE_CODE = 'REVIEWER'
                ) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """,
                userId
        );
    }

    @Override
    public List<ResearchAreaSnapshot> listUserResearchAreas(long userId) {
        return jdbcTemplate.query(
                """
                SELECT AREA_CODE, AREA_NAME
                FROM USER_RESEARCH_AREA
                WHERE USER_ID = ?
                ORDER BY AREA_CODE
                """,
                (rs, rowNum) -> new ResearchAreaSnapshot(rs.getString("AREA_CODE"), rs.getString("AREA_NAME")),
                userId
        );
    }

    @Override
    public ConferenceReviewerMembership upsertConferenceReviewer(
            long conferenceId,
            long reviewerId,
            int maxLoad,
            long invitedBy,
            List<ResearchAreaSnapshot> researchAreas
    ) {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE_REVIEWER TARGET
                USING (
                  SELECT ? CONFERENCE_ID,
                         ? REVIEWER_ID,
                         ? MAX_LOAD,
                         ? RESEARCH_AREAS_JSON,
                         ? INVITED_BY
                  FROM DUAL
                ) SOURCE
                ON (TARGET.CONFERENCE_ID = SOURCE.CONFERENCE_ID AND TARGET.REVIEWER_ID = SOURCE.REVIEWER_ID)
                WHEN MATCHED THEN UPDATE SET
                  TARGET.MAX_LOAD = SOURCE.MAX_LOAD,
                  TARGET.RESEARCH_AREAS_JSON = SOURCE.RESEARCH_AREAS_JSON,
                  TARGET.MEMBERSHIP_STATUS = 'ACTIVE',
                  TARGET.INVITED_BY = SOURCE.INVITED_BY,
                  TARGET.JOINED_AT = CURRENT_TIMESTAMP,
                  TARGET.UPDATED_AT = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                  CONFERENCE_REVIEWER_ID,
                  CONFERENCE_ID,
                  REVIEWER_ID,
                  MAX_LOAD,
                  RESEARCH_AREAS_JSON,
                  MEMBERSHIP_STATUS,
                  INVITED_BY,
                  JOINED_AT,
                  UPDATED_AT
                ) VALUES (
                  SEQ_CONFERENCE_REVIEWER.NEXTVAL,
                  SOURCE.CONFERENCE_ID,
                  SOURCE.REVIEWER_ID,
                  SOURCE.MAX_LOAD,
                  SOURCE.RESEARCH_AREAS_JSON,
                  'ACTIVE',
                  SOURCE.INVITED_BY,
                  CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP
                )
                """,
                conferenceId,
                reviewerId,
                maxLoad,
                toJson(researchAreas),
                invitedBy
        );
        return findActiveMembership(conferenceId, reviewerId)
                .orElseThrow(() -> new ConferenceNotFoundException("Conference reviewer membership was not found"));
    }

    @Override
    public Optional<ConferenceReviewerMembership> findActiveMembership(long conferenceId, long reviewerId) {
        List<ConferenceReviewerMembership> rows = jdbcTemplate.query(
                """
                SELECT CONFERENCE_REVIEWER_ID, CONFERENCE_ID, REVIEWER_ID, MAX_LOAD,
                       RESEARCH_AREAS_JSON, MEMBERSHIP_STATUS, JOINED_AT
                FROM CONFERENCE_REVIEWER
                WHERE CONFERENCE_ID = ?
                  AND REVIEWER_ID = ?
                  AND MEMBERSHIP_STATUS = 'ACTIVE'
                """,
                this::mapMembership,
                conferenceId,
                reviewerId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<BiddingConferencePolicy> findBiddingPolicy(long conferenceId) {
        List<BiddingConferencePolicy> rows = jdbcTemplate.query(
                """
                SELECT C.CONFERENCE_ID, C.ORGANIZER_USER_ID, C.CONFERENCE_STATUS, P.BIDDING_CLOSE_AT
                FROM CONFERENCE C
                JOIN CONFERENCE_PHASE P ON P.CONFERENCE_ID = C.CONFERENCE_ID
                WHERE C.CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new BiddingConferencePolicy(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("ORGANIZER_USER_ID"),
                        rs.getString("CONFERENCE_STATUS"),
                        instant(rs.getTimestamp("BIDDING_CLOSE_AT"))
                ),
                conferenceId
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean manuscriptBelongsToConference(long conferenceId, long manuscriptId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MANUSCRIPT WHERE MANUSCRIPT_ID = ? AND CONFERENCE_ID = ?",
                Integer.class,
                manuscriptId,
                conferenceId
        );
        return count != null && count > 0;
    }

    @Override
    public List<ReviewerBiddingItem> listOpenBiddingItems(long conferenceId, long reviewerId) {
        List<BiddingItemRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       V.VERSION_ID,
                       V.TITLE,
                       V.ABSTRACT,
                       V.KEYWORDS,
                       B.BID_VALUE,
                       B.CONFLICT_DECLARED
                FROM CONFERENCE_REVIEWER CR
                JOIN CONFERENCE C ON C.CONFERENCE_ID = CR.CONFERENCE_ID
                JOIN CONFERENCE_PHASE P ON P.CONFERENCE_ID = C.CONFERENCE_ID
                JOIN MANUSCRIPT M ON M.CONFERENCE_ID = C.CONFERENCE_ID
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = M.CURRENT_VERSION_ID
                LEFT JOIN REVIEWER_BID B
                  ON B.CONFERENCE_ID = C.CONFERENCE_ID
                 AND B.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND B.REVIEWER_ID = CR.REVIEWER_ID
                WHERE CR.CONFERENCE_ID = ?
                  AND CR.REVIEWER_ID = ?
                  AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                  AND C.CONFERENCE_STATUS = 'BIDDING_OPEN'
                  AND P.BIDDING_CLOSE_AT > CURRENT_TIMESTAMP
                  AND M.CURRENT_STATUS IN ('SUBMITTED', 'UNDER_SCREENING')
                ORDER BY M.MANUSCRIPT_ID
                """,
                (rs, rowNum) -> new BiddingItemRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getString("BID_VALUE"),
                        rs.getInt("CONFLICT_DECLARED") == 1
                ),
                conferenceId,
                reviewerId
        );
        return rows.stream()
                .map(row -> deidentify(row, listIdentityTerms(row.manuscriptId(), row.versionId())))
                .toList();
    }

    @Override
    public ReviewerBidResponse saveBid(ReviewerBidDraft draft) {
        jdbcTemplate.update(
                """
                MERGE INTO REVIEWER_BID TARGET
                USING (
                  SELECT ? CONFERENCE_ID,
                         ? MANUSCRIPT_ID,
                         ? REVIEWER_ID,
                         ? BID_VALUE,
                         ? CONFLICT_DECLARED
                  FROM DUAL
                ) SOURCE
                ON (
                  TARGET.CONFERENCE_ID = SOURCE.CONFERENCE_ID
                  AND TARGET.MANUSCRIPT_ID = SOURCE.MANUSCRIPT_ID
                  AND TARGET.REVIEWER_ID = SOURCE.REVIEWER_ID
                )
                WHEN MATCHED THEN UPDATE SET
                  TARGET.BID_VALUE = SOURCE.BID_VALUE,
                  TARGET.CONFLICT_DECLARED = SOURCE.CONFLICT_DECLARED,
                  TARGET.BID_AT = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                  BID_ID,
                  CONFERENCE_ID,
                  MANUSCRIPT_ID,
                  REVIEWER_ID,
                  BID_VALUE,
                  CONFLICT_DECLARED,
                  BID_AT
                ) VALUES (
                  SEQ_REVIEWER_BID.NEXTVAL,
                  SOURCE.CONFERENCE_ID,
                  SOURCE.MANUSCRIPT_ID,
                  SOURCE.REVIEWER_ID,
                  SOURCE.BID_VALUE,
                  SOURCE.CONFLICT_DECLARED,
                  CURRENT_TIMESTAMP
                )
                """,
                draft.conferenceId(),
                draft.manuscriptId(),
                draft.reviewerId(),
                draft.bidValue(),
                draft.conflictDeclared() ? 1 : 0
        );
        return findBid(draft.conferenceId(), draft.manuscriptId(), draft.reviewerId())
                .orElseThrow(() -> new ConferenceNotFoundException("Reviewer bid was not found"));
    }

    @Override
    public boolean conflictExists(long manuscriptId, long reviewerId, String conflictType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM CONFLICT_CHECK_RECORD
                WHERE MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = ?
                  AND CONFLICT_TYPE = ?
                  AND SOURCE = 'SELF_DECLARED'
                """,
                Integer.class,
                manuscriptId,
                reviewerId,
                conflictType
        );
        return count != null && count > 0;
    }

    @Override
    public void insertSelfDeclaredConflict(long manuscriptId, long reviewerId, String conflictType, String conflictDescription) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_CHECK_RECORD (
                  CONFLICT_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REVIEWER_ID, CONFLICT_TYPE, CONFLICT_DESC,
                  SOURCE, DECLARED_BY, DECLARED_AT, DETECTED_AT, CONFIRMED_BY_CHAIR
                ) VALUES (
                  SEQ_CONFLICT_CHECK_RECORD.NEXTVAL, NULL, ?, ?, ?, ?, 'SELF_DECLARED',
                  ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
                )
                """,
                manuscriptId,
                reviewerId,
                conflictType,
                conflictDescription,
                reviewerId
        );
    }

    private Optional<ReviewerBidResponse> findBid(long conferenceId, long manuscriptId, long reviewerId) {
        List<ReviewerBidResponse> rows = jdbcTemplate.query(
                """
                SELECT BID_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID, BID_VALUE, CONFLICT_DECLARED, BID_AT
                FROM REVIEWER_BID
                WHERE CONFERENCE_ID = ? AND MANUSCRIPT_ID = ? AND REVIEWER_ID = ?
                """,
                (rs, rowNum) -> new ReviewerBidResponse(
                        rs.getLong("BID_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("BID_VALUE"),
                        rs.getInt("CONFLICT_DECLARED") == 1,
                        instant(rs.getTimestamp("BID_AT"))
                ),
                conferenceId,
                manuscriptId,
                reviewerId
        );
        return rows.stream().findFirst();
    }

    private ConferenceReviewerMembership mapMembership(ResultSet rs, int rowNum) throws SQLException {
        return new ConferenceReviewerMembership(
                rs.getLong("CONFERENCE_REVIEWER_ID"),
                rs.getLong("CONFERENCE_ID"),
                rs.getLong("REVIEWER_ID"),
                rs.getInt("MAX_LOAD"),
                rs.getString("MEMBERSHIP_STATUS"),
                fromJsonResearchAreas(rs.getString("RESEARCH_AREAS_JSON")),
                instant(rs.getTimestamp("JOINED_AT"))
        );
    }

    private List<String> listIdentityTerms(long manuscriptId, long versionId) {
        return jdbcTemplate.query(
                """
                SELECT AUTHOR_NAME, EMAIL, INSTITUTION
                FROM MANUSCRIPT_AUTHOR
                WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ?
                """,
                (rs, rowNum) -> List.of(rs.getString("AUTHOR_NAME"), rs.getString("EMAIL"), rs.getString("INSTITUTION")),
                manuscriptId,
                versionId
        ).stream()
                .flatMap(List::stream)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private ReviewerBiddingItem deidentify(BiddingItemRow row, List<String> identityTerms) {
        return new ReviewerBiddingItem(
                row.manuscriptId(),
                row.versionId(),
                redact(row.title(), identityTerms),
                redact(row.abstractText(), identityTerms),
                redact(row.keywords(), identityTerms),
                row.bidValue(),
                row.conflictDeclared()
        );
    }

    private String redact(String value, List<String> identityTerms) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = value;
        for (String term : identityTerms) {
            redacted = redacted.replace(term, "[redacted]");
        }
        return redacted;
    }

    private String toJson(List<ResearchAreaSnapshot> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Research area snapshot is invalid", ex);
        }
    }

    private List<ResearchAreaSnapshot> fromJsonResearchAreas(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ResearchAreaSnapshot>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record BiddingItemRow(
            long manuscriptId,
            long versionId,
            String title,
            String abstractText,
            String keywords,
            String bidValue,
            boolean conflictDeclared
    ) {
    }

    private record PlatformReviewerUserRow(
            long userId,
            String realName,
            String email,
            String institution
    ) {
    }
}
