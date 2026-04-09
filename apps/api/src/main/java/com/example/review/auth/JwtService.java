package com.example.review.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final String issuer;
    private final Duration expiration;

    public JwtService(
            @Value("${review.jwt.secret}") String secret,
            @Value("${review.jwt.issuer}") String issuer,
            @Value("${review.jwt.expiration}") String expiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = Duration.parse(expiration);
    }

    public String generateToken(AuthUserRecord user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.username())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .claim("uid", user.userId())
                .claim("roles", user.roles())
                .signWith(signingKey)
                .compact();
    }

    public AuthTokenClaims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = ((Number) claims.get("uid")).longValue();
        Object rawRoles = claims.get("roles");
        List<String> roles = rawRoles instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();

        return new AuthTokenClaims(userId, claims.getSubject(), roles);
    }

    public record AuthTokenClaims(Long userId, String username, List<String> roles) {
    }
}
