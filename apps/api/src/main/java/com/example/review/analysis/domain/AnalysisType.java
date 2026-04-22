package com.example.review.analysis.domain;

public enum AnalysisType {
  REVIEWER_ASSIST(AnalysisAnchorType.ASSIGNMENT),
  CONFLICT_ANALYSIS(AnalysisAnchorType.ROUND),
  SCREENING(AnalysisAnchorType.MANUSCRIPT_VERSION);

  private final AnalysisAnchorType businessAnchorType;

  AnalysisType(AnalysisAnchorType businessAnchorType) {
    this.businessAnchorType = businessAnchorType;
  }

  public AnalysisAnchorType businessAnchorType() {
    return businessAnchorType;
  }
}
