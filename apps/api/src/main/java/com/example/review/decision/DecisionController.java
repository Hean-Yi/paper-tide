package com.example.review.decision;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {
    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
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
}
