package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformWorkflowReadService {
    private final RealPlatformWorkflowReadRepository repository;

    public RealPlatformWorkflowReadService(RealPlatformWorkflowReadRepository repository) {
        this.repository = repository;
    }

    public ReviewFormPackageResponse reviewForm(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        ReviewerFormAccessRow access = repository.findReviewerFormAccess(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (access.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        FormDefinitionResponse form = repository.findActiveReviewForm(access.conferenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active review form not found"));
        ReviewFormResponse response = repository.findReviewFormResponse(access.assignmentId(), form.formId()).orElse(null);
        return new ReviewFormPackageResponse(form, response);
    }
}
