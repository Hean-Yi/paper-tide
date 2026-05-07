package com.example.review.platform;

import java.sql.Timestamp;
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
