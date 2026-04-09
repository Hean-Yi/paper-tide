package com.example.review.manuscript;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public VersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextVersionId() {
        return jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
    }

    public void insert(
            long versionId,
            long manuscriptId,
            int versionNo,
            String versionType,
            String title,
            String abstractText,
            String keywords,
            long submittedBy,
            Long sourceDecisionId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (
                  VERSION_ID,
                  MANUSCRIPT_ID,
                  VERSION_NO,
                  VERSION_TYPE,
                  TITLE,
                  ABSTRACT,
                  KEYWORDS,
                  PDF_FILE,
                  PDF_FILE_NAME,
                  PDF_FILE_SIZE,
                  SUBMITTED_BY,
                  SUBMITTED_AT,
                  SOURCE_DECISION_ID
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, CURRENT_TIMESTAMP, ?)
                """,
                versionId,
                manuscriptId,
                versionNo,
                versionType,
                title,
                abstractText,
                keywords,
                submittedBy,
                sourceDecisionId
        );
    }

    public Optional<VersionRow> findById(long versionId) {
        List<VersionRow> rows = jdbcTemplate.query(
                """
                SELECT VERSION_ID,
                       MANUSCRIPT_ID,
                       VERSION_NO,
                       VERSION_TYPE,
                       TITLE,
                       ABSTRACT,
                       KEYWORDS,
                       PDF_FILE,
                       PDF_FILE_NAME,
                       PDF_FILE_SIZE,
                       SUBMITTED_BY,
                       SUBMITTED_AT,
                       SOURCE_DECISION_ID
                FROM MANUSCRIPT_VERSION
                WHERE VERSION_ID = ?
                """,
                (rs, rowNum) -> {
                    return new VersionRow(
                            rs.getLong("VERSION_ID"),
                            rs.getLong("MANUSCRIPT_ID"),
                            rs.getInt("VERSION_NO"),
                            rs.getString("VERSION_TYPE"),
                            rs.getString("TITLE"),
                            rs.getString("ABSTRACT"),
                            rs.getString("KEYWORDS"),
                            rs.getBytes("PDF_FILE"),
                            rs.getString("PDF_FILE_NAME"),
                            rs.getObject("PDF_FILE_SIZE", Long.class),
                            rs.getLong("SUBMITTED_BY"),
                            rs.getTimestamp("SUBMITTED_AT")
                    );
                },
                versionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public int nextVersionNumber(long manuscriptId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(VERSION_NO), 0) FROM MANUSCRIPT_VERSION WHERE MANUSCRIPT_ID = ?",
                Integer.class,
                manuscriptId
        );
        return (max == null ? 0 : max) + 1;
    }

    public void updatePdf(long versionId, byte[] pdfBytes, String fileName, long fileSize) {
        jdbcTemplate.update(
                """
                UPDATE MANUSCRIPT_VERSION
                SET PDF_FILE = ?, PDF_FILE_NAME = ?, PDF_FILE_SIZE = ?
                WHERE VERSION_ID = ?
                """,
                pdfBytes,
                fileName,
                fileSize,
                versionId
        );
    }

    public void updateSubmittedAt(long versionId, Timestamp submittedAt) {
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT_VERSION SET SUBMITTED_AT = ? WHERE VERSION_ID = ?",
                submittedAt,
                versionId
        );
    }

    public List<VersionSummaryRow> listByManuscript(long manuscriptId) {
        return jdbcTemplate.query(
                """
                SELECT VERSION_ID, VERSION_NO, VERSION_TYPE, TITLE, SUBMITTED_AT, PDF_FILE_NAME, PDF_FILE_SIZE
                FROM MANUSCRIPT_VERSION
                WHERE MANUSCRIPT_ID = ?
                ORDER BY VERSION_NO
                """,
                (rs, rowNum) -> new VersionSummaryRow(
                        rs.getLong("VERSION_ID"),
                        rs.getInt("VERSION_NO"),
                        rs.getString("VERSION_TYPE"),
                        rs.getString("TITLE"),
                        rs.getTimestamp("SUBMITTED_AT"),
                        rs.getString("PDF_FILE_NAME"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                ),
                manuscriptId
        );
    }
}

record VersionRow(
        long versionId,
        long manuscriptId,
        int versionNo,
        String versionType,
        String title,
        String abstractText,
        String keywords,
        byte[] pdfFile,
        String pdfFileName,
        Long pdfFileSize,
        long submittedBy,
        Timestamp submittedAt
) {
}

record VersionSummaryRow(
        long versionId,
        int versionNo,
        String versionType,
        String title,
        Timestamp submittedAt,
        String pdfFileName,
        Long pdfFileSize
) {
}
