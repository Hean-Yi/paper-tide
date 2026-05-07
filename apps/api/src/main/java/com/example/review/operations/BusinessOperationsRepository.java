package com.example.review.operations;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessOperationsRepository {
    private final JdbcTemplate jdbcTemplate;

    public BusinessOperationsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OperationsManuscriptRow> findManuscript(long manuscriptId) {
        List<OperationsManuscriptRow> rows = jdbcTemplate.query(
                """
                SELECT M.MANUSCRIPT_ID,
                       M.SUBMITTER_ID,
                       M.CONFERENCE_ID,
                       M.CURRENT_VERSION_ID,
                       M.CURRENT_STATUS,
                       C.ORGANIZER_USER_ID
                FROM MANUSCRIPT M
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = M.CONFERENCE_ID
                WHERE M.MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new OperationsManuscriptRow(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("SUBMITTER_ID"),
                        rs.getObject("CONFERENCE_ID", Long.class),
                        rs.getObject("CURRENT_VERSION_ID", Long.class),
                        rs.getString("CURRENT_STATUS"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<OperationsRoundRow> findRound(long roundId) {
        List<OperationsRoundRow> rows = jdbcTemplate.query(
                """
                SELECT R.ROUND_ID,
                       R.MANUSCRIPT_ID,
                       R.VERSION_ID,
                       M.CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ROUND R
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = R.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = M.CONFERENCE_ID
                WHERE R.ROUND_ID = ?
                """,
                (rs, rowNum) -> new OperationsRoundRow(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getObject("CONFERENCE_ID", Long.class),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<OperationsAssignmentRow> findAssignment(long assignmentId) {
        List<OperationsAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       M.CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = M.CONFERENCE_ID
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new OperationsAssignmentRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getObject("CONFERENCE_ID", Long.class),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                assignmentId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertDiscussionMessage(
            long roundId,
            Long assignmentId,
            long manuscriptId,
            long senderId,
            String senderRole,
            String messageScope,
            String messageText
    ) {
        long messageId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_DISCUSSION_MESSAGE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_DISCUSSION_MESSAGE (
                  MESSAGE_ID, ROUND_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, SENDER_ID,
                  SENDER_ROLE, MESSAGE_SCOPE, MESSAGE_TEXT, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                messageId,
                roundId,
                assignmentId,
                manuscriptId,
                senderId,
                senderRole,
                messageScope,
                messageText
        );
        return messageId;
    }

    public List<DiscussionMessageRow> listDiscussionMessages(long roundId, Long assignmentId) {
        if (assignmentId == null) {
            return jdbcTemplate.query(
                    """
                    SELECT MESSAGE_ID, ROUND_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, SENDER_ID,
                           SENDER_ROLE, MESSAGE_SCOPE, MESSAGE_TEXT, CREATED_AT
                    FROM REVIEW_DISCUSSION_MESSAGE
                    WHERE ROUND_ID = ?
                    ORDER BY CREATED_AT, MESSAGE_ID
                    """,
                    this::mapDiscussion,
                    roundId
            );
        }
        return jdbcTemplate.query(
                """
                SELECT MESSAGE_ID, ROUND_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, SENDER_ID,
                       SENDER_ROLE, MESSAGE_SCOPE, MESSAGE_TEXT, CREATED_AT
                FROM REVIEW_DISCUSSION_MESSAGE
                WHERE ROUND_ID = ?
                  AND (ASSIGNMENT_ID = ? OR MESSAGE_SCOPE IN ('ROUND', 'META_REVIEW'))
                ORDER BY CREATED_AT, MESSAGE_ID
                """,
                this::mapDiscussion,
                roundId,
                assignmentId
        );
    }

    public long upsertCameraReadySubmission(
            long manuscriptId,
            long versionId,
            long submittedBy,
            String fileName,
            long fileSize,
            boolean copyrightConfirmed,
            String licenseType
    ) {
        Long existingId = jdbcTemplate.query(
                "SELECT CAMERA_READY_ID FROM CAMERA_READY_SUBMISSION WHERE MANUSCRIPT_ID = ?",
                rs -> rs.next() ? rs.getLong("CAMERA_READY_ID") : null,
                manuscriptId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE CAMERA_READY_SUBMISSION
                    SET VERSION_ID = ?,
                        SUBMITTED_BY = ?,
                        FILE_NAME = ?,
                        FILE_SIZE = ?,
                        COPYRIGHT_CONFIRMED = ?,
                        LICENSE_TYPE = ?,
                        STATUS = 'SUBMITTED',
                        SUBMITTED_AT = CURRENT_TIMESTAMP
                    WHERE CAMERA_READY_ID = ?
                    """,
                    versionId,
                    submittedBy,
                    fileName,
                    fileSize,
                    copyrightConfirmed ? 1 : 0,
                    licenseType,
                    existingId
            );
            return existingId;
        }
        long cameraReadyId = jdbcTemplate.queryForObject("SELECT SEQ_CAMERA_READY_SUBMISSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CAMERA_READY_SUBMISSION (
                  CAMERA_READY_ID, MANUSCRIPT_ID, VERSION_ID, SUBMITTED_BY, FILE_NAME,
                  FILE_SIZE, COPYRIGHT_CONFIRMED, LICENSE_TYPE, STATUS, SUBMITTED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                cameraReadyId,
                manuscriptId,
                versionId,
                submittedBy,
                fileName,
                fileSize,
                copyrightConfirmed ? 1 : 0,
                licenseType
        );
        return cameraReadyId;
    }

    public Optional<CameraReadyRow> findCameraReady(long manuscriptId) {
        List<CameraReadyRow> rows = jdbcTemplate.query(
                """
                SELECT CAMERA_READY_ID, MANUSCRIPT_ID, VERSION_ID, SUBMITTED_BY, FILE_NAME,
                       FILE_SIZE, COPYRIGHT_CONFIRMED, LICENSE_TYPE, STATUS, SUBMITTED_AT, UPDATED_AT
                FROM CAMERA_READY_SUBMISSION
                WHERE MANUSCRIPT_ID = ?
                """,
                (rs, rowNum) -> new CameraReadyRow(
                        rs.getLong("CAMERA_READY_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("FILE_NAME"),
                        rs.getLong("FILE_SIZE"),
                        rs.getInt("COPYRIGHT_CONFIRMED") == 1,
                        rs.getString("LICENSE_TYPE"),
                        rs.getString("STATUS"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getTimestamp("UPDATED_AT")
                ),
                manuscriptId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void insertCommunicationLog(
            Long conferenceId,
            long manuscriptId,
            long recipientId,
            String templateKey,
            String subject,
            String bizType,
            long bizId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO COMMUNICATION_LOG (
                  COMMUNICATION_ID, CONFERENCE_ID, MANUSCRIPT_ID, RECIPIENT_ID,
                  CHANNEL, TEMPLATE_KEY, SUBJECT, DELIVERY_STATUS, BIZ_TYPE, BIZ_ID, SENT_AT, DETAIL_JSON
                ) VALUES (SEQ_COMMUNICATION_LOG.NEXTVAL, ?, ?, ?, 'IN_APP', ?, ?, 'SENT', ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                conferenceId,
                manuscriptId,
                recipientId,
                templateKey,
                subject,
                bizType,
                bizId
        );
    }

    public List<CommunicationLogRow> listCommunicationLogs(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT COMMUNICATION_ID, CONFERENCE_ID, MANUSCRIPT_ID, RECIPIENT_ID,
                       CHANNEL, TEMPLATE_KEY, SUBJECT, DELIVERY_STATUS, BIZ_TYPE, BIZ_ID, SENT_AT
                FROM COMMUNICATION_LOG
                WHERE CONFERENCE_ID = ?
                ORDER BY SENT_AT DESC, COMMUNICATION_ID DESC
                FETCH FIRST 100 ROWS ONLY
                """,
                (rs, rowNum) -> new CommunicationLogRow(
                        rs.getLong("COMMUNICATION_ID"),
                        rs.getObject("CONFERENCE_ID", Long.class),
                        rs.getObject("MANUSCRIPT_ID", Long.class),
                        rs.getLong("RECIPIENT_ID"),
                        rs.getString("CHANNEL"),
                        rs.getString("TEMPLATE_KEY"),
                        rs.getString("SUBJECT"),
                        rs.getString("DELIVERY_STATUS"),
                        rs.getString("BIZ_TYPE"),
                        rs.getObject("BIZ_ID", Long.class),
                        rs.getTimestamp("SENT_AT")
                ),
                conferenceId
        );
    }

    public GovernanceReportRow governanceReport(long conferenceId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT C.CONFERENCE_ID,
                       C.ORGANIZER_USER_ID,
                       (SELECT COUNT(*) FROM MANUSCRIPT M WHERE M.CONFERENCE_ID = C.CONFERENCE_ID) AS SUBMISSION_COUNT,
                       (SELECT COUNT(*) FROM MANUSCRIPT M WHERE M.CONFERENCE_ID = C.CONFERENCE_ID AND M.CURRENT_STATUS = 'ACCEPTED') AS ACCEPTED_COUNT,
                       (SELECT COUNT(*)
                        FROM REVIEW_ASSIGNMENT A
                        JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                        WHERE M.CONFERENCE_ID = C.CONFERENCE_ID) AS ASSIGNMENT_COUNT,
                       (SELECT COUNT(*)
                        FROM REVIEW_ASSIGNMENT A
                        JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                        WHERE M.CONFERENCE_ID = C.CONFERENCE_ID AND A.TASK_STATUS = 'SUBMITTED') AS SUBMITTED_REVIEW_COUNT,
                       (SELECT COUNT(*)
                        FROM REVIEW_ASSIGNMENT A
                        JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                        WHERE M.CONFERENCE_ID = C.CONFERENCE_ID
                          AND A.TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'OVERDUE')
                          AND COALESCE(A.DEADLINE_AT, CURRENT_TIMESTAMP + INTERVAL '1' DAY) < CURRENT_TIMESTAMP) AS OVERDUE_REVIEW_COUNT,
                       (SELECT COUNT(*)
                        FROM CONFLICT_CHECK_RECORD CCR
                        JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = CCR.MANUSCRIPT_ID
                        WHERE M.CONFERENCE_ID = C.CONFERENCE_ID) AS CONFLICT_COUNT,
                       (SELECT COUNT(*) FROM CAMERA_READY_SUBMISSION CRS
                        JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = CRS.MANUSCRIPT_ID
                        WHERE M.CONFERENCE_ID = C.CONFERENCE_ID) AS CAMERA_READY_COUNT
                FROM CONFERENCE C
                WHERE C.CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new GovernanceReportRow(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("ORGANIZER_USER_ID"),
                        rs.getLong("SUBMISSION_COUNT"),
                        rs.getLong("ACCEPTED_COUNT"),
                        rs.getLong("ASSIGNMENT_COUNT"),
                        rs.getLong("SUBMITTED_REVIEW_COUNT"),
                        rs.getLong("OVERDUE_REVIEW_COUNT"),
                        rs.getLong("CONFLICT_COUNT"),
                        rs.getLong("CAMERA_READY_COUNT")
                ),
                conferenceId
        );
    }

    private DiscussionMessageRow mapDiscussion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DiscussionMessageRow(
                rs.getLong("MESSAGE_ID"),
                rs.getLong("ROUND_ID"),
                rs.getObject("ASSIGNMENT_ID", Long.class),
                rs.getLong("MANUSCRIPT_ID"),
                rs.getLong("SENDER_ID"),
                rs.getString("SENDER_ROLE"),
                rs.getString("MESSAGE_SCOPE"),
                rs.getString("MESSAGE_TEXT"),
                rs.getTimestamp("CREATED_AT")
        );
    }
}

record OperationsManuscriptRow(
        long manuscriptId,
        long submitterId,
        Long conferenceId,
        Long currentVersionId,
        String currentStatus,
        Long organizerUserId
) {
}

record OperationsRoundRow(
        long roundId,
        long manuscriptId,
        long versionId,
        Long conferenceId,
        Long organizerUserId
) {
}

record OperationsAssignmentRow(
        long assignmentId,
        long roundId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        Long conferenceId,
        Long organizerUserId
) {
}

record DiscussionMessageRow(
        long messageId,
        long roundId,
        Long assignmentId,
        long manuscriptId,
        long senderId,
        String senderRole,
        String messageScope,
        String messageText,
        Timestamp createdAt
) {
}

record CameraReadyRow(
        long cameraReadyId,
        long manuscriptId,
        long versionId,
        long submittedBy,
        String fileName,
        long fileSize,
        boolean copyrightConfirmed,
        String licenseType,
        String status,
        Timestamp submittedAt,
        Timestamp updatedAt
) {
}

record CommunicationLogRow(
        long communicationId,
        Long conferenceId,
        Long manuscriptId,
        long recipientId,
        String channel,
        String templateKey,
        String subject,
        String deliveryStatus,
        String bizType,
        Long bizId,
        Timestamp sentAt
) {
}

record GovernanceReportRow(
        long conferenceId,
        long organizerUserId,
        long submissionCount,
        long acceptedCount,
        long assignmentCount,
        long submittedReviewCount,
        long overdueReviewCount,
        long conflictCount,
        long cameraReadyCount
) {
}
