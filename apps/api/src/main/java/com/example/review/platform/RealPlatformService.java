package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformService {
    private final RealPlatformRepository repository;
    private final ObjectMapper objectMapper;

    public RealPlatformService(RealPlatformRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormDefinitionResponse createForm(
            CurrentUserPrincipal principal,
            long conferenceId,
            FormDefinitionRequest request
    ) {
        PlatformConferenceRow conference = requireConferenceOperator(principal, conferenceId);
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
        if (!canSeeAuthorFeedback(principal, manuscript)) {
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
        PlatformManuscriptRow manuscript = requireManuscriptOperator(principal, manuscriptId);
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
        PlatformManuscriptRow manuscript = requireManuscriptOperator(principal, manuscriptId);
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
        PlatformConferenceRow conference = requireConferenceOperator(principal, conferenceId);
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
        requireConferenceOperator(principal, batch.conferenceId());
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

    @Transactional
    public ReviewerInvitationResponse createReviewerInvitation(
            CurrentUserPrincipal principal,
            long conferenceId,
            ReviewerInvitationRequest request
    ) {
        PlatformConferenceRow conference = requireConferenceOperator(principal, conferenceId);
        long invitationId = repository.upsertReviewerInvitation(
                conference.conferenceId(),
                request.reviewerId(),
                request.invitationMessage(),
                principal.userId(),
                request.expiresAt() == null ? null : Timestamp.from(request.expiresAt())
        );
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitationId).orElseThrow());
    }

    @Transactional
    public ReviewerInvitationResponse acceptReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        PlatformReviewerInvitationRow invitation = requireInvitationInvitee(principal, invitationId);
        repository.updateReviewerInvitationStatus(invitation.invitationId(), "ACCEPTED");
        repository.upsertConferenceReviewer(invitation.conferenceId(), invitation.reviewerId(), invitation.invitedBy());
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitation.invitationId()).orElseThrow());
    }

    @Transactional
    public ReviewerInvitationResponse declineReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        PlatformReviewerInvitationRow invitation = requireInvitationInvitee(principal, invitationId);
        repository.updateReviewerInvitationStatus(invitation.invitationId(), "DECLINED");
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitation.invitationId()).orElseThrow());
    }

    @Transactional
    public ExternalDelegationResponse requestExternalDelegation(
            CurrentUserPrincipal principal,
            long assignmentId,
            ExternalDelegationRequest request
    ) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        long delegationId = repository.insertExternalReviewerDelegation(
                assignment,
                principal.userId(),
                requireText(request.externalName(), "externalName"),
                requireText(request.externalEmail(), "externalEmail"),
                request.rationale()
        );
        return toExternalDelegationResponse(repository.findExternalDelegation(delegationId).orElseThrow());
    }

    @Transactional
    public ExternalDelegationResponse decideExternalDelegation(
            CurrentUserPrincipal principal,
            long delegationId,
            String delegationStatus,
            ExternalDelegationDecisionRequest request
    ) {
        PlatformExternalDelegationRow delegation = repository.findExternalDelegation(delegationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delegation not found"));
        requireConferenceOperator(principal, delegation.conferenceId());
        repository.decideExternalDelegation(delegation.delegationId(), delegationStatus, request.decisionNote(), principal.userId());
        return toExternalDelegationResponse(repository.findExternalDelegation(delegation.delegationId()).orElseThrow());
    }

    @Transactional
    public ConflictRelationshipResponse recordConflictRelationship(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ConflictRelationshipRequest request
    ) {
        PlatformManuscriptRow manuscript = requireManuscriptOperator(principal, manuscriptId);
        String severity = requireEnum(request.severity(), "severity", List.of("SOFT", "HARD"));
        String source = requireEnum(request.conflictSource(), "conflictSource", List.of("MANUAL", "PROFILE", "IMPORT", "BID", "SYSTEM"));
        long relationshipId = repository.upsertConflictRelationship(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.reviewerId(),
                requireText(request.conflictType(), "conflictType"),
                source,
                severity,
                request.note(),
                principal.userId()
        );
        PlatformConflictRelationshipRow row = repository.findConflictRelationship(relationshipId).orElseThrow();
        return new ConflictRelationshipResponse(
                row.conflictRelationshipId(),
                row.manuscriptId(),
                row.reviewerId(),
                row.conflictType(),
                row.conflictSource(),
                row.severity()
        );
    }

    @Transactional
    public ReviewerMatchingScoreResponse recordReviewerMatchingScore(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ReviewerMatchingScoreRequest request
    ) {
        PlatformManuscriptRow manuscript = requireManuscriptOperator(principal, manuscriptId);
        if (request.matchingScore() == null || request.matchingScore() < 0 || request.matchingScore() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchingScore is invalid");
        }
        long matchingScoreId = repository.upsertReviewerMatchingScore(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.reviewerId(),
                requireText(request.scoreSource(), "scoreSource"),
                request.matchingScore(),
                request.rationale(),
                principal.userId()
        );
        return new ReviewerMatchingScoreResponse(
                matchingScoreId,
                manuscript.manuscriptId(),
                request.reviewerId(),
                request.matchingScore()
        );
    }

    @Transactional
    public AssignmentProposalBundleResponse createAssignmentProposalBundle(
            CurrentUserPrincipal principal,
            long roundId,
            AssignmentProposalRequest request
    ) {
        PlatformReviewRoundRow round = repository.findReviewRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        requireConferenceOperator(principal, round.conferenceId());
        String proposalName = requireText(request.proposalName(), "proposalName");
        int limit = request.limit() == null ? 10 : Math.max(0, Math.min(request.limit(), 50));
        long bundleId = repository.insertAssignmentProposalBundle(round, proposalName, principal.userId());
        List<PlatformReviewerCandidateRow> candidates = repository.listEligibleProposalCandidates(
                round.conferenceId(),
                round.manuscriptId(),
                round.roundId(),
                limit
        );
        int rank = 1;
        for (PlatformReviewerCandidateRow candidate : candidates) {
            repository.insertAssignmentProposal(
                    bundleId,
                    candidate.reviewerId(),
                    rank++,
                    candidate.matchingScore(),
                    candidate.eligibilityStatus(),
                    "Generated from active reviewer pool and conflict checks"
            );
        }
        return new AssignmentProposalBundleResponse(bundleId, round.roundId(), round.manuscriptId(), candidates.size());
    }

    @Transactional
    public AssignmentOverrideResponse recordAssignmentOverride(
            CurrentUserPrincipal principal,
            long bundleId,
            AssignmentOverrideRequest request
    ) {
        PlatformAssignmentProposalBundleRow bundle = repository.findAssignmentProposalBundle(bundleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment proposal bundle not found"));
        requireConferenceOperator(principal, bundle.conferenceId());
        long overrideId = repository.insertAssignmentOverrideAudit(
                bundle.bundleId(),
                request.reviewerId(),
                requireText(request.overrideReason(), "overrideReason"),
                principal.userId()
        );
        return new AssignmentOverrideResponse(overrideId, bundle.bundleId(), request.reviewerId());
    }

    @Transactional
    public EmailTemplateResponse createEmailTemplate(
            CurrentUserPrincipal principal,
            long conferenceId,
            EmailTemplateRequest request
    ) {
        PlatformConferenceRow conference = requireConferenceOperator(principal, conferenceId);
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
        PlatformAssignmentRow assignment = requireReviewerAssignment(principal, assignmentId);
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
        PlatformAssignmentRow assignment = requireReviewerAssignment(principal, assignmentId);
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
        requireConferenceOperator(principal, file.conferenceId());
        repository.decideCameraReadyFile(file.cameraReadyFileId(), fileStatus, request.decisionNote());
        return new CameraReadyFileResponse(file.cameraReadyFileId(), file.manuscriptId(), fileStatus);
    }

    @Transactional
    public PublicationMetadataResponse upsertPublicationMetadata(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PublicationMetadataRequest request
    ) {
        PlatformManuscriptRow manuscript = requireManuscriptOperator(principal, manuscriptId);
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
        PlatformConferenceRow conference = requireConferenceOperator(principal, conferenceId);
        int paperCount = repository.countProceedingsReadyPapers(conference.conferenceId());
        long exportBatchId = repository.insertProceedingsExportPreview(
                conference.conferenceId(),
                requireText(request.exportName(), "exportName"),
                paperCount,
                toJson(Map.of("paperCount", paperCount)),
                principal.userId()
        );
        return new ProceedingsPreviewResponse(exportBatchId, conference.conferenceId(), paperCount, "PREVIEWED");
    }

    private PlatformConferenceRow requireConferenceOperator(CurrentUserPrincipal principal, long conferenceId) {
        RoleGuard.requireChairOrAdmin(principal);
        PlatformConferenceRow conference = repository.findConference(conferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference not found"));
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, conference.organizerUserId())) {
            return conference;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conference operator access is not allowed");
    }

    private PlatformManuscriptRow requireManuscriptOperator(CurrentUserPrincipal principal, long manuscriptId) {
        RoleGuard.requireChairOrAdmin(principal);
        PlatformManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, manuscript.organizerUserId())) {
            return manuscript;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manuscript operator access is not allowed");
    }

    private PlatformReviewerInvitationRow requireInvitationInvitee(CurrentUserPrincipal principal, long invitationId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformReviewerInvitationRow invitation = repository.findReviewerInvitation(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reviewer invitation not found"));
        if (invitation.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer invitation access is not allowed");
        }
        return invitation;
    }

    private PlatformAssignmentRow requireReviewerAssignment(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        return assignment;
    }

    private PlatformEmailTemplateRow requireEmailTemplateOperator(CurrentUserPrincipal principal, long templateId) {
        PlatformEmailTemplateRow template = repository.findEmailTemplate(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email template not found"));
        requireConferenceOperator(principal, template.conferenceId());
        return template;
    }

    private boolean canSeeAuthorFeedback(CurrentUserPrincipal principal, PlatformManuscriptRow manuscript) {
        return RoleGuard.hasRole(principal, "ADMIN")
                || isConferenceOrganizer(principal, manuscript.organizerUserId())
                || (RoleGuard.hasRole(principal, "AUTHOR") && manuscript.submitterId() == principal.userId());
    }

    private boolean isConferenceOrganizer(CurrentUserPrincipal principal, Long organizerUserId) {
        return RoleGuard.hasRole(principal, "CHAIR")
                && organizerUserId != null
                && organizerUserId.equals(principal.userId());
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

    private ReviewerInvitationResponse toReviewerInvitationResponse(PlatformReviewerInvitationRow row) {
        return new ReviewerInvitationResponse(
                row.invitationId(),
                row.conferenceId(),
                row.reviewerId(),
                row.invitationStatus()
        );
    }

    private ExternalDelegationResponse toExternalDelegationResponse(PlatformExternalDelegationRow row) {
        return new ExternalDelegationResponse(
                row.delegationId(),
                row.assignmentId(),
                row.manuscriptId(),
                row.externalName(),
                row.externalEmail(),
                row.delegationStatus()
        );
    }
}
