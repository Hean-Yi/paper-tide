package com.example.review.platform;

import java.sql.Timestamp;
import java.util.Map;

record PlatformConferenceRow(long conferenceId, Long organizerUserId) {
}

record PlatformManuscriptRow(
        long manuscriptId,
        long submitterId,
        long conferenceId,
        Long currentVersionId,
        String currentStatus,
        Long organizerUserId
) {
}

record PlatformAssignmentRow(
        long assignmentId,
        long manuscriptId,
        long versionId,
        long reviewerId,
        String taskStatus,
        long conferenceId,
        Long organizerUserId
) {
}

record PlatformFormRow(long formId, long conferenceId, String formType, String formName) {
}

record PlatformFormFieldRow(
        long fieldId,
        String fieldKey,
        String fieldLabel,
        String fieldType,
        boolean required,
        String visibility,
        int displayOrder
) {
}

record PlatformReviewFormResponseRow(
        long responseId,
        long formId,
        long assignmentId,
        String responseStatus,
        Map<String, Object> answers,
        Timestamp submittedAt
) {
}

record PlatformAuthorFeedbackRow(
        long feedbackId,
        long manuscriptId,
        long submittedBy,
        String feedbackType,
        String feedbackText,
        Timestamp createdAt
) {
}

record PlatformPaperTagRow(long paperTagId, long conferenceId, long manuscriptId, String tagName, String tagValue) {
}

record PlatformPaperRoleRow(long paperRoleId, long conferenceId, long manuscriptId, long userId, String roleType) {
}

record PlatformImportBatchRow(
        long batchId,
        long conferenceId,
        String importType,
        long submittedBy,
        String batchStatus,
        TagImportPreviewDocument previewDocument
) {
}

record PlatformReviewerInvitationImportBatchRow(
        long batchId,
        long conferenceId,
        String importType,
        long submittedBy,
        String batchStatus,
        BulkReviewerInvitationImportDocument previewDocument
) {
}

record PlatformMatchingScoreImportBatchRow(
        long batchId,
        long conferenceId,
        String importType,
        long submittedBy,
        String batchStatus,
        BulkMatchingScoreImportDocument previewDocument
) {
}

record PlatformReviewRoundRow(long roundId, long manuscriptId, long versionId, long conferenceId, Long organizerUserId) {
}

record PlatformReviewerInvitationRow(
        long invitationId,
        long conferenceId,
        long reviewerId,
        String invitationStatus,
        String invitationMessage,
        long invitedBy,
        Timestamp invitedAt,
        Timestamp respondedAt,
        Timestamp expiresAt
) {
}

record PlatformExternalDelegationRow(
        long delegationId,
        long assignmentId,
        long manuscriptId,
        long requestedBy,
        String externalName,
        String externalEmail,
        String delegationStatus,
        String decisionNote,
        long conferenceId,
        Long organizerUserId
) {
}

record PlatformConflictRelationshipRow(
        long conflictRelationshipId,
        long conferenceId,
        long manuscriptId,
        long reviewerId,
        String conflictType,
        String conflictSource,
        String severity,
        String note
) {
}

record PlatformReviewerCandidateRow(long reviewerId, double matchingScore, String eligibilityStatus) {
}

record PlatformAssignmentProposalBundleRow(
        long bundleId,
        long roundId,
        long conferenceId,
        long manuscriptId,
        String proposalName,
        String bundleStatus,
        long createdBy
) {
}

record PlatformAssignmentProposalRow(
        long proposalId,
        long bundleId,
        long reviewerId,
        int rankOrder,
        double matchingScore,
        String eligibilityStatus,
        String rationale
) {
}

record PlatformProposalReviewerValidationRow(
        long reviewerId,
        int currentLoad,
        int maxLoad,
        int hardConflictCount,
        int roundAssignmentCount,
        int openDraftCount
) {
}

record PlatformEmailTemplateRow(
        long templateId,
        long conferenceId,
        String templateKey,
        long templateVersionId,
        String subjectTemplate,
        String bodyTemplate,
        Long organizerUserId
) {
}

record PlatformOfflineReviewBatchRow(
        long batchId,
        long assignmentId,
        long reviewerId,
        String batchStatus,
        long roundId,
        long manuscriptId,
        long versionId
) {
}

record PlatformOfflineReviewRow(long rowId, int overallScore, String recommendation, String commentsToAuthor) {
}

record PlatformCameraReadyFileRow(
        long cameraReadyFileId,
        long manuscriptId,
        long versionId,
        long submittedBy,
        String fileStatus,
        long conferenceId,
        Long organizerUserId
) {
}

record PlatformProceedingsExportBatchRow(
        long exportBatchId,
        long conferenceId,
        String exportName,
        String exportStatus,
        int paperCount,
        Long organizerUserId
) {
}
