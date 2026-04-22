package com.example.review.analysis.domain;

import java.time.Instant;
import java.util.Objects;

public record AnalysisIntent(
    long intentId,
    AnalysisType analysisType,
    String businessAnchorType,
    long businessAnchorId,
    long requestedBy,
    String idempotencyKey,
    String businessStatus,
    String executionJobId,
    Instant createdAt) {
  public AnalysisIntent {
    Objects.requireNonNull(analysisType, "analysisType");
    Objects.requireNonNull(businessAnchorType, "businessAnchorType");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(businessStatus, "businessStatus");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public boolean isActive() {
    return "REQUESTED".equals(businessStatus) || "AVAILABLE".equals(businessStatus);
  }
}
