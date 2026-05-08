package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformWorkflowCommandService {
    private final RealPlatformWorkflowRepository repository;
    private final RealPlatformAccessService accessService;
    private final ObjectMapper objectMapper;

    public RealPlatformWorkflowCommandService(
            RealPlatformWorkflowRepository repository,
            RealPlatformAccessService accessService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormDefinitionResponse createForm(
            CurrentUserPrincipal principal,
            long conferenceId,
            FormDefinitionRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        String formType = requireEnum(request.formType(), "formType", List.of(
                "SUBMISSION",
                "REVIEW",
                "META_REVIEW",
                "AUTHOR_FEEDBACK",
                "CAMERA_READY"
        ));
        String formName = requireText(request.formName(), "formName");
        if (request.fields() == null || request.fields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fields are required");
        }

        long formId = repository.insertFormDefinition(conference.conferenceId(), formType, formName, principal.userId());
        List<FormFieldResponse> fields = new ArrayList<>();
        for (FormFieldRequest field : request.fields()) {
            String fieldKey = requireText(field.fieldKey(), "fieldKey");
            String fieldLabel = requireText(field.fieldLabel(), "fieldLabel");
            String fieldType = requireEnum(field.fieldType(), "fieldType", List.of(
                    "TEXT",
                    "LONG_TEXT",
                    "NUMBER",
                    "SCORE",
                    "BOOLEAN",
                    "SELECT"
            ));
            String visibility = requireEnum(field.visibility(), "visibility", List.of(
                    "AUTHOR_VISIBLE",
                    "CHAIR_ONLY",
                    "REVIEWER_ONLY",
                    "PUBLIC_SUMMARY"
            ));
            boolean required = Boolean.TRUE.equals(field.required());
            int displayOrder = field.displayOrder() == null ? fields.size() + 1 : field.displayOrder();
            long fieldId = repository.insertFormField(
                    formId,
                    fieldKey,
                    fieldLabel,
                    fieldType,
                    required,
                    visibility,
                    displayOrder,
                    field.options() == null ? null : toJson(field.options())
            );
            fields.add(new FormFieldResponse(fieldId, fieldKey, fieldLabel, fieldType, required, visibility, displayOrder));
        }
        return new FormDefinitionResponse(formId, conference.conferenceId(), formType, formName, fields);
    }

    @Transactional
    public ReviewFormResponse saveReviewFormResponse(
            CurrentUserPrincipal principal,
            long assignmentId,
            ReviewFormResponseRequest request
    ) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        PlatformFormRow form = repository.findForm(request.formId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found"));
        if (!"REVIEW".equals(form.formType()) || form.conferenceId() != assignment.conferenceId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review form does not match assignment conference");
        }

        String responseStatus = requireEnum(request.responseStatus(), "responseStatus", List.of("DRAFT", "SUBMITTED"));
        Map<String, Object> answers = request.answers() == null ? Map.of() : request.answers();
        List<PlatformFormFieldRow> fields = repository.listFormFields(form.formId());
        if ("SUBMITTED".equals(responseStatus)) {
            validateRequiredAnswers(fields, answers);
        }

        long responseId = repository.upsertReviewFormResponse(
                form.formId(),
                assignment.assignmentId(),
                principal.userId(),
                responseStatus,
                answers
        );
        if ("SUBMITTED".equals(responseStatus)) {
            repository.markAssignmentSubmitted(assignment.assignmentId());
        }
        PlatformReviewFormResponseRow row = repository.findReviewFormResponse(responseId).orElseThrow();
        return new ReviewFormResponse(
                row.responseId(),
                row.formId(),
                row.assignmentId(),
                row.responseStatus(),
                row.answers(),
                row.submittedAt()
        );
    }

    @Transactional
    public AuthorFeedbackResponse submitAuthorFeedback(
            CurrentUserPrincipal principal,
            long manuscriptId,
            AuthorFeedbackRequest request
    ) {
        RoleGuard.requireRole(principal, "AUTHOR");
        PlatformManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (manuscript.submitterId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Author feedback access is not allowed");
        }
        String feedbackType = requireEnum(request.feedbackType(), "feedbackType", List.of(
                "REBUTTAL",
                "AUTHOR_FEEDBACK",
                "REVISION_NOTE"
        ));
        long feedbackId = repository.insertAuthorFeedback(
                manuscript.manuscriptId(),
                principal.userId(),
                feedbackType,
                requireText(request.feedbackText(), "feedbackText")
        );
        return toAuthorFeedbackResponse(repository.findAuthorFeedback(feedbackId).orElseThrow());
    }

    public List<AuthorFeedbackResponse> listAuthorFeedback(CurrentUserPrincipal principal, long manuscriptId) {
        PlatformManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (!accessService.canSeeAuthorFeedback(principal, manuscript)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Author feedback access is not allowed");
        }
        return repository.listAuthorFeedback(manuscriptId).stream()
                .map(this::toAuthorFeedbackResponse)
                .toList();
    }

    @Transactional
    public PaperTagResponse upsertPaperTag(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PaperTagRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        long paperTagId = repository.upsertPaperTag(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                requireText(request.tagName(), "tagName"),
                request.tagValue(),
                principal.userId()
        );
        PlatformPaperTagRow row = repository.findPaperTag(paperTagId).orElseThrow();
        return new PaperTagResponse(
                row.paperTagId(),
                row.conferenceId(),
                row.manuscriptId(),
                row.tagName(),
                row.tagValue()
        );
    }

    @Transactional
    public PaperRoleResponse assignPaperRole(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PaperRoleRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        String roleType = requireEnum(request.roleType(), "roleType", List.of(
                "PRIMARY_REVIEWER",
                "SECONDARY_REVIEWER",
                "META_REVIEWER",
                "DISCUSSION_LEAD",
                "SHEPHERD",
                "PROCEEDINGS_EDITOR"
        ));
        long paperRoleId = repository.upsertPaperRole(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.userId(),
                roleType,
                principal.userId()
        );
        PlatformPaperRoleRow row = repository.findPaperRole(paperRoleId).orElseThrow();
        return new PaperRoleResponse(
                row.paperRoleId(),
                row.conferenceId(),
                row.manuscriptId(),
                row.userId(),
                row.roleType()
        );
    }

    @Transactional
    public ImportPreviewResponse previewTagImport(
            CurrentUserPrincipal principal,
            long conferenceId,
            TagImportPreviewRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        TagImportPreviewDocument preview = parseTagCsv(request.csvText());
        int rowCount = preview.validRows().size() + preview.errorRows().size();
        long batchId = repository.insertImportBatch(
                conference.conferenceId(),
                principal.userId(),
                rowCount,
                preview.validRows().size(),
                preview.errorRows().size(),
                preview
        );
        return new ImportPreviewResponse(batchId, rowCount, preview.validRows().size(), preview.errorRows().size());
    }

    @Transactional
    public ImportConfirmResponse confirmImport(CurrentUserPrincipal principal, long batchId) {
        PlatformImportBatchRow batch = repository.findImportBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found"));
        accessService.requireConferenceOperator(principal, batch.conferenceId());
        if (!"TAGS".equals(batch.importType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported import type");
        }
        if (!"PREVIEWED".equals(batch.batchStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Import batch is not confirmable");
        }

        int appliedCount = 0;
        for (TagImportValidRow row : batch.previewDocument().validRows()) {
            PlatformManuscriptRow manuscript = repository.findManuscript(row.manuscriptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previewed manuscript no longer exists"));
            if (manuscript.conferenceId() != batch.conferenceId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previewed manuscript does not belong to batch conference");
            }
            repository.upsertPaperTag(
                    batch.conferenceId(),
                    manuscript.manuscriptId(),
                    row.tagName(),
                    row.tagValue(),
                    principal.userId()
            );
            appliedCount++;
        }
        repository.markImportApplied(batch.batchId());
        return new ImportConfirmResponse(batch.batchId(), appliedCount);
    }

    private void validateRequiredAnswers(List<PlatformFormFieldRow> fields, Map<String, Object> answers) {
        for (PlatformFormFieldRow field : fields) {
            if (!field.required()) {
                continue;
            }
            Object value = answers.get(field.fieldKey());
            if (value == null || (value instanceof String text && text.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.fieldKey() + " is required");
            }
        }
    }

    private TagImportPreviewDocument parseTagCsv(String csvText) {
        String text = requireText(csvText, "csvText");
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !"manuscriptId,tagName,tagValue".equals(lines[0].strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV header must be manuscriptId,tagName,tagValue");
        }
        List<TagImportValidRow> validRows = new ArrayList<>();
        List<TagImportErrorRow> errorRows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            int rowNumber = i + 1;
            if (columns.length != 3) {
                errorRows.add(new TagImportErrorRow(rowNumber, line, "Expected 3 columns"));
                continue;
            }
            try {
                long manuscriptId = Long.parseLong(columns[0].strip());
                String tagName = requireCsvText(columns[1], "tagName");
                String tagValue = columns[2].strip();
                validRows.add(new TagImportValidRow(rowNumber, manuscriptId, tagName, tagValue));
            } catch (IllegalArgumentException ex) {
                errorRows.add(new TagImportErrorRow(rowNumber, line, ex.getMessage()));
            }
        }
        return new TagImportPreviewDocument(validRows, errorRows);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options cannot be serialized");
        }
    }

    private AuthorFeedbackResponse toAuthorFeedbackResponse(PlatformAuthorFeedbackRow row) {
        return new AuthorFeedbackResponse(
                row.feedbackId(),
                row.manuscriptId(),
                row.submittedBy(),
                row.feedbackType(),
                row.feedbackText(),
                row.createdAt()
        );
    }
}
