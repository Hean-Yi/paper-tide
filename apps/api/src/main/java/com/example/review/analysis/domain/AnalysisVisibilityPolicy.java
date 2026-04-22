package com.example.review.analysis.domain;

public final class AnalysisVisibilityPolicy {
  private AnalysisVisibilityPolicy() {
  }

  public static AnalysisVisibilityLevel resolve(boolean canReadRaw, boolean canReadRedacted) {
    if (canReadRaw) {
      return AnalysisVisibilityLevel.RAW_AND_REDACTED;
    }
    if (canReadRedacted) {
      return AnalysisVisibilityLevel.REDACTED_ONLY;
    }
    return AnalysisVisibilityLevel.NONE;
  }

  public static boolean canReadRaw(AnalysisVisibilityLevel visibilityLevel) {
    return visibilityLevel != null && visibilityLevel.allowsRaw();
  }
}
