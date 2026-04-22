package com.example.review.analysis.domain;

import java.time.Instant;
import java.util.Objects;

public record AnalysisProjection(
    long projectionId,
    long intentId,
    AnalysisType analysisType,
    AnalysisVisibilityLevel visibilityLevel,
    AnalysisStatus businessStatus,
    String summaryText,
    String redactedResult,
    String rawResultReference,
    boolean superseded,
    Instant updatedAt) {
  public AnalysisProjection {
    Objects.requireNonNull(analysisType, "analysisType");
    Objects.requireNonNull(visibilityLevel, "visibilityLevel");
    Objects.requireNonNull(businessStatus, "businessStatus");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public boolean hasRawReference() {
    return rawResultReference != null && !rawResultReference.isBlank();
  }
}
