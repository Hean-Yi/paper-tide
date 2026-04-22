package com.example.review.analysis.domain;

public enum AnalysisType {
  REVIEWER_ASSIST("ASSIGNMENT"),
  CONFLICT_ANALYSIS("ROUND"),
  SCREENING("MANUSCRIPT");

  private final String businessAnchorType;

  AnalysisType(String businessAnchorType) {
    this.businessAnchorType = businessAnchorType;
  }

  public String businessAnchorType() {
    return businessAnchorType;
  }
}
