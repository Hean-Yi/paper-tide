package com.example.review.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AuthUserRecord user = authUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if ("PENDING_EMAIL_VERIFICATION".equals(user.status())) {
            authUserRepository.activatePendingEmailVerificationRegistration(user.userId());
            user = authUserRepository.findByUsername(user.username())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        }

        if (!"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new LoginResponse(jwtService.generateToken(user));
    }
}
