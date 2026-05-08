package com.example.review.platform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

record FormDefinitionRequest(
        String formType,
        String formName,
        List<FormFieldRequest> fields
) {
}

record FormFieldRequest(
        String fieldKey,
        String fieldLabel,
        String fieldType,
        Boolean required,
        String visibility,
        Integer displayOrder,
        Object options
) {
}

record FormDefinitionResponse(
        long formId,
        long conferenceId,
        String formType,
        String formName,
        List<FormFieldResponse> fields
) {
}

record FormFieldResponse(
        long fieldId,
        String fieldKey,
        String fieldLabel,
        String fieldType,
        boolean required,
        String visibility,
        int displayOrder
) {
}

record ReviewFormResponseRequest(
        long formId,
        String responseStatus,
        Map<String, Object> answers
) {
}

record ReviewFormResponse(
        long responseId,
        long formId,
        long assignmentId,
        String responseStatus,
        Map<String, Object> answers,
        Timestamp submittedAt
) {
}

record WorkflowFormResponseRequest(
        long formId,
        String responseStatus,
        Map<String, Object> answers
) {
}

record WorkflowFormPackageResponse(
        FormDefinitionResponse form,
        WorkflowFormResponse currentResponse
) {
}

record WorkflowFormResponse(
        long responseId,
        long formId,
        String subjectType,
        long subjectId,
        String responseStatus,
        Map<String, Object> answers,
        Timestamp submittedAt
) {
}

record ReviewFormRevisionResponse(
        long revisionId,
        int revisionNo,
        Map<String, Object> answers,
        Timestamp submittedAt
) {
}

record AuthorFeedbackRequest(
        String feedbackType,
        String feedbackText
) {
}

record AuthorFeedbackResponse(
        long feedbackId,
        long manuscriptId,
        long submittedBy,
        String feedbackType,
        String feedbackText,
        Timestamp createdAt
) {
}

record PaperTagRequest(
        String tagName,
        String tagValue
) {
}

record PaperTagResponse(
        long paperTagId,
        long conferenceId,
        long manuscriptId,
        String tagName,
        String tagValue
) {
}

record PaperRoleRequest(
        long userId,
        String roleType
) {
}

record PaperRoleResponse(
        long paperRoleId,
        long conferenceId,
        long manuscriptId,
        long userId,
        String roleType
) {
}

record TagImportPreviewRequest(String csvText) {
}

record BulkReviewerInvitationImportPreviewRequest(String csvText) {
}

record BulkMatchingScoreImportPreviewRequest(String csvText) {
}

record ImportPreviewResponse(
        long batchId,
        int rowCount,
        int validRowCount,
        int errorCount
) {
}

record ImportConfirmResponse(
        long batchId,
        int appliedCount
) {
}

record TagImportPreviewDocument(
        List<TagImportValidRow> validRows,
        List<TagImportErrorRow> errorRows
) {
}

record BulkReviewerInvitationImportDocument(
        List<BulkReviewerInvitationValidRow> validRows,
        List<ImportErrorRow> errorRows
) {
}

record BulkMatchingScoreImportDocument(
        long manuscriptId,
        List<BulkMatchingScoreValidRow> validRows,
        List<ImportErrorRow> errorRows
) {
}

record TagImportValidRow(
        int rowNumber,
        long manuscriptId,
        String tagName,
        String tagValue
) {
}

record TagImportErrorRow(
        int rowNumber,
        String rawLine,
        String error
) {
}

record ImportErrorRow(
        int rowNumber,
        String rawLine,
        String error
) {
}

record BulkReviewerInvitationValidRow(
        int rowNumber,
        long reviewerId,
        String invitationMessage,
        Instant expiresAt
) {
}

record BulkMatchingScoreValidRow(
        int rowNumber,
        long reviewerId,
        String scoreSource,
        double matchingScore,
        String rationale
) {
}

record ReviewerInvitationRequest(
        long reviewerId,
        String invitationMessage,
        Instant expiresAt
) {
}

record ReviewerInvitationResponse(
        long invitationId,
        long conferenceId,
        long reviewerId,
        String invitationStatus
) {
}

record ExternalDelegationRequest(
        String externalName,
        String externalEmail,
        String rationale
) {
}

record ExternalDelegationDecisionRequest(String decisionNote) {
}

record ExternalDelegationResponse(
        long delegationId,
        long assignmentId,
        long manuscriptId,
        String externalName,
        String externalEmail,
        String delegationStatus
) {
}

record ConflictRelationshipRequest(
        long reviewerId,
        String conflictType,
        String conflictSource,
        String severity,
        String note
) {
}

record ConflictRelationshipResponse(
        long conflictRelationshipId,
        long manuscriptId,
        long reviewerId,
        String conflictType,
        String conflictSource,
        String severity
) {
}

record ReviewerMatchingScoreRequest(
        long reviewerId,
        String scoreSource,
        Double matchingScore,
        String rationale
) {
}

record ReviewerMatchingScoreResponse(
        long matchingScoreId,
        long manuscriptId,
        long reviewerId,
        double matchingScore
) {
}

record AssignmentProposalRequest(String proposalName, Integer limit) {
}

record AssignmentProposalBundleResponse(
        long bundleId,
        long roundId,
        long manuscriptId,
        int proposalCount
) {
}

record AssignmentProposalConfirmDraftsResponse(
        long bundleId,
        int createdCount
) {
}

record AssignmentOverrideRequest(
        long reviewerId,
        String overrideReason
) {
}

record AssignmentOverrideResponse(
        long overrideId,
        long bundleId,
        long reviewerId
) {
}

record EmailTemplateRequest(
        String templateKey,
        String subjectTemplate,
        String bodyTemplate
) {
}

record EmailTemplateResponse(
        long templateId,
        long templateVersionId,
        long conferenceId,
        String templateKey
) {
}

record EmailTemplatePreviewRequest(Map<String, Object> variables) {
}

record EmailTemplatePreviewResponse(String subject, String body) {
}

record EmailTemplateTestSendRequest(
        String recipientEmail,
        Map<String, Object> variables
) {
}

record EmailTemplateTestSendResponse(
        long emailHistoryId,
        String deliveryStatus
) {
}

record OfflineReviewPreviewRequest(String csvText) {
}

record OfflineReviewTemplateResponse(
        long assignmentId,
        String csvHeader,
        String sampleRow
) {
}

record OfflineReviewPreviewResponse(
        long batchId,
        int rowCount,
        int validRowCount,
        int errorCount
) {
}

record OfflineReviewConfirmResponse(
        long batchId,
        int appliedCount
) {
}

record OfflineReviewValidRow(
        int rowNumber,
        int overallScore,
        String recommendation,
        String commentsToAuthor
) {
}

record CameraReadyFileRequest(
        String fileName,
        Long fileSize,
        String checksumSha256,
        Boolean copyrightConfirmed,
        String licenseType
) {
}

record CameraReadyFileResponse(
        long cameraReadyFileId,
        long manuscriptId,
        String fileStatus
) {
}

record CameraReadyDecisionRequest(String decisionNote) {
}

record PublicationMetadataRequest(
        String doi,
        String indexKeywords,
        String publicationStatus
) {
}

record PublicationMetadataResponse(
        long publicationMetadataId,
        long manuscriptId,
        String publicationStatus
) {
}

record ProceedingsPreviewRequest(String exportName) {
}

record ProceedingsPreviewResponse(
        long exportBatchId,
        long conferenceId,
        int paperCount,
        String exportStatus
) {
}

record ProceedingsExportDownloadMetadataResponse(
        long exportBatchId,
        String exportStatus,
        String downloadFileName,
        String downloadUrl,
        int paperCount
) {
}
