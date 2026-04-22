package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.domain.AnalysisVisibilityLevel;
import java.util.LinkedHashMap;
import java.util.List;
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

  @Test
  void buildsSameKeyForNestedMapsRegardlessOfKeyOrder() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("title", "Robust Review Systems");
    first.put(
        "sections",
        List.of(
            nestedMap("name", "intro", "metrics", nestedMap("precision", 0.9, "recall", 0.8)),
            nestedMap("name", "body", "metrics", nestedMap("recall", 0.8, "precision", 0.9))));

    Map<String, Object> second = new LinkedHashMap<>();
    second.put(
        "sections",
        List.of(
            nestedMap("metrics", nestedMap("recall", 0.8, "precision", 0.9), "name", "intro"),
            nestedMap("metrics", nestedMap("precision", 0.9, "recall", 0.8), "name", "body")));
    second.put("title", "Robust Review Systems");

    String firstKey =
        AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            Map.of("assignmentId", 77L, "versionId", 11L),
            first,
            1);
    String secondKey =
        AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            Map.of("versionId", 11L, "assignmentId", 77L),
            second,
            1);

    assertThat(firstKey).isEqualTo(secondKey);
  }

  private static Map<String, Object> nestedMap(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }
}
