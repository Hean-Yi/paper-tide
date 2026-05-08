package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformPublicationCommandService {
    private final RealPlatformPublicationRepository repository;
    private final RealPlatformAccessService accessService;

    public RealPlatformPublicationCommandService(
            RealPlatformPublicationRepository repository,
            RealPlatformAccessService accessService
    ) {
        this.repository = repository;
        this.accessService = accessService;
    }

    @Transactional
    public EmailTemplateResponse createEmailTemplate(
            CurrentUserPrincipal principal,
            long conferenceId,
            EmailTemplateRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        long templateId = repository.insertEmailTemplate(
                conference.conferenceId(),
                requireText(request.templateKey(), "templateKey"),
                principal.userId()
        );
        long versionId = repository.insertEmailTemplateVersion(
                templateId,
                requireText(request.subjectTemplate(), "subjectTemplate"),
                requireText(request.bodyTemplate(), "bodyTemplate"),
                principal.userId()
        );
        PlatformEmailTemplateRow template = repository.findEmailTemplate(templateId).orElseThrow();
        return new EmailTemplateResponse(template.templateId(), versionId, template.conferenceId(), template.templateKey());
    }

    public EmailTemplatePreviewResponse previewEmailTemplate(
            CurrentUserPrincipal principal,
            long templateId,
            EmailTemplatePreviewRequest request
    ) {
        PlatformEmailTemplateRow template = requireEmailTemplateOperator(principal, templateId);
        Map<String, Object> variables = request.variables() == null ? Map.of() : request.variables();
        return new EmailTemplatePreviewResponse(
                renderTemplate(template.subjectTemplate(), variables),
                renderTemplate(template.bodyTemplate(), variables)
        );
    }

    @Transactional
    public EmailTemplateTestSendResponse testSendEmailTemplate(
            CurrentUserPrincipal principal,
            long templateId,
            EmailTemplateTestSendRequest request
    ) {
        PlatformEmailTemplateRow template = requireEmailTemplateOperator(principal, templateId);
        Map<String, Object> variables = request.variables() == null ? Map.of() : request.variables();
        long historyId = repository.insertOutboundEmailHistory(
                template,
                requireText(request.recipientEmail(), "recipientEmail"),
                renderTemplate(template.subjectTemplate(), variables),
                renderTemplate(template.bodyTemplate(), variables),
                principal.userId()
        );
        return new EmailTemplateTestSendResponse(historyId, "RECORDED");
    }

    @Transactional
    public OfflineReviewPreviewResponse previewOfflineReview(
            CurrentUserPrincipal principal,
            long assignmentId,
            OfflineReviewPreviewRequest request
    ) {
        PlatformAssignmentRow assignment = accessService.requireReviewerAssignment(principal, assignmentId);
        List<OfflineReviewValidRow> rows = parseOfflineReviewCsv(request.csvText());
        long batchId = repository.insertOfflineReviewImportBatch(
                assignment.assignmentId(),
                principal.userId(),
                rows.size(),
                rows.size(),
                0
        );
        for (OfflineReviewValidRow row : rows) {
            repository.insertOfflineReviewImportRow(
                    batchId,
                    row.rowNumber(),
                    "VALID",
                    row.overallScore(),
                    row.recommendation(),
                    row.commentsToAuthor(),
                    null
            );
        }
        return new OfflineReviewPreviewResponse(batchId, rows.size(), rows.size(), 0);
    }

    public OfflineReviewTemplateResponse offlineReviewTemplate(CurrentUserPrincipal principal, long assignmentId) {
        PlatformAssignmentRow assignment = accessService.requireReviewerAssignment(principal, assignmentId);
        return new OfflineReviewTemplateResponse(
                assignment.assignmentId(),
                "overallScore,recommendation,commentsToAuthor",
                "4,ACCEPT,Strong paper"
        );
    }

    @Transactional
    public OfflineReviewConfirmResponse confirmOfflineReview(CurrentUserPrincipal principal, long batchId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformOfflineReviewBatchRow batch = repository.findOfflineReviewImportBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offline review batch not found"));
        if (batch.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Offline review import access is not allowed");
        }
        if (!"PREVIEWED".equals(batch.batchStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offline review batch is not confirmable");
        }
        int appliedCount = 0;
        for (PlatformOfflineReviewRow row : repository.listValidOfflineReviewRows(batch.batchId())) {
            repository.insertReviewReportFromOfflineRow(batch, row);
            repository.markOfflineReviewRowApplied(row.rowId());
            appliedCount++;
        }
        repository.markOfflineReviewBatchApplied(batch.batchId());
        return new OfflineReviewConfirmResponse(batch.batchId(), appliedCount);
    }

    @Transactional
    public CameraReadyFileResponse submitCameraReadyFile(
            CurrentUserPrincipal principal,
            long manuscriptId,
            CameraReadyFileRequest request
    ) {
        RoleGuard.requireRole(principal, "AUTHOR");
        PlatformManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (manuscript.submitterId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Camera-ready upload access is not allowed");
        }
        if (!"ACCEPTED".equals(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camera-ready files require an accepted manuscript");
        }
        if (manuscript.currentVersionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current version is required");
        }
        long fileId = repository.insertCameraReadyFile(
                manuscript.manuscriptId(),
                manuscript.currentVersionId(),
                principal.userId(),
                requireText(request.fileName(), "fileName"),
                requirePositiveLong(request.fileSize(), "fileSize"),
                requireText(request.checksumSha256(), "checksumSha256"),
                Boolean.TRUE.equals(request.copyrightConfirmed()),
                request.licenseType()
        );
        return new CameraReadyFileResponse(fileId, manuscript.manuscriptId(), "SUBMITTED");
    }

    @Transactional
    public CameraReadyFileResponse decideCameraReadyFile(
            CurrentUserPrincipal principal,
            long fileId,
            String fileStatus,
            CameraReadyDecisionRequest request
    ) {
        PlatformCameraReadyFileRow file = repository.findCameraReadyFile(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camera-ready file not found"));
        accessService.requireConferenceOperator(principal, file.conferenceId());
        repository.decideCameraReadyFile(file.cameraReadyFileId(), fileStatus, request.decisionNote());
        return new CameraReadyFileResponse(file.cameraReadyFileId(), file.manuscriptId(), fileStatus);
    }

    @Transactional
    public PublicationMetadataResponse upsertPublicationMetadata(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PublicationMetadataRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        String status = requireEnum(request.publicationStatus(), "publicationStatus", List.of(
                "DRAFT",
                "READY_FOR_PROCEEDINGS",
                "EXPORTED",
                "PUBLISHED"
        ));
        long metadataId = repository.upsertPublicationMetadata(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.doi(),
                request.indexKeywords(),
                status,
                principal.userId()
        );
        return new PublicationMetadataResponse(metadataId, manuscript.manuscriptId(), status);
    }

    @Transactional
    public ProceedingsPreviewResponse previewProceedings(
            CurrentUserPrincipal principal,
            long conferenceId,
            ProceedingsPreviewRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        int paperCount = repository.countProceedingsReadyPapers(conference.conferenceId());
        long exportBatchId = repository.insertProceedingsExportPreview(
                conference.conferenceId(),
                requireText(request.exportName(), "exportName"),
                paperCount,
                repository.toJson(Map.of("paperCount", paperCount)),
                principal.userId()
        );
        return new ProceedingsPreviewResponse(exportBatchId, conference.conferenceId(), paperCount, "PREVIEWED");
    }

    @Transactional
    public ProceedingsExportDownloadMetadataResponse proceedingsExportDownloadMetadata(
            CurrentUserPrincipal principal,
            long exportBatchId
    ) {
        PlatformProceedingsExportBatchRow batch = repository.findProceedingsExportBatch(exportBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proceedings export batch not found"));
        accessService.requireConferenceOperator(principal, batch.conferenceId());
        if (!"PREVIEWED".equals(batch.exportStatus()) && !"EXPORTED".equals(batch.exportStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proceedings export batch is not downloadable");
        }
        repository.markProceedingsExported(batch.exportBatchId());
        repository.markProceedingsPublicationMetadataExported(batch.conferenceId());
        String fileName = slugify(batch.exportName()) + ".json";
        return new ProceedingsExportDownloadMetadataResponse(
                batch.exportBatchId(),
                "EXPORTED",
                fileName,
                "/api/proceedings-exports/" + batch.exportBatchId() + "/files/" + fileName,
                batch.paperCount()
        );
    }

    private PlatformEmailTemplateRow requireEmailTemplateOperator(CurrentUserPrincipal principal, long templateId) {
        PlatformEmailTemplateRow template = repository.findEmailTemplate(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email template not found"));
        accessService.requireConferenceOperator(principal, template.conferenceId());
        return template;
    }

    private List<OfflineReviewValidRow> parseOfflineReviewCsv(String csvText) {
        String text = requireText(csvText, "csvText");
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !"overallScore,recommendation,commentsToAuthor".equals(lines[0].strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV header must be overallScore,recommendation,commentsToAuthor");
        }
        List<OfflineReviewValidRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length != 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected 3 columns");
            }
            int score;
            try {
                score = Integer.parseInt(columns[0].strip());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "overallScore is invalid");
            }
            if (score < 1 || score > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "overallScore is invalid");
            }
            String recommendation = requireEnum(columns[1].strip(), "recommendation", List.of(
                    "ACCEPT",
                    "REJECT",
                    "MINOR_REVISION",
                    "MAJOR_REVISION",
                    "DESK_REJECT"
            ));
            rows.add(new OfflineReviewValidRow(i + 1, score, recommendation, columns[2].strip()));
        }
        return rows;
    }

    private String requireCsvText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.strip();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.strip();
    }

    private String requireEnum(String value, String fieldName, List<String> allowedValues) {
        String text = requireText(value, fieldName);
        if (!allowedValues.contains(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is invalid");
        }
        return text;
    }

    private long requirePositiveLong(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is invalid");
        }
        return value;
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private String slugify(String value) {
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "proceedings-export" : slug;
    }
}
