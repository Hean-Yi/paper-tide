package com.example.review.health;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/liveness")
    public Map<String, String> liveness() {
        return Map.of("status", "alive");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", Integer.class);
            if (result != null && result == 1) {
                return ResponseEntity.ok(Map.of("status", "ready", "database", "ok"));
            }
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "not_ready", "database", "unavailable"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "not_ready", "database", "unexpected"));
    }
}
