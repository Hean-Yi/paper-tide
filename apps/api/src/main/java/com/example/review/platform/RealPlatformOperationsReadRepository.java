package com.example.review.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformOperationsReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public RealPlatformOperationsReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlatformConferenceAccessRow> findConferenceAccess(long conferenceId) {
        List<PlatformConferenceAccessRow> rows = jdbcTemplate.query(
                """
                SELECT CONFERENCE_ID, ORGANIZER_USER_ID
                FROM CONFERENCE
                WHERE CONFERENCE_ID = ?
                """,
                (rs, rowNum) -> new PlatformConferenceAccessRow(
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                conferenceId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<ReviewerInvitationOperationRow> listReviewerInvitations(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT INVITATION_ID, REVIEWER_ID, INVITATION_STATUS, INVITED_AT, EXPIRES_AT
                FROM REVIEWER_INVITATION
                WHERE CONFERENCE_ID = ?
                ORDER BY INVITED_AT DESC, INVITATION_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new ReviewerInvitationOperationRow(
                        rs.getLong("INVITATION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("INVITATION_STATUS"),
                        rs.getTimestamp("INVITED_AT"),
                        rs.getTimestamp("EXPIRES_AT")
                ),
                conferenceId
        );
    }

    public List<ExternalDelegationOperationRow> listExternalDelegations(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT D.DELEGATION_ID, D.ASSIGNMENT_ID, D.MANUSCRIPT_ID, D.EXTERNAL_NAME,
                       D.EXTERNAL_EMAIL, D.DELEGATION_STATUS, D.REQUESTED_AT
                FROM EXTERNAL_REVIEWER_DELEGATION D
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = D.MANUSCRIPT_ID
                WHERE M.CONFERENCE_ID = ?
                ORDER BY D.REQUESTED_AT DESC, D.DELEGATION_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new ExternalDelegationOperationRow(
                        rs.getLong("DELEGATION_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("EXTERNAL_NAME"),
                        rs.getString("EXTERNAL_EMAIL"),
                        rs.getString("DELEGATION_STATUS"),
                        rs.getTimestamp("REQUESTED_AT")
                ),
                conferenceId
        );
    }

    public List<ImportBatchOperationRow> listImportBatches(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT IMPORT_BATCH_ID, IMPORT_TYPE, BATCH_STATUS, ROW_COUNT, VALID_ROW_COUNT, ERROR_COUNT, CREATED_AT
                FROM IMPORT_BATCH
                WHERE CONFERENCE_ID = ?
                ORDER BY CREATED_AT DESC, IMPORT_BATCH_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new ImportBatchOperationRow(
                        rs.getLong("IMPORT_BATCH_ID"),
                        rs.getString("IMPORT_TYPE"),
                        rs.getString("BATCH_STATUS"),
                        rs.getInt("ROW_COUNT"),
                        rs.getInt("VALID_ROW_COUNT"),
                        rs.getInt("ERROR_COUNT"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    public List<AssignmentProposalOperationRow> listAssignmentProposals(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT B.BUNDLE_ID, B.ROUND_ID, B.MANUSCRIPT_ID, B.PROPOSAL_NAME, B.BUNDLE_STATUS,
                       COUNT(P.PROPOSAL_ID) AS PROPOSAL_COUNT, B.CREATED_AT
                FROM ASSIGNMENT_PROPOSAL_BUNDLE B
                LEFT JOIN ASSIGNMENT_PROPOSAL P ON P.BUNDLE_ID = B.BUNDLE_ID
                WHERE B.CONFERENCE_ID = ?
                GROUP BY B.BUNDLE_ID, B.ROUND_ID, B.MANUSCRIPT_ID, B.PROPOSAL_NAME, B.BUNDLE_STATUS, B.CREATED_AT
                ORDER BY B.CREATED_AT DESC, B.BUNDLE_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new AssignmentProposalOperationRow(
                        rs.getLong("BUNDLE_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("PROPOSAL_NAME"),
                        rs.getString("BUNDLE_STATUS"),
                        rs.getInt("PROPOSAL_COUNT"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    public List<MatchingScoreOperationRow> listMatchingScores(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT MATCHING_SCORE_ID, MANUSCRIPT_ID, REVIEWER_ID, SCORE_SOURCE,
                       MATCHING_SCORE, RATIONALE, IMPORTED_AT
                FROM REVIEWER_MATCHING_SCORE
                WHERE CONFERENCE_ID = ?
                ORDER BY IMPORTED_AT DESC, MATCHING_SCORE_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                this::mapMatchingScore,
                conferenceId
        );
    }

    public List<EmailTemplateOperationRow> listEmailTemplates(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT TEMPLATE_ID, ACTIVE_VERSION_ID, TEMPLATE_KEY, CREATED_AT
                FROM EMAIL_TEMPLATE
                WHERE CONFERENCE_ID = ?
                ORDER BY CREATED_AT DESC, TEMPLATE_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new EmailTemplateOperationRow(
                        rs.getLong("TEMPLATE_ID"),
                        rs.getObject("ACTIVE_VERSION_ID", Long.class),
                        rs.getString("TEMPLATE_KEY"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    public List<EmailHistoryOperationRow> listEmailHistory(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT H.EMAIL_HISTORY_ID, T.TEMPLATE_KEY, H.RECIPIENT_EMAIL, H.DELIVERY_STATUS, H.CREATED_AT
                FROM OUTBOUND_EMAIL_HISTORY H
                JOIN EMAIL_TEMPLATE T ON T.TEMPLATE_ID = H.TEMPLATE_ID
                WHERE H.CONFERENCE_ID = ?
                ORDER BY H.CREATED_AT DESC, H.EMAIL_HISTORY_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new EmailHistoryOperationRow(
                        rs.getLong("EMAIL_HISTORY_ID"),
                        rs.getString("TEMPLATE_KEY"),
                        rs.getString("RECIPIENT_EMAIL"),
                        rs.getString("DELIVERY_STATUS"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    public List<OfflineReviewImportOperationRow> listOfflineReviewImports(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT B.BATCH_ID, B.ASSIGNMENT_ID, B.REVIEWER_ID, B.BATCH_STATUS,
                       B.ROW_COUNT, B.VALID_ROW_COUNT, B.ERROR_COUNT, B.CREATED_AT
                FROM OFFLINE_REVIEW_IMPORT_BATCH B
                JOIN REVIEW_ASSIGNMENT A ON A.ASSIGNMENT_ID = B.ASSIGNMENT_ID
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = A.MANUSCRIPT_ID
                WHERE M.CONFERENCE_ID = ?
                ORDER BY B.CREATED_AT DESC, B.BATCH_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new OfflineReviewImportOperationRow(
                        rs.getLong("BATCH_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("BATCH_STATUS"),
                        rs.getInt("ROW_COUNT"),
                        rs.getInt("VALID_ROW_COUNT"),
                        rs.getInt("ERROR_COUNT"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    public List<CameraReadyFileOperationRow> listCameraReadyFiles(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT C.CAMERA_READY_FILE_ID, C.MANUSCRIPT_ID, C.FILE_NAME, C.FILE_SIZE,
                       C.FILE_STATUS, C.SUBMITTED_AT
                FROM CAMERA_READY_FILE C
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = C.MANUSCRIPT_ID
                WHERE M.CONFERENCE_ID = ?
                ORDER BY C.SUBMITTED_AT DESC, C.CAMERA_READY_FILE_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new CameraReadyFileOperationRow(
                        rs.getLong("CAMERA_READY_FILE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("FILE_NAME"),
                        rs.getLong("FILE_SIZE"),
                        rs.getString("FILE_STATUS"),
                        rs.getTimestamp("SUBMITTED_AT")
                ),
                conferenceId
        );
    }

    public List<PublicationMetadataOperationRow> listPublicationMetadata(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT PUBLICATION_METADATA_ID, MANUSCRIPT_ID, DOI, INDEX_KEYWORDS,
                       PUBLICATION_STATUS, UPDATED_AT
                FROM PUBLICATION_METADATA
                WHERE CONFERENCE_ID = ?
                ORDER BY UPDATED_AT DESC, PUBLICATION_METADATA_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new PublicationMetadataOperationRow(
                        rs.getLong("PUBLICATION_METADATA_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("DOI"),
                        rs.getString("INDEX_KEYWORDS"),
                        rs.getString("PUBLICATION_STATUS"),
                        rs.getTimestamp("UPDATED_AT")
                ),
                conferenceId
        );
    }

    public List<ProceedingsExportOperationRow> listProceedingsExports(long conferenceId) {
        return jdbcTemplate.query(
                """
                SELECT EXPORT_BATCH_ID, EXPORT_NAME, EXPORT_STATUS, PAPER_COUNT, CREATED_AT
                FROM PROCEEDINGS_EXPORT_BATCH
                WHERE CONFERENCE_ID = ?
                ORDER BY CREATED_AT DESC, EXPORT_BATCH_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (rs, rowNum) -> new ProceedingsExportOperationRow(
                        rs.getLong("EXPORT_BATCH_ID"),
                        rs.getString("EXPORT_NAME"),
                        rs.getString("EXPORT_STATUS"),
                        rs.getInt("PAPER_COUNT"),
                        rs.getTimestamp("CREATED_AT")
                ),
                conferenceId
        );
    }

    private MatchingScoreOperationRow mapMatchingScore(ResultSet rs, int rowNum) throws SQLException {
        return new MatchingScoreOperationRow(
                rs.getLong("MATCHING_SCORE_ID"),
                rs.getLong("MANUSCRIPT_ID"),
                rs.getLong("REVIEWER_ID"),
                rs.getString("SCORE_SOURCE"),
                rs.getDouble("MATCHING_SCORE"),
                rs.getString("RATIONALE"),
                rs.getTimestamp("IMPORTED_AT")
        );
    }
}
