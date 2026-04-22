package com.example.review.analysis.domain;

public final class AnalysisRequestPolicy {
  private AnalysisRequestPolicy() {
  }

  public static boolean allows(AnalysisType analysisType, String businessAnchorType) {
    if (analysisType == null || businessAnchorType == null) {
      return false;
    }
    return analysisType.businessAnchorType().equalsIgnoreCase(businessAnchorType);
  }
}
