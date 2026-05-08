package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformOperationsReadService {
    private final RealPlatformOperationsReadRepository repository;

    public RealPlatformOperationsReadService(RealPlatformOperationsReadRepository repository) {
        this.repository = repository;
    }

    public AssignmentOperationsResponse assignmentOperations(CurrentUserPrincipal principal, long conferenceId) {
        requireConferenceOperator(principal, conferenceId);
        return new AssignmentOperationsResponse(
                conferenceId,
                repository.listReviewerInvitations(conferenceId),
                repository.listExternalDelegations(conferenceId),
                repository.listImportBatches(conferenceId),
                repository.listAssignmentProposals(conferenceId),
                repository.listMatchingScores(conferenceId)
        );
    }

    public PublicationOperationsResponse publicationOperations(CurrentUserPrincipal principal, long conferenceId) {
        requireConferenceOperator(principal, conferenceId);
        return new PublicationOperationsResponse(
                conferenceId,
                repository.listEmailTemplates(conferenceId),
                repository.listEmailHistory(conferenceId),
                repository.listOfflineReviewImports(conferenceId),
                repository.listCameraReadyFiles(conferenceId),
                repository.listPublicationMetadata(conferenceId),
                repository.listProceedingsExports(conferenceId)
        );
    }

    private void requireConferenceOperator(CurrentUserPrincipal principal, long conferenceId) {
        RoleGuard.requireChairOrAdmin(principal);
        PlatformConferenceAccessRow conference = repository.findConferenceAccess(conferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference not found"));
        if (RoleGuard.hasRole(principal, "ADMIN")) {
            return;
        }
        if (RoleGuard.hasRole(principal, "CHAIR")
                && conference.organizerUserId() != null
                && conference.organizerUserId().equals(principal.userId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conference operator access is not allowed");
    }
}
