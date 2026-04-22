package com.example.review.analysis.domain;

public enum AnalysisVisibilityLevel {
  NONE,
  REDACTED_ONLY,
  RAW_AND_REDACTED;

  public boolean allowsRaw() {
    return this == RAW_AND_REDACTED;
  }

  public boolean allowsRedacted() {
    return this == REDACTED_ONLY || this == RAW_AND_REDACTED;
  }
}
