package com.example.review.analysis.domain;

public enum AnalysisAnchorType {
  ASSIGNMENT(false),
  ROUND(false),
  MANUSCRIPT(false),
  MANUSCRIPT_VERSION(true);

  private final boolean composite;

  AnalysisAnchorType(boolean composite) {
    this.composite = composite;
  }

  public boolean isComposite() {
    return composite;
  }

  public boolean requiresSecondaryId() {
    return composite;
  }
}
