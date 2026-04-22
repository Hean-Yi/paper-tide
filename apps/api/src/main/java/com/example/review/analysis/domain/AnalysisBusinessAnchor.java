package com.example.review.analysis.domain;

import java.util.Objects;

public record AnalysisBusinessAnchor(
    AnalysisAnchorType businessAnchorType,
    long businessAnchorId,
    Long businessAnchorVersionId) {
  public AnalysisBusinessAnchor {
    Objects.requireNonNull(businessAnchorType, "businessAnchorType");
    if (businessAnchorType.requiresSecondaryId() && businessAnchorVersionId == null) {
      throw new IllegalArgumentException(
          "businessAnchorVersionId is required for " + businessAnchorType);
    }
    if (!businessAnchorType.requiresSecondaryId() && businessAnchorVersionId != null) {
      throw new IllegalArgumentException(
          "businessAnchorVersionId must be null for " + businessAnchorType);
    }
  }

  public static AnalysisBusinessAnchor assignment(long assignmentId) {
    return new AnalysisBusinessAnchor(AnalysisAnchorType.ASSIGNMENT, assignmentId, null);
  }

  public static AnalysisBusinessAnchor round(long roundId) {
    return new AnalysisBusinessAnchor(AnalysisAnchorType.ROUND, roundId, null);
  }

  public static AnalysisBusinessAnchor manuscript(long manuscriptId) {
    return new AnalysisBusinessAnchor(AnalysisAnchorType.MANUSCRIPT, manuscriptId, null);
  }

  public static AnalysisBusinessAnchor screening(long manuscriptId, long versionId) {
    return new AnalysisBusinessAnchor(AnalysisAnchorType.MANUSCRIPT_VERSION, manuscriptId, versionId);
  }

  public boolean isComposite() {
    return businessAnchorVersionId != null;
  }
}
