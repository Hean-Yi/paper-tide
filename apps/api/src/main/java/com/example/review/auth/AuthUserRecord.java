package com.example.review.auth;

import java.util.List;

public record AuthUserRecord(
        Long userId,
        String username,
        String passwordHash,
        String status,
        List<String> roles
) {
}
