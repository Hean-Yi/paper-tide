package com.example.review.analysis.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class AnalysisIdempotencyKeyFactory {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private AnalysisIdempotencyKeyFactory() {
  }

  public static String build(
      AnalysisType analysisType,
      Map<String, Object> businessAnchor,
      Map<String, Object> normalizedInput,
      int requestVersion) {
    Objects.requireNonNull(analysisType, "analysisType");
    Objects.requireNonNull(businessAnchor, "businessAnchor");
    Objects.requireNonNull(normalizedInput, "normalizedInput");
    if (requestVersion < 1) {
      throw new IllegalArgumentException("requestVersion must be positive");
    }

    try {
      String anchorText =
          new TreeMap<>(businessAnchor).entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(Collectors.joining(":"));
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(OBJECT_MAPPER.writeValueAsString(new TreeMap<>(normalizedInput)).getBytes(StandardCharsets.UTF_8));
      String hash = HexFormat.of().formatHex(digest);
      return analysisType.name() + ":" + anchorText + ":v" + requestVersion + ":" + hash;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build analysis idempotency key", ex);
    }
  }
}
