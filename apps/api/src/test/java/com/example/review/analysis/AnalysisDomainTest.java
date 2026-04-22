package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.domain.AnalysisVisibilityLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisDomainTest {
  @Test
  void buildsStableIdempotencyKeyFromBusinessAnchor() {
    String key =
        AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            Map.of("assignmentId", 77L, "versionId", 11L),
            Map.of("title", "Robust Review Systems"),
            1);

    assertThat(key).startsWith("REVIEWER_ASSIST:");
    assertThat(key).contains(":assignmentId=77:");
  }

  @Test
  void reviewerCannotSeeRawProjection() {
    assertThat(AnalysisVisibilityLevel.REDACTED_ONLY.allowsRaw()).isFalse();
    assertThat(AnalysisVisibilityLevel.RAW_AND_REDACTED.allowsRaw()).isTrue();
  }
}
