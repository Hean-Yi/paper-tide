package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RealPlatformController {
    private final RealPlatformService service;

    public RealPlatformController(RealPlatformService service) {
        this.service = service;
    }

    @PostMapping("/conferences/{conferenceId}/forms")
    public FormDefinitionResponse createForm(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody FormDefinitionRequest request
    ) {
        return service.createForm(principal, conferenceId, request);
    }

    @PostMapping("/review-assignments/{assignmentId}/form-response")
    public ReviewFormResponse saveReviewFormResponse(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody ReviewFormResponseRequest request
    ) {
        return service.saveReviewFormResponse(principal, assignmentId, request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/author-feedback")
    public AuthorFeedbackResponse submitAuthorFeedback(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody AuthorFeedbackRequest request
    ) {
        return service.submitAuthorFeedback(principal, manuscriptId, request);
    }

    @GetMapping("/manuscripts/{manuscriptId}/author-feedback")
    public List<AuthorFeedbackResponse> listAuthorFeedback(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId
    ) {
        return service.listAuthorFeedback(principal, manuscriptId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/tags")
    public PaperTagResponse upsertPaperTag(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody PaperTagRequest request
    ) {
        return service.upsertPaperTag(principal, manuscriptId, request);
    }

    @PostMapping("/manuscripts/{manuscriptId}/paper-roles")
    public PaperRoleResponse assignPaperRole(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @RequestBody PaperRoleRequest request
    ) {
        return service.assignPaperRole(principal, manuscriptId, request);
    }

    @PostMapping("/conferences/{conferenceId}/imports/tags/preview")
    public ImportPreviewResponse previewTagImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conferenceId,
            @RequestBody TagImportPreviewRequest request
    ) {
        return service.previewTagImport(principal, conferenceId, request);
    }

    @PostMapping("/imports/{batchId}/confirm")
    public ImportConfirmResponse confirmImport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long batchId
    ) {
        return service.confirmImport(principal, batchId);
    }
}
