package com.example.review.auth;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record CurrentUserPrincipal(Long userId, String username, List<String> roles) implements Principal {
    @Override
    public String getName() {
        return username;
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
