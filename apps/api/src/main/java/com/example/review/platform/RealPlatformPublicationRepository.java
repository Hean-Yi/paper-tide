package com.example.review.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformPublicationRepository extends RealPlatformRepository {
    public RealPlatformPublicationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        super(jdbcTemplate, objectMapper);
    }

    public long insertEmailTemplate(long conferenceId, String templateKey, long createdBy) {
        long templateId = jdbcTemplate.queryForObject("SELECT SEQ_EMAIL_TEMPLATE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EMAIL_TEMPLATE (
                  TEMPLATE_ID, CONFERENCE_ID, TEMPLATE_KEY, ACTIVE_VERSION_ID, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
                """,
                templateId,
                conferenceId,
                templateKey,
                createdBy
        );
        return templateId;
    }

    public long insertEmailTemplateVersion(long templateId, String subjectTemplate, String bodyTemplate, long createdBy) {
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(VERSION_NO), 0) + 1 FROM EMAIL_TEMPLATE_VERSION WHERE TEMPLATE_ID = ?",
                Integer.class,
                templateId
        );
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_EMAIL_TEMPLATE_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EMAIL_TEMPLATE_VERSION (
                  TEMPLATE_VERSION_ID, TEMPLATE_ID, VERSION_NO, SUBJECT_TEMPLATE, BODY_TEMPLATE, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                versionId,
                templateId,
                nextVersion,
                subjectTemplate,
                bodyTemplate,
                createdBy
        );
        jdbcTemplate.update("UPDATE EMAIL_TEMPLATE SET ACTIVE_VERSION_ID = ? WHERE TEMPLATE_ID = ?", versionId, templateId);
        return versionId;
    }

    public Optional<PlatformEmailTemplateRow> findEmailTemplate(long templateId) {
        List<PlatformEmailTemplateRow> rows = jdbcTemplate.query(
                """
                SELECT T.TEMPLATE_ID,
                       T.CONFERENCE_ID,
                       T.TEMPLATE_KEY,
                       V.TEMPLATE_VERSION_ID,
                       V.SUBJECT_TEMPLATE,
                       V.BODY_TEMPLATE,
                       C.ORGANIZER_USER_ID
                FROM EMAIL_TEMPLATE T
                JOIN EMAIL_TEMPLATE_VERSION V ON V.TEMPLATE_VERSION_ID = T.ACTIVE_VERSION_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = T.CONFERENCE_ID
                WHERE T.TEMPLATE_ID = ?
                """,
                (rs, rowNum) -> new PlatformEmailTemplateRow(
                        rs.getLong("TEMPLATE_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("TEMPLATE_KEY"),
                        rs.getLong("TEMPLATE_VERSION_ID"),
                        rs.getString("SUBJECT_TEMPLATE"),
                        rs.getString("BODY_TEMPLATE"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                templateId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertOutboundEmailHistory(
            PlatformEmailTemplateRow template,
            String recipientEmail,
            String subjectText,
            String bodyText,
            long createdBy
    ) {
        long historyId = jdbcTemplate.queryForObject("SELECT SEQ_OUTBOUND_EMAIL_HISTORY.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OUTBOUND_EMAIL_HISTORY (
                  EMAIL_HISTORY_ID, CONFERENCE_ID, TEMPLATE_ID, TEMPLATE_VERSION_ID, RECIPIENT_EMAIL,
                  SUBJECT_TEXT, BODY_TEXT, DELIVERY_STATUS, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'RECORDED', ?, CURRENT_TIMESTAMP)
                """,
                historyId,
                template.conferenceId(),
                template.templateId(),
                template.templateVersionId(),
                recipientEmail,
                subjectText,
                bodyText,
                createdBy
        );
        return historyId;
    }

    public long insertOfflineReviewImportBatch(long assignmentId, long reviewerId, int rowCount, int validRowCount, int errorCount) {
        long batchId = jdbcTemplate.queryForObject("SELECT SEQ_OFFLINE_REVIEW_IMPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OFFLINE_REVIEW_IMPORT_BATCH (
                  BATCH_ID, ASSIGNMENT_ID, REVIEWER_ID, BATCH_STATUS, ROW_COUNT,
                  VALID_ROW_COUNT, ERROR_COUNT, CREATED_AT, APPLIED_AT
                ) VALUES (?, ?, ?, 'PREVIEWED', ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                batchId,
                assignmentId,
                reviewerId,
                rowCount,
                validRowCount,
                errorCount
        );
        return batchId;
    }

    public long insertOfflineReviewImportRow(
            long batchId,
            int rowNo,
            String rowStatus,
            Integer overallScore,
            String recommendation,
            String commentsToAuthor,
            String errorMessage
    ) {
        long rowId = jdbcTemplate.queryForObject("SELECT SEQ_OFFLINE_REVIEW_IMPORT_ROW.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO OFFLINE_REVIEW_IMPORT_ROW (
                  ROW_ID, BATCH_ID, ROW_NO, ROW_STATUS, OVERALL_SCORE,
                  RECOMMENDATION, COMMENTS_TO_AUTHOR, ERROR_MESSAGE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rowId,
                batchId,
                rowNo,
                rowStatus,
                overallScore,
                recommendation,
                commentsToAuthor,
                errorMessage
        );
        return rowId;
    }

    public Optional<PlatformOfflineReviewBatchRow> findOfflineReviewImportBatch(long batchId) {
        List<PlatformOfflineReviewBatchRow> rows = jdbcTemplate.query(
                """
                SELECT B.BATCH_ID,
                       B.ASSIGNMENT_ID,
                       B.REVIEWER_ID,
                       B.BATCH_STATUS,
                       A.ROUND_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID
                FROM OFFLINE_REVIEW_IMPORT_BATCH B
                JOIN REVIEW_ASSIGNMENT A ON A.ASSIGNMENT_ID = B.ASSIGNMENT_ID
                WHERE B.BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformOfflineReviewBatchRow(
                        rs.getLong("BATCH_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("BATCH_STATUS"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID")
                ),
                batchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformOfflineReviewRow> listValidOfflineReviewRows(long batchId) {
        return jdbcTemplate.query(
                """
                SELECT ROW_ID, OVERALL_SCORE, RECOMMENDATION, COMMENTS_TO_AUTHOR
                FROM OFFLINE_REVIEW_IMPORT_ROW
                WHERE BATCH_ID = ?
                  AND ROW_STATUS = 'VALID'
                ORDER BY ROW_NO, ROW_ID
                """,
                (rs, rowNum) -> new PlatformOfflineReviewRow(
                        rs.getLong("ROW_ID"),
                        rs.getInt("OVERALL_SCORE"),
                        rs.getString("RECOMMENDATION"),
                        rs.getString("COMMENTS_TO_AUTHOR")
                ),
                batchId
        );
    }

    public void insertReviewReportFromOfflineRow(PlatformOfflineReviewBatchRow batch, PlatformOfflineReviewRow row) {
        long reviewId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_REPORT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_REPORT (
                  REVIEW_ID, ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  NOVELTY_SCORE, METHOD_SCORE, EXPERIMENT_SCORE, WRITING_SCORE, OVERALL_SCORE,
                  CONFIDENCE_LEVEL, STRENGTHS, WEAKNESSES, COMMENTS_TO_AUTHOR, COMMENTS_TO_CHAIR,
                  RECOMMENDATION, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MEDIUM', NULL, NULL, ?, NULL, ?, CURRENT_TIMESTAMP)
                """,
                reviewId,
                batch.assignmentId(),
                batch.roundId(),
                batch.manuscriptId(),
                batch.reviewerId(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.overallScore(),
                row.commentsToAuthor(),
                row.recommendation()
        );
        jdbcTemplate.update(
                "UPDATE REVIEW_ASSIGNMENT SET TASK_STATUS = 'SUBMITTED', SUBMITTED_AT = CURRENT_TIMESTAMP WHERE ASSIGNMENT_ID = ?",
                batch.assignmentId()
        );
    }

    public void markOfflineReviewRowApplied(long rowId) {
        jdbcTemplate.update("UPDATE OFFLINE_REVIEW_IMPORT_ROW SET ROW_STATUS = 'APPLIED' WHERE ROW_ID = ?", rowId);
    }

    public void markOfflineReviewBatchApplied(long batchId) {
        jdbcTemplate.update(
                "UPDATE OFFLINE_REVIEW_IMPORT_BATCH SET BATCH_STATUS = 'APPLIED', APPLIED_AT = CURRENT_TIMESTAMP WHERE BATCH_ID = ?",
                batchId
        );
    }

    public long insertCameraReadyFile(
            long manuscriptId,
            long versionId,
            long submittedBy,
            String fileName,
            long fileSize,
            String checksumSha256,
            boolean copyrightConfirmed,
            String licenseType
    ) {
        long fileId = jdbcTemplate.queryForObject("SELECT SEQ_CAMERA_READY_FILE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CAMERA_READY_FILE (
                  CAMERA_READY_FILE_ID, MANUSCRIPT_ID, VERSION_ID, SUBMITTED_BY, FILE_NAME,
                  FILE_SIZE, CHECKSUM_SHA256, COPYRIGHT_CONFIRMED, LICENSE_TYPE, FILE_STATUS,
                  DECISION_NOTE, SUBMITTED_AT, DECIDED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', NULL, CURRENT_TIMESTAMP, NULL)
                """,
                fileId,
                manuscriptId,
                versionId,
                submittedBy,
                fileName,
                fileSize,
                checksumSha256,
                copyrightConfirmed ? 1 : 0,
                licenseType
        );
        return fileId;
    }

    public Optional<PlatformCameraReadyFileRow> findCameraReadyFile(long fileId) {
        List<PlatformCameraReadyFileRow> rows = jdbcTemplate.query(
                """
                SELECT F.CAMERA_READY_FILE_ID,
                       F.MANUSCRIPT_ID,
                       F.VERSION_ID,
                       F.SUBMITTED_BY,
                       F.FILE_STATUS,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM CAMERA_READY_FILE F
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = F.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE F.CAMERA_READY_FILE_ID = ?
                """,
                (rs, rowNum) -> new PlatformCameraReadyFileRow(
                        rs.getLong("CAMERA_READY_FILE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("FILE_STATUS"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                fileId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void decideCameraReadyFile(long fileId, String fileStatus, String decisionNote) {
        jdbcTemplate.update(
                """
                UPDATE CAMERA_READY_FILE
                SET FILE_STATUS = ?,
                    DECISION_NOTE = ?,
                    DECIDED_AT = CURRENT_TIMESTAMP
                WHERE CAMERA_READY_FILE_ID = ?
                """,
                fileStatus,
                decisionNote,
                fileId
        );
    }

    public long upsertPublicationMetadata(
            long conferenceId,
            long manuscriptId,
            String doi,
            String indexKeywords,
            String publicationStatus,
            long updatedBy
    ) {
        Long existingId = jdbcTemplate.query(
                "SELECT PUBLICATION_METADATA_ID FROM PUBLICATION_METADATA WHERE MANUSCRIPT_ID = ?",
                rs -> rs.next() ? rs.getLong("PUBLICATION_METADATA_ID") : null,
                manuscriptId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE PUBLICATION_METADATA
                    SET CONFERENCE_ID = ?,
                        DOI = ?,
                        INDEX_KEYWORDS = ?,
                        PUBLICATION_STATUS = ?,
                        UPDATED_BY = ?,
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE PUBLICATION_METADATA_ID = ?
                    """,
                    conferenceId,
                    doi,
                    indexKeywords,
                    publicationStatus,
                    updatedBy,
                    existingId
            );
            return existingId;
        }
        long metadataId = jdbcTemplate.queryForObject("SELECT SEQ_PUBLICATION_METADATA.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PUBLICATION_METADATA (
                  PUBLICATION_METADATA_ID, CONFERENCE_ID, MANUSCRIPT_ID, DOI,
                  INDEX_KEYWORDS, PUBLICATION_STATUS, UPDATED_BY, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                metadataId,
                conferenceId,
                manuscriptId,
                doi,
                indexKeywords,
                publicationStatus,
                updatedBy
        );
        return metadataId;
    }

    public int countProceedingsReadyPapers(long conferenceId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM MANUSCRIPT M
                JOIN PUBLICATION_METADATA P ON P.MANUSCRIPT_ID = M.MANUSCRIPT_ID
                WHERE COALESCE(M.CONFERENCE_ID, 0) = ?
                  AND M.CURRENT_STATUS = 'ACCEPTED'
                  AND P.PUBLICATION_STATUS = 'READY_FOR_PROCEEDINGS'
                """,
                Integer.class,
                conferenceId
        );
        return count == null ? 0 : count;
    }

    public long insertProceedingsExportPreview(long conferenceId, String exportName, int paperCount, String previewJson, long createdBy) {
        long exportBatchId = jdbcTemplate.queryForObject("SELECT SEQ_PROCEEDINGS_EXPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO PROCEEDINGS_EXPORT_BATCH (
                  EXPORT_BATCH_ID, CONFERENCE_ID, EXPORT_NAME, EXPORT_STATUS, PAPER_COUNT,
                  PREVIEW_JSON, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, 'PREVIEWED', ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                exportBatchId,
                conferenceId,
                exportName,
                paperCount,
                previewJson,
                createdBy
        );
        return exportBatchId;
    }

    public Optional<PlatformProceedingsExportBatchRow> findProceedingsExportBatch(long exportBatchId) {
        List<PlatformProceedingsExportBatchRow> rows = jdbcTemplate.query(
                """
                SELECT E.EXPORT_BATCH_ID, E.CONFERENCE_ID, E.EXPORT_NAME, E.EXPORT_STATUS, E.PAPER_COUNT, C.ORGANIZER_USER_ID
                FROM PROCEEDINGS_EXPORT_BATCH E
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = E.CONFERENCE_ID
                WHERE E.EXPORT_BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformProceedingsExportBatchRow(
                        rs.getLong("EXPORT_BATCH_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("EXPORT_NAME"),
                        rs.getString("EXPORT_STATUS"),
                        rs.getInt("PAPER_COUNT"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                exportBatchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void markProceedingsExported(long exportBatchId) {
        jdbcTemplate.update(
                """
                UPDATE PROCEEDINGS_EXPORT_BATCH
                SET EXPORT_STATUS = 'EXPORTED'
                WHERE EXPORT_BATCH_ID = ?
                  AND EXPORT_STATUS = 'PREVIEWED'
                """,
                exportBatchId
        );
    }

    public void markProceedingsPublicationMetadataExported(long conferenceId) {
        jdbcTemplate.update(
                """
                UPDATE PUBLICATION_METADATA
                SET PUBLICATION_STATUS = 'EXPORTED',
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE CONFERENCE_ID = ?
                  AND PUBLICATION_STATUS = 'READY_FOR_PROCEEDINGS'
                """,
                conferenceId
        );
    }
}
