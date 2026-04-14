package com.example.review.manuscript;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManuscriptService {
    private static final long MAX_PDF_BYTES = 50L * 1024L * 1024L;
    private static final Set<String> VALID_BLIND_MODES = Set.of("DOUBLE_BLIND", "SINGLE_BLIND", "OPEN");
    private static final Set<String> DRAFT_STATUSES = Set.of("DRAFT", "REVISION_REQUIRED");
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ManuscriptRepository manuscriptRepository;
    private final VersionRepository versionRepository;
    private final AuthorRepository authorRepository;

    public ManuscriptService(
            ManuscriptRepository manuscriptRepository,
            VersionRepository versionRepository,
            AuthorRepository authorRepository
    ) {
        this.manuscriptRepository = manuscriptRepository;
        this.versionRepository = versionRepository;
        this.authorRepository = authorRepository;
    }

    @Transactional
    public ManuscriptResponse createManuscript(CurrentUserPrincipal principal, CreateManuscriptRequest request) {
        RoleGuard.requireRole(principal, "AUTHOR");
        validateBlindMode(request.blindMode());
        validateAuthors(request.authors());

        long manuscriptId = manuscriptRepository.nextManuscriptId();
        long versionId = versionRepository.nextVersionId();

        manuscriptRepository.insert(manuscriptId, principal.userId(), request.blindMode());
        versionRepository.insert(
                versionId,
                manuscriptId,
                1,
                "INITIAL",
                request.title(),
                request.abstractText(),
                request.keywords(),
                principal.userId(),
                null
        );
        authorRepository.insertAuthors(manuscriptId, versionId, request.authors());
        manuscriptRepository.updateCurrentVersion(manuscriptId, versionId);

        return getManuscript(principal, manuscriptId);
    }

    @Transactional
    public ManuscriptResponse createRevision(CurrentUserPrincipal principal, long manuscriptId, CreateVersionRequest request) {
        RoleGuard.requireRole(principal, "AUTHOR");
        validateAuthors(request.authors());

        ManuscriptRow manuscript = findOwnedManuscriptForUpdate(principal, manuscriptId);
        if (!"REVISION_REQUIRED".equals(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revision can only be created from REVISION_REQUIRED");
        }

        int nextVersionNo = versionRepository.nextVersionNumber(manuscriptId);
        long versionId = versionRepository.nextVersionId();
        versionRepository.insert(
                versionId,
                manuscriptId,
                nextVersionNo,
                "REVISION",
                request.title(),
                request.abstractText(),
                request.keywords(),
                principal.userId(),
                null
        );
        authorRepository.insertAuthors(manuscriptId, versionId, request.authors());
        manuscriptRepository.updateCurrentVersion(manuscriptId, versionId);

        return getManuscript(principal, manuscriptId);
    }

    @Transactional
    public void uploadPdf(CurrentUserPrincipal principal, long manuscriptId, long versionId, MultipartFile file) {
        RoleGuard.requireRole(principal, "AUTHOR");
        ManuscriptRow manuscript = findOwnedManuscriptForUpdate(principal, manuscriptId);
        VersionRow version = findVersion(versionId);
        ensureVersionBelongsToManuscript(version, manuscriptId);

        if (!isPdf(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF uploads are supported");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF uploads must be 50MB or smaller");
        }
        if (manuscript.currentVersionId() == null || manuscript.currentVersionId() != versionId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PDF upload is allowed only for the current version");
        }
        if (!DRAFT_STATUSES.contains(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted versions cannot replace their PDF");
        }

        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read PDF upload", ex);
        }
        if (!hasPdfMagic(pdfBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is not a valid PDF");
        }
        versionRepository.updatePdf(versionId, pdfBytes, file.getOriginalFilename(), file.getSize());
    }

    public PdfPayload downloadPdf(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
        ManuscriptRow manuscript = manuscriptRepository.findById(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        VersionRow version = findVersion(versionId);
        ensureVersionBelongsToManuscript(version, manuscriptId);
        if (!canDownloadPdf(principal, manuscript, version)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden manuscript PDF access");
        }
        if (version.pdfFile() == null || version.pdfFile().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found");
        }
        return new PdfPayload(
                version.pdfFile(),
                version.pdfFileName() == null ? "manuscript.pdf" : version.pdfFileName()
        );
    }

    @Transactional
    public ManuscriptResponse submitVersion(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
        RoleGuard.requireRole(principal, "AUTHOR");
        ManuscriptRow manuscript = findOwnedManuscriptForUpdate(principal, manuscriptId);
        VersionRow version = findVersion(versionId);
        ensureVersionBelongsToManuscript(version, manuscriptId);

        if (manuscript.currentVersionId() == null || manuscript.currentVersionId() != versionId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the current version can be submitted");
        }
        if (version.pdfFile() == null || version.pdfFile().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before submission");
        }

        String nextStatus = switch (manuscript.currentStatus()) {
            case "DRAFT" -> "SUBMITTED";
            case "REVISION_REQUIRED" -> "REVISED_SUBMITTED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid manuscript state for submission");
        };

        Timestamp now = Timestamp.from(Instant.now());
        manuscriptRepository.updateStatusAndSubmittedAt(manuscriptId, nextStatus, now);
        versionRepository.updateSubmittedAt(versionId, now);

        return getManuscript(principal, manuscriptId);
    }

    @Transactional
    public ManuscriptResponse startScreening(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
        RoleGuard.requireChairOrAdmin(principal);
        ManuscriptRow manuscript = manuscriptRepository.findByIdForUpdate(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        VersionRow version = findVersion(versionId);
        ensureVersionBelongsToManuscript(version, manuscriptId);
        if (manuscript.currentVersionId() == null || manuscript.currentVersionId() != versionId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the current version can enter screening");
        }
        if ("UNDER_SCREENING".equals(manuscript.currentStatus())) {
            return toResponse(manuscriptRepository.findById(manuscriptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found")));
        }
        if (!Set.of("SUBMITTED", "REVISED_SUBMITTED").contains(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manuscript is not ready for screening");
        }
        manuscriptRepository.updateStatus(manuscriptId, "UNDER_SCREENING");
        return toResponse(manuscriptRepository.findById(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found")));
    }

    public ManuscriptResponse getManuscript(CurrentUserPrincipal principal, long manuscriptId) {
        RoleGuard.requireRole(principal, "AUTHOR");
        ManuscriptRow manuscript = findOwnedManuscript(principal, manuscriptId);
        return toResponse(manuscript);
    }

    public List<ManuscriptSummaryResponse> listManuscripts(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "AUTHOR");
        return manuscriptRepository.listBySubmitter(principal.userId()).stream()
                .map(row -> new ManuscriptSummaryResponse(
                        row.manuscriptId(),
                        row.currentVersionId(),
                        row.currentStatus(),
                        row.currentRoundNo(),
                        row.blindMode(),
                        row.submittedAt(),
                        row.lastDecisionCode(),
                        row.currentVersionTitle(),
                        row.currentVersionNo()
                ))
                .toList();
    }

    public List<VersionSummaryResponse> listVersions(CurrentUserPrincipal principal, long manuscriptId) {
        RoleGuard.requireRole(principal, "AUTHOR");
        findOwnedManuscript(principal, manuscriptId);
        return versionRepository.listByManuscript(manuscriptId).stream()
                .map(row -> new VersionSummaryResponse(
                        row.versionId(),
                        row.versionNo(),
                        row.versionType(),
                        row.title(),
                        row.submittedAt(),
                        row.pdfFileName(),
                        row.pdfFileSize()
                ))
                .toList();
    }

    private ManuscriptResponse toResponse(ManuscriptRow manuscript) {
        return new ManuscriptResponse(
                manuscript.manuscriptId(),
                manuscript.submitterId(),
                manuscript.currentVersionId() == null ? 0 : manuscript.currentVersionId(),
                manuscript.currentStatus(),
                manuscript.currentRoundNo(),
                manuscript.blindMode(),
                manuscript.submittedAt(),
                manuscript.lastDecisionCode(),
                manuscript.currentVersionTitle(),
                manuscript.currentVersionNo() == null ? 0 : manuscript.currentVersionNo()
        );
    }

    private ManuscriptRow findOwnedManuscript(CurrentUserPrincipal principal, long manuscriptId) {
        ManuscriptRow manuscript = manuscriptRepository.findById(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureOwnership(principal, manuscript);
        return manuscript;
    }

    private ManuscriptRow findOwnedManuscriptForUpdate(CurrentUserPrincipal principal, long manuscriptId) {
        ManuscriptRow manuscript = manuscriptRepository.findByIdForUpdate(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        ensureOwnership(principal, manuscript);
        return manuscript;
    }

    private VersionRow findVersion(long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    private void ensureVersionBelongsToManuscript(VersionRow version, long manuscriptId) {
        if (version.manuscriptId() != manuscriptId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version does not belong to manuscript");
        }
    }

    private void ensureOwnership(CurrentUserPrincipal principal, ManuscriptRow manuscript) {
        if (principal.userId() != manuscript.submitterId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden manuscript access");
        }
    }

    private boolean canDownloadPdf(CurrentUserPrincipal principal, ManuscriptRow manuscript, VersionRow version) {
        if (principal == null) {
            return false;
        }
        if (RoleGuard.hasRole(principal, "ADMIN") || RoleGuard.hasRole(principal, "CHAIR")) {
            return true;
        }
        if (RoleGuard.hasRole(principal, "AUTHOR") && principal.userId() == manuscript.submitterId()) {
            return true;
        }
        return false;
    }

    private void validateBlindMode(String blindMode) {
        if (blindMode == null || !VALID_BLIND_MODES.contains(blindMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid blind mode");
        }
    }

    private void validateAuthors(List<AuthorRequest> authors) {
        if (authors == null || authors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one author is required");
        }
        int correspondingCount = 0;
        Set<Integer> seenOrders = new HashSet<>();
        for (AuthorRequest author : authors) {
            if (author.authorOrder() == null || author.authorOrder() <= 0 || !seenOrders.add(author.authorOrder())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Author order must be positive and unique");
            }
            if (Boolean.TRUE.equals(author.isCorresponding())) {
                correspondingCount++;
            }
        }
        if (correspondingCount != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one corresponding author is required");
        }
    }

    private boolean isPdf(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        String contentType = file.getContentType();
        return "application/pdf".equalsIgnoreCase(contentType) || lowerFileName.endsWith(".pdf");
    }

    private boolean hasPdfMagic(byte[] pdfBytes) {
        if (pdfBytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (pdfBytes[index] != PDF_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    record PdfPayload(byte[] bytes, String fileName) {
    }
}
