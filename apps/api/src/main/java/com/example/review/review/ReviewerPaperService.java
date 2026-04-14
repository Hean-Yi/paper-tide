package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewerPaperService {
    private static final int RENDER_DPI = 144;
    private static final List<String> PAGE_ALLOWED_STATUSES = List.of("ACCEPTED", "IN_REVIEW", "OVERDUE", "SUBMITTED");

    private final JdbcTemplate jdbcTemplate;

    public ReviewerPaperService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReviewerPaperResponse getPaper(CurrentUserPrincipal principal, long assignmentId) {
        ReviewerPaperRow row = loadOwnedPaper(principal, assignmentId);
        return new ReviewerPaperResponse(
                row.assignmentId(),
                row.manuscriptId(),
                row.versionId(),
                row.title(),
                pageCount(row.pdfFile()),
                row.pdfFileName(),
                false
        );
    }

    public byte[] renderPage(CurrentUserPrincipal principal, long assignmentId, int pageNo) {
        ReviewerPaperRow row = loadOwnedPaper(principal, assignmentId);
        if (!PAGE_ALLOWED_STATUSES.contains(row.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accept the assignment before reading paper pages");
        }
        try (PDDocument document = Loader.loadPDF(row.pdfFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageNo < 1 || pageNo > pageCount) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper page not found");
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageNo - 1, RENDER_DPI);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to render paper page", ex);
        }
    }

    private ReviewerPaperRow loadOwnedPaper(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        List<ReviewerPaperRow> rows = jdbcTemplate.query(
                """
                SELECT A.ASSIGNMENT_ID,
                       A.MANUSCRIPT_ID,
                       A.VERSION_ID,
                       A.REVIEWER_ID,
                       A.TASK_STATUS,
                       V.TITLE,
                       V.PDF_FILE,
                       V.PDF_FILE_NAME
                FROM REVIEW_ASSIGNMENT A
                JOIN MANUSCRIPT_VERSION V ON V.VERSION_ID = A.VERSION_ID
                WHERE A.ASSIGNMENT_ID = ?
                """,
                (rs, rowNum) -> new ReviewerPaperRow(
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("TASK_STATUS"),
                        rs.getString("TITLE"),
                        rs.getBytes("PDF_FILE"),
                        rs.getString("PDF_FILE_NAME")
                ),
                assignmentId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review assignment not found");
        }
        ReviewerPaperRow row = rows.getFirst();
        if (row.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to read this paper");
        }
        if (row.pdfFile() == null || row.pdfFile().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found");
        }
        return row;
    }

    private int pageCount(byte[] pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to inspect paper PDF", ex);
        }
    }
}

record ReviewerPaperResponse(
        long assignmentId,
        long manuscriptId,
        long versionId,
        String title,
        int pageCount,
        String pdfFileName,
        boolean downloadAllowed
) {
}

record ReviewerPaperRow(
        long assignmentId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        String taskStatus,
        String title,
        byte[] pdfFile,
        String pdfFileName
) {
}
