package com.example.review.analysis.domain;

public enum AnalysisStatus {
  REQUESTED,
  AVAILABLE,
  FAILED_VISIBLE,
  SUPERSEDED;

  public boolean isActive() {
    return this == REQUESTED || this == AVAILABLE;
  }
}
