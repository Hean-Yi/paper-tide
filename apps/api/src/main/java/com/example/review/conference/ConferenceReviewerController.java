package com.example.review.conference;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ConferenceReviewerController {
    private final ConferenceReviewerService reviewerService;

    public ConferenceReviewerController(ConferenceReviewerService reviewerService) {
        this.reviewerService = reviewerService;
    }

    @PostMapping("/chair/conferences/{conferenceId}/reviewers")
    public ConferenceReviewerResponse addReviewer(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ConferenceReviewerRequest request
    ) {
        try {
            return reviewerService.addReviewer(conferenceId, principal, request);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/chair/reviewers/search")
    public List<PlatformReviewerSearchResult> searchReviewers(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(name = "query", required = false) String query
    ) {
        try {
            return reviewerService.searchActivePlatformReviewers(principal, query);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @GetMapping("/reviewer/conferences/{conferenceId}/bids/open")
    public List<ReviewerBiddingItem> listOpenBiddingItems(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return reviewerService.listOpenBiddingItems(conferenceId, principal);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/reviewer/conferences/{conferenceId}/bids")
    public ReviewerBidResponse submitBid(
            @PathVariable long conferenceId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ReviewerBidRequest request
    ) {
        try {
            return reviewerService.submitBid(conferenceId, principal, request);
        } catch (ConferenceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (ConferenceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConferenceStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (ConferenceValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
