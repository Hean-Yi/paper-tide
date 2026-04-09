package com.example.review.manuscript;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.List;

record CreateManuscriptRequest(
        String title,
        @JsonProperty("abstract") String abstractText,
        String keywords,
        String blindMode,
        List<AuthorRequest> authors
) {
}

record CreateVersionRequest(
        String title,
        @JsonProperty("abstract") String abstractText,
        String keywords,
        List<AuthorRequest> authors
) {
}

record AuthorRequest(
        String authorName,
        String email,
        String institution,
        Integer authorOrder,
        Long userId,
        Boolean isCorresponding,
        Boolean isExternal
) {
}

record ManuscriptResponse(
        long manuscriptId,
        long submitterId,
        long currentVersionId,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String lastDecisionCode,
        String currentVersionTitle,
        int currentVersionNo
) {
}

record ManuscriptSummaryResponse(
        long manuscriptId,
        long currentVersionId,
        String currentStatus,
        int currentRoundNo,
        String blindMode,
        Timestamp submittedAt,
        String lastDecisionCode,
        String currentVersionTitle,
        int currentVersionNo
) {
}

record VersionSummaryResponse(
        long versionId,
        int versionNo,
        String versionType,
        String title,
        Timestamp submittedAt,
        String pdfFileName,
        Long pdfFileSize
) {
}
