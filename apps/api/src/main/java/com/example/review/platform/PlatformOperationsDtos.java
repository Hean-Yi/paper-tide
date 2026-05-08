package com.example.review.platform;

import java.sql.Timestamp;
import java.util.List;

record AssignmentOperationsResponse(
        long conferenceId,
        List<ReviewerInvitationOperationRow> reviewerInvitations,
        List<ExternalDelegationOperationRow> externalDelegations,
        List<ImportBatchOperationRow> importBatches,
        List<AssignmentProposalOperationRow> assignmentProposals,
        List<MatchingScoreOperationRow> matchingScores
) {
}

record ReviewerInvitationOperationRow(
        long invitationId,
        long reviewerId,
        String invitationStatus,
        Timestamp invitedAt,
        Timestamp expiresAt
) {
}

record ExternalDelegationOperationRow(
        long delegationId,
        long assignmentId,
        long manuscriptId,
        String externalName,
        String externalEmail,
        String delegationStatus,
        Timestamp requestedAt
) {
}

record ImportBatchOperationRow(
        long batchId,
        String importType,
        String batchStatus,
        int rowCount,
        int validRowCount,
        int errorCount,
        Timestamp createdAt
) {
}

record AssignmentProposalOperationRow(
        long bundleId,
        long roundId,
        long manuscriptId,
        String proposalName,
        String bundleStatus,
        int proposalCount,
        Timestamp createdAt
) {
}

record MatchingScoreOperationRow(
        long matchingScoreId,
        long manuscriptId,
        long reviewerId,
        String scoreSource,
        double matchingScore,
        String rationale,
        Timestamp importedAt
) {
}

record PublicationOperationsResponse(
        long conferenceId,
        List<EmailTemplateOperationRow> emailTemplates,
        List<EmailHistoryOperationRow> emailHistory,
        List<OfflineReviewImportOperationRow> offlineReviewImports,
        List<CameraReadyFileOperationRow> cameraReadyFiles,
        List<PublicationMetadataOperationRow> publicationMetadata,
        List<ProceedingsExportOperationRow> proceedingsExports
) {
}

record EmailTemplateOperationRow(
        long templateId,
        Long activeVersionId,
        String templateKey,
        Timestamp createdAt
) {
}

record EmailHistoryOperationRow(
        long emailHistoryId,
        String templateKey,
        String recipientEmail,
        String deliveryStatus,
        Timestamp createdAt
) {
}

record OfflineReviewImportOperationRow(
        long batchId,
        long assignmentId,
        long reviewerId,
        String batchStatus,
        int rowCount,
        int validRowCount,
        int errorCount,
        Timestamp createdAt
) {
}

record CameraReadyFileOperationRow(
        long cameraReadyFileId,
        long manuscriptId,
        String fileName,
        long fileSize,
        String fileStatus,
        Timestamp submittedAt
) {
}

record PublicationMetadataOperationRow(
        long publicationMetadataId,
        long manuscriptId,
        String doi,
        String indexKeywords,
        String publicationStatus,
        Timestamp updatedAt
) {
}

record ProceedingsExportOperationRow(
        long exportBatchId,
        String exportName,
        String exportStatus,
        int paperCount,
        Timestamp createdAt
) {
}

record PlatformConferenceAccessRow(long conferenceId, Long organizerUserId) {
}

record ReviewFormPackageResponse(
        FormDefinitionResponse form,
        ReviewFormResponse currentResponse
) {
}

record ReviewerFormAccessRow(
        long assignmentId,
        long reviewerId,
        long conferenceId
) {
}
