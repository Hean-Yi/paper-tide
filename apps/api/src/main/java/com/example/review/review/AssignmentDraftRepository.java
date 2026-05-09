package com.example.review.review;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentDraftRepository {
    private static final String ACTIVE_ASSIGNMENT_STATUSES =
            "'ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE'";

    private final JdbcTemplate jdbcTemplate;

    public AssignmentDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssignmentCandidateRow> listCandidates(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       CR.REVIEWER_ID,
                       CR.MAX_LOAD,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM REVIEW_ASSIGNMENT A
                         JOIN MANUSCRIPT AM ON AM.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                         WHERE A.REVIEWER_ID = CR.REVIEWER_ID
                           AND A.TASK_STATUS IN (%s)
                           AND AM.CONFERENCE_ID = M.CONFERENCE_ID
                       ), 0) AS CURRENT_LOAD,
                       COALESCE(B.BID_VALUE, 'NEUTRAL') AS BID_VALUE
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                JOIN CONFERENCE_REVIEWER CR
                  ON CR.CONFERENCE_ID = M.CONFERENCE_ID
                 AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                LEFT JOIN REVIEWER_BID B
                  ON B.CONFERENCE_ID = M.CONFERENCE_ID
                 AND B.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                 AND B.REVIEWER_ID = CR.REVIEWER_ID
                WHERE R.ROUND_ID = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM MANUSCRIPT_AUTHOR MA
                    WHERE MA.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                      AND MA.USER_ID = CR.REVIEWER_ID
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM CONFLICT_CHECK_RECORD C
                    WHERE C.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                      AND C.REVIEWER_ID = CR.REVIEWER_ID
                  )
                  AND COALESCE(B.BID_VALUE, 'NEUTRAL') <> 'DECLINE'
                  AND (
                    SELECT COUNT(*)
                    FROM REVIEW_ASSIGNMENT A
                    JOIN MANUSCRIPT AM ON AM.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                    WHERE A.REVIEWER_ID = CR.REVIEWER_ID
                      AND A.TASK_STATUS IN (%s)
                      AND AM.CONFERENCE_ID = M.CONFERENCE_ID
                  ) < CR.MAX_LOAD
                ORDER BY
                  CASE COALESCE(B.BID_VALUE, 'NEUTRAL')
                    WHEN 'WANT_TO_REVIEW' THEN 1
                    WHEN 'NEUTRAL' THEN 2
                    ELSE 3
                  END,
                  CURRENT_LOAD ASC,
                  CR.REVIEWER_ID ASC
                """.formatted(ACTIVE_ASSIGNMENT_STATUSES, ACTIVE_ASSIGNMENT_STATUSES),
                (rs, rowNum) -> new AssignmentCandidateRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("CURRENT_LOAD"),
                        rs.getInt("MAX_LOAD"),
                        rs.getString("BID_VALUE")
                ),
                roundId
        );
    }

    public void clearOpenDrafts(long roundId) {
        jdbcTemplate.update(
                "DELETE FROM ASSIGNMENT_DRAFT WHERE ROUND_ID = ? AND DRAFT_STATUS <> 'CONFIRMED'",
                roundId
        );
    }

    public void insertDraft(AssignmentDraftInsert draft) {
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_DRAFT (
                  ASSIGNMENT_DRAFT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID,
                  RANK_ORDER, SCORE, CURRENT_LOAD, MAX_LOAD, BID_VALUE, REASON, DRAFT_STATUS,
                  CREATED_BY, CREATED_AT, UPDATED_AT
                ) VALUES (
                  SEQ_ASSIGNMENT_DRAFT.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROPOSED',
                  ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                draft.roundId(),
                draft.manuscriptId(),
                draft.versionId(),
                draft.reviewerId(),
                draft.rankOrder(),
                draft.score(),
                draft.currentLoad(),
                draft.maxLoad(),
                draft.bidValue(),
                draft.reason(),
                draft.createdBy()
        );
    }

    public List<AssignmentDraftRow> listByRound(long roundId) {
        return jdbcTemplate.query(
                """
                SELECT ASSIGNMENT_DRAFT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID,
                       RANK_ORDER, SCORE, CURRENT_LOAD, MAX_LOAD, BID_VALUE, REASON, DRAFT_STATUS
                FROM ASSIGNMENT_DRAFT
                WHERE ROUND_ID = ?
                ORDER BY RANK_ORDER, ASSIGNMENT_DRAFT_ID
                """,
                this::mapDraft,
                roundId
        );
    }

    public List<AssignmentDraftRow> listOpenByConference(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT D.ASSIGNMENT_DRAFT_ID, D.ROUND_ID, D.MANUSCRIPT_ID, D.VERSION_ID, D.REVIEWER_ID,
                       D.RANK_ORDER, D.SCORE, D.CURRENT_LOAD, D.MAX_LOAD, D.BID_VALUE, D.REASON, D.DRAFT_STATUS
                FROM ASSIGNMENT_DRAFT D
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = D.MANUSCRIPT_ID
                WHERE COALESCE(M.CONFERENCE_ID, 0) = ?
                  AND D.DRAFT_STATUS = 'PROPOSED'
                ORDER BY D.MANUSCRIPT_ID, D.RANK_ORDER, D.ASSIGNMENT_DRAFT_ID
                """,
                this::mapDraft,
                conferenceId
        );
    }

    public List<AssignmentDraftRow> findOpenByConferenceForUpdate(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT D.ASSIGNMENT_DRAFT_ID, D.ROUND_ID, D.MANUSCRIPT_ID, D.VERSION_ID, D.REVIEWER_ID,
                       D.RANK_ORDER, D.SCORE, D.CURRENT_LOAD, D.MAX_LOAD, D.BID_VALUE, D.REASON, D.DRAFT_STATUS
                FROM ASSIGNMENT_DRAFT D
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = D.MANUSCRIPT_ID
                WHERE COALESCE(M.CONFERENCE_ID, 0) = ?
                  AND D.DRAFT_STATUS = 'PROPOSED'
                ORDER BY D.MANUSCRIPT_ID, D.RANK_ORDER, D.ASSIGNMENT_DRAFT_ID
                FOR UPDATE
                """,
                this::mapDraft,
                conferenceId
        );
    }

    public List<AssignmentDraftRow> findDraftsForUpdate(long roundId, List<Long> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", draftIds.stream().map(id -> "?").toList());
        Object[] args = new Object[draftIds.size() + 1];
        args[0] = roundId;
        for (int i = 0; i < draftIds.size(); i++) {
            args[i + 1] = draftIds.get(i);
        }
        return jdbcTemplate.query(
                """
                SELECT ASSIGNMENT_DRAFT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID,
                       RANK_ORDER, SCORE, CURRENT_LOAD, MAX_LOAD, BID_VALUE, REASON, DRAFT_STATUS
                FROM ASSIGNMENT_DRAFT
                WHERE ROUND_ID = ?
                  AND ASSIGNMENT_DRAFT_ID IN (%s)
                FOR UPDATE
                """.formatted(placeholders),
                this::mapDraft,
                args
        );
    }

    public ConferenceReviewerLockRow lockConferenceReviewer(long manuscriptId, long reviewerId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT CR.CONFERENCE_REVIEWER_ID, CR.CONFERENCE_ID, CR.REVIEWER_ID, CR.MAX_LOAD
                FROM MANUSCRIPT M
                JOIN CONFERENCE_REVIEWER CR
                  ON CR.CONFERENCE_ID = M.CONFERENCE_ID
                 AND CR.REVIEWER_ID = ?
                 AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                WHERE M.MANUSCRIPT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ConferenceReviewerLockRow(
                        rs.getLong("CONFERENCE_REVIEWER_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("MAX_LOAD")
                ),
                reviewerId,
                manuscriptId
        );
    }

    public int currentLoad(long conferenceId, long reviewerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                WHERE A.REVIEWER_ID = ?
                  AND A.TASK_STATUS IN (%s)
                  AND M.CONFERENCE_ID = ?
                """.formatted(ACTIVE_ASSIGNMENT_STATUSES),
                Integer.class,
                reviewerId,
                conferenceId
        );
        return count == null ? 0 : count;
    }

    public void markConfirmed(long draftId) {
        jdbcTemplate.update(
                """
                UPDATE ASSIGNMENT_DRAFT
                SET DRAFT_STATUS = 'CONFIRMED',
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ASSIGNMENT_DRAFT_ID = ?
                """,
                draftId
        );
    }

    public boolean assignmentExists(long roundId, long reviewerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEW_ASSIGNMENT WHERE ROUND_ID = ? AND REVIEWER_ID = ?",
                Integer.class,
                roundId,
                reviewerId
        );
        return count != null && count > 0;
    }

    private AssignmentDraftRow mapDraft(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AssignmentDraftRow(
                rs.getLong("ASSIGNMENT_DRAFT_ID"),
                rs.getLong("ROUND_ID"),
                rs.getLong("MANUSCRIPT_ID"),
                rs.getLong("VERSION_ID"),
                rs.getLong("REVIEWER_ID"),
                rs.getInt("RANK_ORDER"),
                rs.getInt("SCORE"),
                rs.getInt("CURRENT_LOAD"),
                rs.getInt("MAX_LOAD"),
                rs.getString("BID_VALUE"),
                rs.getString("REASON"),
                rs.getString("DRAFT_STATUS")
        );
    }
}

record AssignmentCandidateRow(
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int currentLoad,
        int maxLoad,
        String bidValue
) {
}

record AssignmentDraftInsert(
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int rankOrder,
        int score,
        int currentLoad,
        int maxLoad,
        String bidValue,
        String reason,
        long createdBy
) {
}

record AssignmentDraftRow(
        long draftId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        int rankOrder,
        int score,
        int currentLoad,
        int maxLoad,
        String bidValue,
        String reason,
        String draftStatus
) {
}

record ConferenceReviewerLockRow(
        long conferenceReviewerId,
        long conferenceId,
        long reviewerId,
        int maxLoad
) {
}
