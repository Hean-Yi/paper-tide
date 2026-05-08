package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RealPlatformController {
    private final RealPlatformService service;
    private final RealPlatformOperationsReadService operationsReadService;
    private final RealPlatformWorkflowReadService workflowReadService;

    public RealPlatformController(
            RealPlatformService service,
            RealPlatformOperationsReadService operationsReadService,
            RealPlatformWorkflowReadService workflowReadService
    ) {
        this.service = service;
        this.operationsReadService = operationsReadService;
        this.workflowReadService = workflowReadService;
    }

    @PostMapping("/conferences/{conferenceId}/forms")
    public FormDefinitionResponse createForm(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody FormDefinitionRequest request
    ) {
        return service.createForm(principal, conferenceId, request);
    }

    @PostMapping("/review-assignments/{assignmentId}/form-response")
    public ReviewFormResponse saveReviewFormResponse(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody ReviewFormResponseRequest request
    ) {
        return service.saveReviewFormResponse(principal, assignmentId, request);
    }

    @GetMapping("/review-assignments/{assignmentId}/review-form")
    public ReviewFormPackageResponse reviewForm(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return workflowReadService.reviewForm(principal, assignmentId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/author-feedback")
    public AuthorFeedbackResponse submitAuthorFeedback(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody AuthorFeedbackRequest request
    ) {
        return service.submitAuthorFeedback(principal, manuscriptId, request);
    }

    @GetMapping("/manuscripts/{manuscriptId}/author-feedback")
    public List<AuthorFeedbackResponse> listAuthorFeedback(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId
    ) {
        return service.listAuthorFeedback(principal, manuscriptId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/tags")
    public PaperTagResponse upsertPaperTag(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody PaperTagRequest request
    ) {
        return service.upsertPaperTag(principal, manuscriptId, request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/paper-roles")
    public PaperRoleResponse assignPaperRole(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody PaperRoleRequest request
    ) {
        return service.assignPaperRole(principal, manuscriptId, request);
    }

    @PostMapping("/conferences/{conferenceId}/imports/tags/preview")
    public ImportPreviewResponse previewTagImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody TagImportPreviewRequest request
    ) {
        return service.previewTagImport(principal, conferenceId, request);
    }

    @PostMapping("/imports/{batchId}/confirm")
    public ImportConfirmResponse confirmImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long batchId
    ) {
        return service.confirmImport(principal, batchId);
    }

    @PostMapping("/conferences/{conferenceId}/reviewer-invitations/imports/preview")
    public ImportPreviewResponse previewReviewerInvitationImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody BulkReviewerInvitationImportPreviewRequest request
    ) {
        return service.previewReviewerInvitationImport(principal, conferenceId, request);
    }

    @PostMapping("/reviewer-invitation-imports/{batchId}/confirm")
    public ImportConfirmResponse confirmReviewerInvitationImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long batchId
    ) {
        return service.confirmReviewerInvitationImport(principal, batchId);
    }

    @GetMapping("/conferences/{conferenceId}/assignment-operations")
    public AssignmentOperationsResponse assignmentOperations(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId
    ) {
        return operationsReadService.assignmentOperations(principal, conferenceId);
    }

    @PostMapping("/conferences/{conferenceId}/reviewer-invitations")
    public ReviewerInvitationResponse createReviewerInvitation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody ReviewerInvitationRequest request
    ) {
        return service.createReviewerInvitation(principal, conferenceId, request);
    }

    @PostMapping("/reviewer-invitations/{invitationId}/accept")
    public ReviewerInvitationResponse acceptReviewerInvitation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long invitationId
    ) {
        return service.acceptReviewerInvitation(principal, invitationId);
    }

    @PostMapping("/reviewer-invitations/{invitationId}/decline")
    public ReviewerInvitationResponse declineReviewerInvitation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long invitationId
    ) {
        return service.declineReviewerInvitation(principal, invitationId);
    }

    @PostMapping("/review-assignments/{assignmentId}/external-delegations")
    public ExternalDelegationResponse requestExternalDelegation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody ExternalDelegationRequest request
    ) {
        return service.requestExternalDelegation(principal, assignmentId, request);
    }

    @PostMapping("/external-delegations/{delegationId}/approve")
    public ExternalDelegationResponse approveExternalDelegation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long delegationId,
            @RequestBody ExternalDelegationDecisionRequest request
    ) {
        return service.decideExternalDelegation(principal, delegationId, "APPROVED", request);
    }

    @PostMapping("/external-delegations/{delegationId}/reject")
    public ExternalDelegationResponse rejectExternalDelegation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long delegationId,
            @RequestBody ExternalDelegationDecisionRequest request
    ) {
        return service.decideExternalDelegation(principal, delegationId, "REJECTED", request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/conflicts")
    public ConflictRelationshipResponse recordConflictRelationship(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody ConflictRelationshipRequest request
    ) {
        return service.recordConflictRelationship(principal, manuscriptId, request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/matching-scores")
    public ReviewerMatchingScoreResponse recordReviewerMatchingScore(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody ReviewerMatchingScoreRequest request
    ) {
        return service.recordReviewerMatchingScore(principal, manuscriptId, request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/matching-scores/imports/preview")
    public ImportPreviewResponse previewMatchingScoreImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody BulkMatchingScoreImportPreviewRequest request
    ) {
        return service.previewMatchingScoreImport(principal, manuscriptId, request);
    }

    @PostMapping("/matching-score-imports/{batchId}/confirm")
    public ImportConfirmResponse confirmMatchingScoreImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long batchId
    ) {
        return service.confirmMatchingScoreImport(principal, batchId);
    }

    @PostMapping("/review-rounds/{roundId}/assignment-proposals")
    public AssignmentProposalBundleResponse createAssignmentProposalBundle(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId,
            @RequestBody AssignmentProposalRequest request
    ) {
        return service.createAssignmentProposalBundle(principal, roundId, request);
    }

    @PostMapping("/assignment-proposals/{bundleId}/overrides")
    public AssignmentOverrideResponse recordAssignmentOverride(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long bundleId,
            @RequestBody AssignmentOverrideRequest request
    ) {
        return service.recordAssignmentOverride(principal, bundleId, request);
    }

    @PostMapping("/assignment-proposals/{bundleId}/confirm-drafts")
    public AssignmentProposalConfirmDraftsResponse confirmAssignmentProposalDrafts(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long bundleId
    ) {
        return service.confirmAssignmentProposalDrafts(principal, bundleId);
    }

    @PostMapping("/conferences/{conferenceId}/email-templates")
    public EmailTemplateResponse createEmailTemplate(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody EmailTemplateRequest request
    ) {
        return service.createEmailTemplate(principal, conferenceId, request);
    }

    @PostMapping("/email-templates/{templateId}/preview")
    public EmailTemplatePreviewResponse previewEmailTemplate(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long templateId,
            @RequestBody EmailTemplatePreviewRequest request
    ) {
        return service.previewEmailTemplate(principal, templateId, request);
    }

    @PostMapping("/email-templates/{templateId}/test-send")
    public EmailTemplateTestSendResponse testSendEmailTemplate(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long templateId,
            @RequestBody EmailTemplateTestSendRequest request
    ) {
        return service.testSendEmailTemplate(principal, templateId, request);
    }

    @PostMapping("/review-assignments/{assignmentId}/offline-review/preview")
    public OfflineReviewPreviewResponse previewOfflineReview(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody OfflineReviewPreviewRequest request
    ) {
        return service.previewOfflineReview(principal, assignmentId, request);
    }

    @GetMapping("/review-assignments/{assignmentId}/offline-review/template")
    public OfflineReviewTemplateResponse offlineReviewTemplate(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return service.offlineReviewTemplate(principal, assignmentId);
    }

    @PostMapping("/offline-review-imports/{batchId}/confirm")
    public OfflineReviewConfirmResponse confirmOfflineReview(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long batchId
    ) {
        return service.confirmOfflineReview(principal, batchId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/camera-ready-files")
    public CameraReadyFileResponse submitCameraReadyFile(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody CameraReadyFileRequest request
    ) {
        return service.submitCameraReadyFile(principal, manuscriptId, request);
    }

    @PostMapping("/camera-ready-files/{fileId}/accept")
    public CameraReadyFileResponse acceptCameraReadyFile(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long fileId,
            @RequestBody CameraReadyDecisionRequest request
    ) {
        return service.decideCameraReadyFile(principal, fileId, "ACCEPTED", request);
    }

    @PostMapping("/camera-ready-files/{fileId}/reject")
    public CameraReadyFileResponse rejectCameraReadyFile(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long fileId,
            @RequestBody CameraReadyDecisionRequest request
    ) {
        return service.decideCameraReadyFile(principal, fileId, "REJECTED", request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/publication-metadata")
    public PublicationMetadataResponse upsertPublicationMetadata(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody PublicationMetadataRequest request
    ) {
        return service.upsertPublicationMetadata(principal, manuscriptId, request);
    }

    @PostMapping("/conferences/{conferenceId}/proceedings/preview")
    public ProceedingsPreviewResponse previewProceedings(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody ProceedingsPreviewRequest request
    ) {
        return service.previewProceedings(principal, conferenceId, request);
    }

    @PostMapping("/proceedings-exports/{exportBatchId}/download-metadata")
    public ProceedingsExportDownloadMetadataResponse proceedingsExportDownloadMetadata(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long exportBatchId
    ) {
        return service.proceedingsExportDownloadMetadata(principal, exportBatchId);
    }

    @GetMapping("/conferences/{conferenceId}/publication-operations")
    public PublicationOperationsResponse publicationOperations(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId
    ) {
        return operationsReadService.publicationOperations(principal, conferenceId);
    }
}
