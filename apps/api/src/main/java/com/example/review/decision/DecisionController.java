package com.example.review.decision;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {
    private final DecisionService decisionService;
    private final DecisionPackageService decisionPackageService;

    public DecisionController(DecisionService decisionService, DecisionPackageService decisionPackageService) {
        this.decisionService = decisionService;
        this.decisionPackageService = decisionPackageService;
    }

    @GetMapping
    public Map<String, Object> listPlaceholder() {
        return Map.of("placeholder", false, "resource", "decisions");
    }

    @PostMapping
    public DecisionResponse decide(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody DecisionRequest request
    ) {
        return decisionService.decide(principal, request);
    }

    @PostMapping("/screening-desk-reject")
    public DecisionResponse screeningDeskReject(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ScreeningDeskRejectRequest request
    ) {
        return decisionService.screeningDeskReject(principal, request);
    }

    @GetMapping("/manuscripts/{manuscriptId}/package")
    public DecisionPackageResponse getAuthorDecisionPackage(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId
    ) {
        return decisionPackageService.getAuthorDecisionPackage(principal, manuscriptId);
    }
}
