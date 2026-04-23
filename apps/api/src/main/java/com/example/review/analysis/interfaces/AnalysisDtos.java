package com.example.review.analysis.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class AnalysisDtos {
    private AnalysisDtos() {
    }

    public record ReviewerAssistRequest(Boolean force) {
        public boolean forceRequested() {
            return Boolean.TRUE.equals(force);
        }
    }

    public record AnalysisIntentResponse(
            long intentId,
            String analysisType,
            String businessStatus
    ) {
    }

    public record AnalysisProjectionResponse(
            long projectionId,
            String analysisType,
            String businessStatus,
            String summaryText,
            JsonNode redactedResult,
            boolean superseded,
            Instant updatedAt
    ) {
    }

    public record ReviewerAssistStateResponse(
            AnalysisIntentResponse intent,
            List<AnalysisProjectionResponse> projections
    ) {
    }
}
