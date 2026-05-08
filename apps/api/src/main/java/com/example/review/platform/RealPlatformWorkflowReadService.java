package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformWorkflowReadService {
    private final RealPlatformWorkflowReadRepository repository;
    private final RealPlatformRepository baseRepository;

    public RealPlatformWorkflowReadService(
            RealPlatformWorkflowReadRepository repository,
            @Qualifier("realPlatformRepository") RealPlatformRepository baseRepository
    ) {
        this.repository = repository;
        this.baseRepository = baseRepository;
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

    public List<ReviewFormRevisionResponse> reviewFormRevisions(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        ReviewerFormAccessRow access = repository.findReviewerFormAccess(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (access.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        return repository.listReviewFormRevisions(assignmentId);
    }

    public WorkflowFormPackageResponse activeConferenceForm(CurrentUserPrincipal principal, long conferenceId, String formType) {
        String normalizedType = normalizeFormType(formType);
        if ("META_REVIEW".equals(normalizedType)) {
            RoleGuard.requireChairOrAdmin(principal);
        }
        FormDefinitionResponse form = repository.findActiveForm(conferenceId, normalizedType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active form not found"));
        return new WorkflowFormPackageResponse(form, null);
    }

    public WorkflowFormPackageResponse manuscriptForm(CurrentUserPrincipal principal, long manuscriptId, String formType) {
        String normalizedType = normalizeFormType(formType);
        PlatformManuscriptRow manuscript = baseRepository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (!canSeeManuscriptForm(principal, manuscript, normalizedType)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manuscript form access is not allowed");
        }
        FormDefinitionResponse form = repository.findActiveForm(manuscript.conferenceId(), normalizedType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active form not found"));
        WorkflowFormResponse response = repository.findWorkflowFormResponse(manuscript.manuscriptId(), form.formId()).orElse(null);
        return new WorkflowFormPackageResponse(form, response);
    }

    private boolean canSeeManuscriptForm(CurrentUserPrincipal principal, PlatformManuscriptRow manuscript, String formType) {
        if (RoleGuard.hasRole(principal, "ADMIN")) {
            return true;
        }
        if (RoleGuard.hasRole(principal, "CHAIR")
                && manuscript.organizerUserId() != null
                && manuscript.organizerUserId().equals(principal.userId())) {
            return true;
        }
        return RoleGuard.hasRole(principal, "AUTHOR")
                && manuscript.submitterId() == principal.userId()
                && List.of("SUBMISSION", "AUTHOR_FEEDBACK", "CAMERA_READY").contains(formType);
    }

    private String normalizeFormType(String formType) {
        String text = formType == null ? "" : formType.strip().toUpperCase();
        if (!List.of("SUBMISSION", "REVIEW", "META_REVIEW", "AUTHOR_FEEDBACK", "CAMERA_READY").contains(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formType is invalid");
        }
        return text;
    }
}
