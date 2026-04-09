package com.example.review.placeholder;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedResourcePlaceholderController {
    @GetMapping("/api/audit-logs")
    public Map<String, Object> auditLogs() {
        return Map.of("placeholder", true, "resource", "audit-logs");
    }
}
