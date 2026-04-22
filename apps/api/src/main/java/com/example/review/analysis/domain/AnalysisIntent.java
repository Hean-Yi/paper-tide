package com.example.review.analysis.domain;

import java.time.Instant;
import java.util.Objects;

public record AnalysisIntent(
    long intentId,
    AnalysisType analysisType,
    AnalysisBusinessAnchor businessAnchor,
    long requestedBy,
    String idempotencyKey,
    AnalysisStatus businessStatus,
    String executionJobId,
    Instant createdAt) {
  public AnalysisIntent {
    Objects.requireNonNull(analysisType, "analysisType");
    Objects.requireNonNull(businessAnchor, "businessAnchor");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(businessStatus, "businessStatus");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public boolean isActive() {
    return businessStatus.isActive();
  }
}
