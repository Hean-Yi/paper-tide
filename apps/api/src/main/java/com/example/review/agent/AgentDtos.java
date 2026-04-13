package com.example.review.agent;

import java.util.List;
import java.util.Map;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record CreateAgentTaskRequest(String taskType, Boolean force) {
        boolean forceRequested() {
            return Boolean.TRUE.equals(force);
        }
    }

    public record AgentTaskResponse(
            long taskId,
            String externalTaskId,
            String taskType,
            String taskStatus,
            String step
    ) {
    }

    public record AgentServiceCreateRequest(
            String taskType,
            long manuscriptId,
            long versionId,
            Long roundId,
            Map<String, Object> requestPayload,
            boolean force,
            String fileName,
            byte[] pdfBytes
    ) {
    }

    public record AgentServiceTaskSummary(String taskId, String status, String step) {
    }

    public record AgentServiceTaskStatus(String taskId, String taskType, String status, String step, String error) {
    }

    public record AgentServiceResult(String resultType, Map<String, Object> rawResult, Map<String, Object> redactedResult) {
    }

    public record AgentResultResponse(
            long resultId,
            String resultType,
            Map<String, Object> rawResult,
            Map<String, Object> redactedResult
    ) {
    }

    record AgentVersionData(
            long manuscriptId,
            long versionId,
            String title,
            String abstractText,
            String keywords,
            byte[] pdfFile,
            String pdfFileName,
            Long pdfFileSize
    ) {
        List<String> keywordList() {
            if (keywords == null || keywords.isBlank()) {
                return List.of();
            }
            return List.of(keywords.split("\\s*,\\s*"));
        }
    }
}
