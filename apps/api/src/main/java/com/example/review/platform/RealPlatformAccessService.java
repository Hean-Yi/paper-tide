package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformAccessService {
    private final RealPlatformRepository repository;

    public RealPlatformAccessService(@Qualifier("realPlatformRepository") RealPlatformRepository repository) {
        this.repository = repository;
    }

    public PlatformConferenceRow requireConferenceOperator(CurrentUserPrincipal principal, long conferenceId) {
        RoleGuard.requireChairOrAdmin(principal);
        PlatformConferenceRow conference = repository.findConference(conferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference not found"));
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, conference.organizerUserId())) {
            return conference;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conference operator access is not allowed");
    }

    public PlatformManuscriptRow requireManuscriptOperator(CurrentUserPrincipal principal, long manuscriptId) {
        RoleGuard.requireChairOrAdmin(principal);
        PlatformManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, manuscript.organizerUserId())) {
            return manuscript;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manuscript operator access is not allowed");
    }

    public PlatformAssignmentRow requireReviewerAssignment(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer assignment access is not allowed");
        }
        return assignment;
    }

    public boolean canSeeAuthorFeedback(CurrentUserPrincipal principal, PlatformManuscriptRow manuscript) {
        return RoleGuard.hasRole(principal, "ADMIN")
                || isConferenceOrganizer(principal, manuscript.organizerUserId())
                || (RoleGuard.hasRole(principal, "AUTHOR") && manuscript.submitterId() == principal.userId());
    }

    private boolean isConferenceOrganizer(CurrentUserPrincipal principal, Long organizerUserId) {
        return RoleGuard.hasRole(principal, "CHAIR")
                && organizerUserId != null
                && organizerUserId.equals(principal.userId());
    }
}
