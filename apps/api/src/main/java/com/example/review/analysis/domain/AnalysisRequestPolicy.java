package com.example.review.analysis.domain;

public final class AnalysisRequestPolicy {
  private AnalysisRequestPolicy() {
  }

  public static boolean allows(AnalysisType analysisType, AnalysisBusinessAnchor businessAnchor) {
    if (analysisType == null || businessAnchor == null) {
      return false;
    }
    return analysisType.businessAnchorType() == businessAnchor.businessAnchorType();
  }

  public static boolean allows(AnalysisType analysisType, AnalysisAnchorType businessAnchorType) {
    if (analysisType == null || businessAnchorType == null) {
      return false;
    }
    return analysisType.businessAnchorType() == businessAnchorType;
  }
}
