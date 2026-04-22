package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisAnchorType;
import com.example.review.analysis.domain.AnalysisBusinessAnchor;
import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisRequestPolicy;
import com.example.review.analysis.domain.AnalysisStatus;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.domain.AnalysisVisibilityLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisDomainTest {
  @Test
  void buildsStableIdempotencyKeyFromBusinessAnchor() {
    String key =
        AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            AnalysisBusinessAnchor.assignment(77L),
            Map.of("title", "Robust Review Systems"),
            1);

    assertThat(key).startsWith("REVIEWER_ASSIST:");
    assertThat(key).contains(":businessAnchorType=ASSIGNMENT:");
  }

  @Test
  void reviewerCannotSeeRawProjection() {
    assertThat(AnalysisVisibilityLevel.REDACTED_ONLY.allowsRaw()).isFalse();
    assertThat(AnalysisVisibilityLevel.RAW_AND_REDACTED.allowsRaw()).isTrue();
  }

  @Test
  void analysisStatusUsesTypedLifecycle() {
    assertThat(AnalysisStatus.REQUESTED.isActive()).isTrue();
    assertThat(AnalysisStatus.SUPERSEDED.isActive()).isFalse();
  }

  @Test
  void screeningUsesVersionScopedAnchorType() {
    assertThat(AnalysisType.SCREENING.businessAnchorType()).isEqualTo(AnalysisAnchorType.MANUSCRIPT_VERSION);
  }

  @Test
  void screeningPolicyAcceptsVersionScopedBusinessAnchor() {
    assertThat(AnalysisRequestPolicy.allows(AnalysisType.SCREENING, AnalysisBusinessAnchor.screening(77L, 11L)))
        .isTrue();
    assertThat(AnalysisRequestPolicy.allows(AnalysisType.SCREENING, AnalysisBusinessAnchor.manuscript(77L)))
        .isFalse();
  }

  @Test
  void idempotencyKeyFactoryRejectsMismatchedBusinessAnchor() {
    assertThatThrownBy(() ->
        AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            AnalysisBusinessAnchor.screening(77L, 11L),
            Map.of("title", "Robust Review Systems"),
            1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not allowed for analysisType REVIEWER_ASSIST");
  }

  @Test
  void schemaDeclaresCompositeAnchorLookupAndUpdateMaintenance() throws IOException {
    String schema = Files.readString(Path.of("..", "..", "database", "oracle", "008_agent_platform_refactor.sql"));

    assertThat(schema).contains("BUSINESS_ANCHOR_VERSION_ID NUMBER(19)");
    assertThat(schema).contains("IDX_ANALYSIS_INTENT_ANCHOR_LOOKUP");
    assertThat(schema).contains("TRG_ANALYSIS_PROJECTION_BU");
    assertThat(schema).contains("TRG_EXECUTION_JOB_BU");
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
