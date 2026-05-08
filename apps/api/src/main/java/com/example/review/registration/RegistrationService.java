package com.example.review.registration;

import com.example.review.audit.AuditLogService;
import com.example.review.auth.CurrentUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private static final String EMAIL_VERIFICATION_PURPOSE = "EMAIL_VERIFICATION";
    private final RegistrationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final InMemoryVerificationEmailGateway emailGateway;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository repository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            InMemoryVerificationEmailGateway emailGateway,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.emailGateway = emailGateway;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        RegistrationType type = RegistrationType.from(request.registrationType());
        validateAccountFields(request);
        validateProfileForPrivilegedRegistration(type, request.academicProfile());

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedUsername = request.username().trim();
        Optional<ExistingUserSummary> existing = repository.findUserByEmail(normalizedEmail);

        if (existing.isPresent() && resubmitAllowed(existing.get(), normalizedUsername, type)) {
            return resubmitRejectedApplication(existing.get(), type, request);
        }

        if (repository.usernameExists(normalizedUsername)) {
            throw new RegistrationValidationException("Username is already registered");
        }
        if (existing.isPresent()) {
            throw new RegistrationValidationException("Email is already registered");
        }

        String applicationStatus = initialApplicationStatus(type);
        long userId = repository.createUser(new UserRegistrationDraft(
                normalizedUsername,
                passwordEncoder.encode(request.password()),
                request.realName().trim(),
                normalizedEmail,
                normalizeNullable(request.institution()),
                "ACTIVE"
        ));

        if (request.academicProfile() != null) {
            repository.saveAcademicProfile(userId, request.academicProfile());
        }
        repository.replaceResearchAreas(userId, request.researchAreas() == null ? List.of() : request.researchAreas());

        long applicationId = repository.createRoleApplication(new RoleApplicationDraft(
                userId,
                type.name(),
                applicationStatus,
                payloadSnapshot(request)
        ));
        grantInitialRoleIfEligible(userId, type);

        return new RegistrationResponse(userId, applicationId, type.name(), applicationStatus, false);
    }

    private boolean resubmitAllowed(ExistingUserSummary existing, String normalizedUsername, RegistrationType type) {
        if (!existing.username().equals(normalizedUsername)) {
            return false;
        }
        Optional<RoleApplicationRecord> application = repository.findApplicationByUserAndType(existing.userId(), type.name());
        return application.isPresent() && "REJECTED".equals(application.get().status());
    }

    private RegistrationResponse resubmitRejectedApplication(
            ExistingUserSummary existing,
            RegistrationType type,
            RegistrationRequest request
    ) {
        RoleApplicationRecord application = repository.findApplicationByUserAndType(existing.userId(), type.name())
                .orElseThrow(() -> new RegistrationValidationException("Role application was not found"));
        String normalizedEmail = existing.email().toLowerCase(Locale.ROOT);

        repository.resetUserForResubmit(
                existing.userId(),
                passwordEncoder.encode(request.password()),
                request.realName().trim(),
                normalizeNullable(request.institution())
        );
        repository.activateUser(existing.userId());
        if (request.academicProfile() != null) {
            repository.saveAcademicProfile(existing.userId(), request.academicProfile());
        }
        repository.replaceResearchAreas(existing.userId(),
                request.researchAreas() == null ? List.of() : request.researchAreas());
        String applicationStatus = initialApplicationStatus(type);
        repository.resetRoleApplicationToPending(application.applicationId(), payloadSnapshot(request));
        if (!"PENDING_EMAIL_VERIFICATION".equals(applicationStatus)) {
            repository.updateRoleApplicationStatus(application.applicationId(), applicationStatus, null, null);
        }
        repository.clearUnconsumedVerificationTokens(application.applicationId());
        grantInitialRoleIfEligible(existing.userId(), type);

        return new RegistrationResponse(existing.userId(), application.applicationId(),
                type.name(), applicationStatus, false);
    }

    private void issueVerificationEmail(long userId, long applicationId, String normalizedEmail) {
        String rawToken = newRawToken();
        Instant expiresAt = Instant.now(clock).plus(24, ChronoUnit.HOURS);
        repository.createEmailVerificationToken(new EmailVerificationTokenDraft(
                userId,
                applicationId,
                hashToken(rawToken),
                EMAIL_VERIFICATION_PURPOSE,
                expiresAt
        ));
        emailGateway.sendVerificationEmail(new VerificationEmailMessage(
                userId,
                applicationId,
                normalizedEmail,
                rawToken,
                expiresAt
        ));
    }

    @Transactional
    public EmailVerificationResponse verifyEmail(String rawToken) {
        if (isBlank(rawToken)) {
            throw new RegistrationValidationException("Verification token is required");
        }

        EmailVerificationTokenRecord token = repository.findVerificationToken(hashToken(rawToken))
                .orElseThrow(() -> new RegistrationValidationException("Invalid verification token"));
        if (token.expiresAt().isBefore(Instant.now(clock))) {
            throw new RegistrationValidationException("Verification token has expired");
        }

        RoleApplicationRecord application = repository.findRoleApplication(token.roleApplicationId())
                .orElseThrow(() -> new RegistrationNotFoundException("Role application was not found"));
        RegistrationType type = RegistrationType.from(application.registrationType());

        if (!repository.consumeVerificationToken(token.tokenId())) {
            throw new RegistrationValidationException("Verification token has already been used");
        }
        repository.activateUser(token.userId());

        String nextStatus;
        if (type == RegistrationType.AUTHOR) {
            nextStatus = "APPROVED";
            repository.updateRoleApplicationStatus(application.applicationId(), nextStatus, null, null);
            repository.grantRole(application.userId(), "AUTHOR");
        } else {
            nextStatus = "PENDING_ADMIN_APPROVAL";
            repository.updateRoleApplicationStatus(application.applicationId(), nextStatus, null, null);
        }

        return new EmailVerificationResponse(application.userId(), application.applicationId(), type.name(), "ACTIVE", nextStatus);
    }

    public List<RoleApplicationRecord> listPendingAdminApplications(CurrentUserPrincipal principal) {
        requireAdmin(principal);
        return repository.listPendingAdminApplications();
    }

    public List<RoleApplicationDetail> listPendingAdminApplicationDetails(CurrentUserPrincipal principal) {
        requireAdmin(principal);
        return repository.listPendingAdminApplicationDetails();
    }

    @Transactional
    public ApplicationReviewResponse approveApplication(
            long applicationId,
            CurrentUserPrincipal principal,
            String rejectionReason
    ) {
        requireAdmin(principal);
        RoleApplicationRecord application = repository.findRoleApplication(applicationId)
                .orElseThrow(() -> new RegistrationNotFoundException("Role application was not found"));
        if (!"PENDING_ADMIN_APPROVAL".equals(application.status())) {
            throw new RegistrationValidationException("Only pending admin applications can be approved");
        }

        RegistrationType type = RegistrationType.from(application.registrationType());
        if (type == RegistrationType.AUTHOR) {
            throw new RegistrationValidationException("Author applications are approved by email verification");
        }

        String roleCode = type.grantedRole();
        repository.updateRoleApplicationStatus(applicationId, "APPROVED", principal.userId(), rejectionReason);
        repository.grantRole(application.userId(), roleCode);
        auditLogService.recordRoleApproval(
                principal.userId(),
                applicationId,
                application.userId(),
                type.name(),
                "ROLE_APPROVED",
                null
        );
        return new ApplicationReviewResponse(applicationId, application.userId(), type.name(), "APPROVED", roleCode);
    }

    @Transactional
    public ApplicationReviewResponse rejectApplication(
            long applicationId,
            CurrentUserPrincipal principal,
            String rejectionReason
    ) {
        requireAdmin(principal);
        if (isBlank(rejectionReason)) {
            throw new RegistrationValidationException("Rejection reason is required");
        }
        RoleApplicationRecord application = repository.findRoleApplication(applicationId)
                .orElseThrow(() -> new RegistrationNotFoundException("Role application was not found"));
        if (!"PENDING_ADMIN_APPROVAL".equals(application.status())) {
            throw new RegistrationValidationException("Only pending admin applications can be rejected");
        }

        String trimmedReason = rejectionReason.trim();
        repository.updateRoleApplicationStatus(applicationId, "REJECTED", principal.userId(), trimmedReason);
        auditLogService.recordRoleApproval(
                principal.userId(),
                applicationId,
                application.userId(),
                application.registrationType(),
                "ROLE_REJECTED",
                trimmedReason
        );
        return new ApplicationReviewResponse(applicationId, application.userId(), application.registrationType(), "REJECTED", null);
    }

    private void validateAccountFields(RegistrationRequest request) {
        if (isBlank(request.username()) || isBlank(request.password()) || isBlank(request.realName()) || isBlank(request.email())) {
            throw new RegistrationValidationException("Username, password, real name, and email are required");
        }
        if (request.password().length() < 6) {
            throw new RegistrationValidationException("Password must be at least 6 characters");
        }
        if (!request.email().contains("@")) {
            throw new RegistrationValidationException("Email is invalid");
        }
    }

    private void requireAdmin(CurrentUserPrincipal principal) {
        if (principal == null || !principal.roles().contains("ADMIN")) {
            throw new RegistrationAccessException("ADMIN role is required");
        }
    }

    private String initialApplicationStatus(RegistrationType type) {
        if (type == RegistrationType.AUTHOR) {
            return "APPROVED";
        }
        return "PENDING_ADMIN_APPROVAL";
    }

    private void grantInitialRoleIfEligible(long userId, RegistrationType type) {
        if (type == RegistrationType.AUTHOR) {
            repository.grantRole(userId, type.grantedRole());
        }
    }

    private void validateProfileForPrivilegedRegistration(RegistrationType type, AcademicProfileRequest profile) {
        if (type == RegistrationType.AUTHOR) {
            return;
        }
        if (profile == null) {
            throw new RegistrationValidationException("Academic profile is required");
        }
        boolean hasVerifiedProfile = !isBlank(profile.homepageUrl())
                || !isBlank(profile.orcid())
                || !isBlank(profile.dblpUrl())
                || !isBlank(profile.googleScholarUrl());
        if (!hasVerifiedProfile) {
            throw new RegistrationValidationException(
                    "Reviewer and organizer applications require an academic profile URL or ORCID"
            );
        }
        if (type == RegistrationType.REVIEWER) {
            boolean hasRepresentativeWork = profile.representativeWorks() != null
                    && profile.representativeWorks().stream().anyMatch(work -> !isBlank(work));
            if (!hasRepresentativeWork) {
                throw new RegistrationValidationException(
                        "Reviewer applications require at least one representative work"
                );
            }
        }
        if (type == RegistrationType.ORGANIZER && isBlank(profile.plannedConferenceTitle())) {
            throw new RegistrationValidationException(
                    "Organizer applications require a planned conference title"
            );
        }
    }

    private String payloadSnapshot(RegistrationRequest request) {
        RegistrationRequest scrubbed = new RegistrationRequest(
                request.registrationType(),
                request.username(),
                null,
                request.realName(),
                request.email(),
                request.institution(),
                request.academicProfile(),
                request.researchAreas()
        );
        try {
            return objectMapper.writeValueAsString(scrubbed);
        } catch (JsonProcessingException ex) {
            throw new RegistrationValidationException("Registration payload could not be recorded");
        }
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private String newRawToken() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeNullable(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum RegistrationType {
        AUTHOR("AUTHOR"),
        REVIEWER("REVIEWER"),
        ORGANIZER("CHAIR");

        private final String grantedRole;

        RegistrationType(String grantedRole) {
            this.grantedRole = grantedRole;
        }

        String grantedRole() {
            return grantedRole;
        }

        static RegistrationType from(String value) {
            if (value == null) {
                throw new RegistrationValidationException("Registration type is required");
            }
            try {
                return RegistrationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new RegistrationValidationException("Registration type must be AUTHOR, REVIEWER, or ORGANIZER");
            }
        }
    }
}
