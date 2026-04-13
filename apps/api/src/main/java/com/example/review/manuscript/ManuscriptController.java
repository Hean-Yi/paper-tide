package com.example.review.manuscript;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/manuscripts")
public class ManuscriptController {
    private final ManuscriptService manuscriptService;

    public ManuscriptController(ManuscriptService manuscriptService) {
        this.manuscriptService = manuscriptService;
    }

    @PostMapping
    public ManuscriptResponse createManuscript(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody CreateManuscriptRequest request
    ) {
        return manuscriptService.createManuscript(principal, request);
    }

    @GetMapping
    public List<ManuscriptSummaryResponse> listManuscripts(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return manuscriptService.listManuscripts(principal);
    }

    @GetMapping("/{id}")
    public ManuscriptResponse getManuscript(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id
    ) {
        return manuscriptService.getManuscript(principal, id);
    }

    @PostMapping("/{id}/versions")
    public ManuscriptResponse createVersion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id,
            @RequestBody CreateVersionRequest request
    ) {
        return manuscriptService.createRevision(principal, id, request);
    }

    @GetMapping("/{id}/versions")
    public List<VersionSummaryResponse> listVersions(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id
    ) {
        return manuscriptService.listVersions(principal, id);
    }

    @PostMapping("/{id}/versions/{versionId}/pdf")
    public ResponseEntity<Void> uploadPdf(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id,
            @PathVariable long versionId,
            @RequestParam MultipartFile file
    ) {
        manuscriptService.uploadPdf(principal, id, versionId, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/versions/{versionId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id,
            @PathVariable long versionId
    ) {
        ManuscriptService.PdfPayload pdf = manuscriptService.downloadPdf(principal, id, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString()
                )
                .body(pdf.bytes());
    }

    @PostMapping("/{id}/versions/{versionId}/submit")
    public ManuscriptResponse submitVersion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id,
            @PathVariable long versionId
    ) {
        return manuscriptService.submitVersion(principal, id, versionId);
    }

    @PostMapping("/{id}/versions/{versionId}/start-screening")
    public ManuscriptResponse startScreening(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long id,
            @PathVariable long versionId
    ) {
        return manuscriptService.startScreening(principal, id, versionId);
    }
}
