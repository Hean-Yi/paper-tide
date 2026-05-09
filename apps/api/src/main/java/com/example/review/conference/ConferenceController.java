package com.example.review.conference;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ConferenceController {
    private final ConferenceService conferenceService;

    public ConferenceController(ConferenceService conferenceService) {
        this.conferenceService = conferenceService;
    }

    @GetMapping("/conferences/cfp")
    public List<ConferenceSummary> listPublicCfps() {
        return conferenceService.listPublicCfps();
    }

    @GetMapping("/conferences/cfp/{publicSlug}")
    public ConferenceDetail getPublicCfp(@PathVariable String publicSlug) {
        try {
            return conferenceService.getPublicCfp(publicSlug);
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/chair/conferences")
    public ConferenceDetail createDraft(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ConferenceDraftRequest request
    ) {
        try {
            return conferenceService.createDraft(principal, request);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/chair/conferences")
    public List<ConferenceSummary> listManageableConferences(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.listManageableConferences(principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @GetMapping("/chair/conferences/{conferenceId}")
    public ConferenceDetail getManageableConference(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.getManageableConference(conferenceId, principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/chair/conferences/{conferenceId}")
    public ConferenceDetail updateDraft(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ConferenceDraftRequest request
    ) {
        try {
            return conferenceService.updateDraft(conferenceId, principal, request);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/chair/conferences/{conferenceId}/submit-approval")
    public ConferenceDetail submitForApproval(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.submitForApproval(conferenceId, principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/chair/conferences/{conferenceId}/advance")
    public ConferenceDetail advanceStatus(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ConferenceStatusTransitionRequest request
    ) {
        try {
            return conferenceService.advanceStatus(conferenceId, principal, request.status());
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/admin/conferences/pending")
    public List<ConferenceSummary> listPendingConferenceApprovals(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.listPendingApproval(principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @GetMapping("/admin/conferences/{conferenceId}")
    public ConferenceDetail getConferenceForApproval(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.getAdminConference(conferenceId, principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/admin/conferences/{conferenceId}/approve")
    public ConferenceDetail approveConference(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return conferenceService.approveConference(conferenceId, principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/admin/conferences/{conferenceId}/reject")
    public ConferenceDetail rejectConference(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ConferenceRejectionRequest request
    ) {
        try {
            return conferenceService.rejectConference(conferenceId, principal, request);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
