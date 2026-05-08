package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RealPlatformService {
    private final RealPlatformWorkflowCommandService workflowCommandService;
    private final RealPlatformAssignmentCommandService assignmentCommandService;
    private final RealPlatformPublicationCommandService publicationCommandService;

    public RealPlatformService(
            RealPlatformWorkflowCommandService workflowCommandService,
            RealPlatformAssignmentCommandService assignmentCommandService,
            RealPlatformPublicationCommandService publicationCommandService
    ) {
        this.workflowCommandService = workflowCommandService;
        this.assignmentCommandService = assignmentCommandService;
        this.publicationCommandService = publicationCommandService;
    }

    public FormDefinitionResponse createForm(
            CurrentUserPrincipal principal,
            long conferenceId,
            FormDefinitionRequest request
    ) {
        return workflowCommandService.createForm(principal, conferenceId, request);
    }
    public ReviewFormResponse saveReviewFormResponse(
            CurrentUserPrincipal principal,
            long assignmentId,
            ReviewFormResponseRequest request
    ) {
        return workflowCommandService.saveReviewFormResponse(principal, assignmentId, request);
    }
    public WorkflowFormResponse saveManuscriptFormResponse(
            CurrentUserPrincipal principal,
            long manuscriptId,
            WorkflowFormResponseRequest request
    ) {
        return workflowCommandService.saveManuscriptFormResponse(principal, manuscriptId, request);
    }
    public AuthorFeedbackResponse submitAuthorFeedback(
            CurrentUserPrincipal principal,
            long manuscriptId,
            AuthorFeedbackRequest request
    ) {
        return workflowCommandService.submitAuthorFeedback(principal, manuscriptId, request);
    }
    public List<AuthorFeedbackResponse> listAuthorFeedback(CurrentUserPrincipal principal, long manuscriptId) {
        return workflowCommandService.listAuthorFeedback(principal, manuscriptId);
    }
    public PaperTagResponse upsertPaperTag(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PaperTagRequest request
    ) {
        return workflowCommandService.upsertPaperTag(principal, manuscriptId, request);
    }
    public PaperRoleResponse assignPaperRole(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PaperRoleRequest request
    ) {
        return workflowCommandService.assignPaperRole(principal, manuscriptId, request);
    }
    public ImportPreviewResponse previewTagImport(
            CurrentUserPrincipal principal,
            long conferenceId,
            TagImportPreviewRequest request
    ) {
        return workflowCommandService.previewTagImport(principal, conferenceId, request);
    }
    public ImportConfirmResponse confirmImport(CurrentUserPrincipal principal, long batchId) {
        return workflowCommandService.confirmImport(principal, batchId);
    }
    public ImportPreviewResponse previewReviewerInvitationImport(
            CurrentUserPrincipal principal,
            long conferenceId,
            BulkReviewerInvitationImportPreviewRequest request
    ) {
        return assignmentCommandService.previewReviewerInvitationImport(principal, conferenceId, request);
    }
    public ImportConfirmResponse confirmReviewerInvitationImport(CurrentUserPrincipal principal, long batchId) {
        return assignmentCommandService.confirmReviewerInvitationImport(principal, batchId);
    }
    public ReviewerInvitationResponse createReviewerInvitation(
            CurrentUserPrincipal principal,
            long conferenceId,
            ReviewerInvitationRequest request
    ) {
        return assignmentCommandService.createReviewerInvitation(principal, conferenceId, request);
    }
    public ReviewerInvitationResponse acceptReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        return assignmentCommandService.acceptReviewerInvitation(principal, invitationId);
    }
    public ReviewerInvitationResponse declineReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        return assignmentCommandService.declineReviewerInvitation(principal, invitationId);
    }
    public ExternalDelegationResponse requestExternalDelegation(
            CurrentUserPrincipal principal,
            long assignmentId,
            ExternalDelegationRequest request
    ) {
        return assignmentCommandService.requestExternalDelegation(principal, assignmentId, request);
    }
    public ExternalDelegationResponse decideExternalDelegation(
            CurrentUserPrincipal principal,
            long delegationId,
            String delegationStatus,
            ExternalDelegationDecisionRequest request
    ) {
        return assignmentCommandService.decideExternalDelegation(principal, delegationId, delegationStatus, request);
    }
    public ConflictRelationshipResponse recordConflictRelationship(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ConflictRelationshipRequest request
    ) {
        return assignmentCommandService.recordConflictRelationship(principal, manuscriptId, request);
    }
    public ReviewerMatchingScoreResponse recordReviewerMatchingScore(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ReviewerMatchingScoreRequest request
    ) {
        return assignmentCommandService.recordReviewerMatchingScore(principal, manuscriptId, request);
    }
    public ImportPreviewResponse previewMatchingScoreImport(
            CurrentUserPrincipal principal,
            long manuscriptId,
            BulkMatchingScoreImportPreviewRequest request
    ) {
        return assignmentCommandService.previewMatchingScoreImport(principal, manuscriptId, request);
    }
    public ImportConfirmResponse confirmMatchingScoreImport(CurrentUserPrincipal principal, long batchId) {
        return assignmentCommandService.confirmMatchingScoreImport(principal, batchId);
    }
    public AssignmentProposalBundleResponse createAssignmentProposalBundle(
            CurrentUserPrincipal principal,
            long roundId,
            AssignmentProposalRequest request
    ) {
        return assignmentCommandService.createAssignmentProposalBundle(principal, roundId, request);
    }
    public AssignmentOverrideResponse recordAssignmentOverride(
            CurrentUserPrincipal principal,
            long bundleId,
            AssignmentOverrideRequest request
    ) {
        return assignmentCommandService.recordAssignmentOverride(principal, bundleId, request);
    }
    public AssignmentProposalConfirmDraftsResponse confirmAssignmentProposalDrafts(CurrentUserPrincipal principal, long bundleId) {
        return assignmentCommandService.confirmAssignmentProposalDrafts(principal, bundleId);
    }
    public EmailTemplateResponse createEmailTemplate(
            CurrentUserPrincipal principal,
            long conferenceId,
            EmailTemplateRequest request
    ) {
        return publicationCommandService.createEmailTemplate(principal, conferenceId, request);
    }
    public EmailTemplatePreviewResponse previewEmailTemplate(
            CurrentUserPrincipal principal,
            long templateId,
            EmailTemplatePreviewRequest request
    ) {
        return publicationCommandService.previewEmailTemplate(principal, templateId, request);
    }
    public EmailTemplateTestSendResponse testSendEmailTemplate(
            CurrentUserPrincipal principal,
            long templateId,
            EmailTemplateTestSendRequest request
    ) {
        return publicationCommandService.testSendEmailTemplate(principal, templateId, request);
    }
    public OfflineReviewPreviewResponse previewOfflineReview(
            CurrentUserPrincipal principal,
            long assignmentId,
            OfflineReviewPreviewRequest request
    ) {
        return publicationCommandService.previewOfflineReview(principal, assignmentId, request);
    }
    public OfflineReviewTemplateResponse offlineReviewTemplate(CurrentUserPrincipal principal, long assignmentId) {
        return publicationCommandService.offlineReviewTemplate(principal, assignmentId);
    }
    public OfflineReviewConfirmResponse confirmOfflineReview(CurrentUserPrincipal principal, long batchId) {
        return publicationCommandService.confirmOfflineReview(principal, batchId);
    }
    public CameraReadyFileResponse submitCameraReadyFile(
            CurrentUserPrincipal principal,
            long manuscriptId,
            CameraReadyFileRequest request
    ) {
        return publicationCommandService.submitCameraReadyFile(principal, manuscriptId, request);
    }
    public CameraReadyFileResponse decideCameraReadyFile(
            CurrentUserPrincipal principal,
            long fileId,
            String fileStatus,
            CameraReadyDecisionRequest request
    ) {
        return publicationCommandService.decideCameraReadyFile(principal, fileId, fileStatus, request);
    }
    public PublicationMetadataResponse upsertPublicationMetadata(
            CurrentUserPrincipal principal,
            long manuscriptId,
            PublicationMetadataRequest request
    ) {
        return publicationCommandService.upsertPublicationMetadata(principal, manuscriptId, request);
    }
    public ProceedingsPreviewResponse previewProceedings(
            CurrentUserPrincipal principal,
            long conferenceId,
            ProceedingsPreviewRequest request
    ) {
        return publicationCommandService.previewProceedings(principal, conferenceId, request);
    }
    public ProceedingsExportDownloadMetadataResponse proceedingsExportDownloadMetadata(
            CurrentUserPrincipal principal,
            long exportBatchId
    ) {
        return publicationCommandService.proceedingsExportDownloadMetadata(principal, exportBatchId);
    }
}
