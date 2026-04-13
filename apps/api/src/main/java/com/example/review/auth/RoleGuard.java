package com.example.review.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RoleGuard {
    private RoleGuard() {
    }

    public static boolean hasRole(CurrentUserPrincipal principal, String role) {
        return principal != null && principal.roles().contains(role);
    }

    public static void requireRole(CurrentUserPrincipal principal, String role) {
        if (!hasRole(principal, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role + " role is required");
        }
    }

    public static void requireAnyRole(CurrentUserPrincipal principal, String... roles) {
        for (String role : roles) {
            if (hasRole(principal, role)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, String.join(" or ", roles) + " role is required");
    }

    public static void requireChairOrAdmin(CurrentUserPrincipal principal) {
        requireAnyRole(principal, "CHAIR", "ADMIN");
    }
}
