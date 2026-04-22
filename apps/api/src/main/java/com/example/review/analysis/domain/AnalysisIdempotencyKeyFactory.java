package com.example.review.analysis.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
      AnalysisBusinessAnchor businessAnchor,
      Map<String, Object> normalizedInput,
      int requestVersion) {
    Objects.requireNonNull(analysisType, "analysisType");
    Objects.requireNonNull(businessAnchor, "businessAnchor");
    if (!AnalysisRequestPolicy.allows(analysisType, businessAnchor)) {
      throw new IllegalArgumentException(
          "businessAnchor "
              + businessAnchor.businessAnchorType()
              + " is not allowed for analysisType "
              + analysisType);
    }
    Map<String, Object> anchorPayload = new LinkedHashMap<>();
    anchorPayload.put("businessAnchorType", businessAnchor.businessAnchorType().name());
    anchorPayload.put("businessAnchorId", businessAnchor.businessAnchorId());
    if (businessAnchor.businessAnchorVersionId() != null) {
      anchorPayload.put("businessAnchorVersionId", businessAnchor.businessAnchorVersionId());
    }
    return build(
        analysisType,
        anchorPayload,
        normalizedInput,
        requestVersion);
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
      String anchorText = renderCanonicalValue(canonicalize(businessAnchor));
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(OBJECT_MAPPER.writeValueAsString(canonicalize(normalizedInput)).getBytes(StandardCharsets.UTF_8));
      String hash = HexFormat.of().formatHex(digest);
      return analysisType.name() + ":" + anchorText + ":v" + requestVersion + ":" + hash;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build analysis idempotency key", ex);
    }
  }

  private static Object canonicalize(Object value) {
    if (value == null
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>) {
      return value;
    }

    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> canonicalMap = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        canonicalMap.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
      }
      return canonicalMap;
    }

    if (value instanceof Collection<?> collection) {
      return collection.stream().map(AnalysisIdempotencyKeyFactory::canonicalize).toList();
    }

    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      ArrayList<Object> canonicalArray = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        canonicalArray.add(canonicalize(Array.get(value, i)));
      }
      return canonicalArray;
    }

    return value;
  }

  private static String renderCanonicalValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .map(entry -> entry.getKey() + "=" + renderCanonicalValue(entry.getValue()))
          .collect(Collectors.joining(":"));
    }

    if (value instanceof Collection<?> collection) {
      return collection.stream()
          .map(AnalysisIdempotencyKeyFactory::renderCanonicalValue)
          .collect(Collectors.joining(",", "[", "]"));
    }

    return String.valueOf(value);
  }
}
