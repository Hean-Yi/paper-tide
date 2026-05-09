package com.example.review.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.review.audit.AuditLogService;
import com.example.review.auth.CurrentUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RegistrationServiceTest {
    private FakeRegistrationRepository repository;
    private InMemoryVerificationEmailGateway emailGateway;
    private AuditLogService auditLogService;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRegistrationRepository();
        emailGateway = new InMemoryVerificationEmailGateway("http://localhost:5173/verify-email");
        auditLogService = mock(AuditLogService.class);
        service = new RegistrationService(
                repository,
                new BCryptPasswordEncoder(),
                new ObjectMapper(),
                emailGateway,
                auditLogService,
                Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void authorRegistersAsActiveAndReceivesAuthorRoleWithoutEmailVerification() {
        RegistrationResponse response = service.register(authorRequest("new_author", "new_author@example.com"));

        assertEquals(1L, response.userId());
        assertEquals("AUTHOR", response.registrationType());
        assertEquals("APPROVED", response.applicationStatus());
        assertFalse(response.emailVerificationRequired());
        assertEquals("ACTIVE", repository.users.get(1L).status());
        assertFalse(repository.users.get(1L).passwordHash().contains("demo123"));
        assertEquals(List.of("AUTHOR"), repository.rolesByUser.get(1L));
        assertTrue(emailGateway.messages().isEmpty());
        assertTrue(repository.tokens.isEmpty());
    }

    @Test
    void reviewerRegistersActiveAndPendingAdminApprovalWithoutEmailVerification() {
        RegistrationResponse registration = service.register(reviewerRequest("new_reviewer", "new_reviewer@example.com"));

        RoleApplicationRecord application = repository.applications.get(registration.applicationId());
        assertEquals("PENDING_ADMIN_APPROVAL", registration.applicationStatus());
        assertFalse(registration.emailVerificationRequired());
        assertEquals("ACTIVE", repository.users.get(1L).status());
        assertEquals("PENDING_ADMIN_APPROVAL", application.status());
        assertTrue(repository.rolesByUser.getOrDefault(1L, List.of()).isEmpty());
        assertTrue(emailGateway.messages().isEmpty());
        assertTrue(repository.tokens.isEmpty());

        assertThrows(RegistrationAccessException.class, () ->
                service.approveApplication(application.applicationId(), chairPrincipal(), null)
        );

        ApplicationReviewResponse response = service.approveApplication(application.applicationId(), adminPrincipal(), null);

        assertEquals("APPROVED", response.applicationStatus());
        assertEquals("REVIEWER", response.grantedRole());
        assertEquals(List.of("REVIEWER"), repository.rolesByUser.get(1L));
    }

    @Test
    void organizerApprovalGrantsExistingChairRoleNotOrganizerRole() {
        RegistrationResponse registration = service.register(organizerRequest("new_org", "new_org@example.com"));

        assertEquals("PENDING_ADMIN_APPROVAL", registration.applicationStatus());
        assertFalse(registration.emailVerificationRequired());
        assertEquals("ACTIVE", repository.users.get(1L).status());
        assertTrue(emailGateway.messages().isEmpty());

        ApplicationReviewResponse response = service.approveApplication(registration.applicationId(), adminPrincipal(), null);

        assertEquals("CHAIR", response.grantedRole());
        assertEquals(List.of("CHAIR"), repository.rolesByUser.get(1L));
        assertFalse(repository.rolesByUser.get(1L).contains("ORGANIZER"));
    }

    @Test
    void organizerCanRegisterWithoutPlannedConferenceTitle() {
        RegistrationResponse registration = service.register(organizerRequestWithoutPlannedConferenceTitle(
                "no_title_org",
                "no_title_org@example.com"
        ));

        RoleApplicationRecord application = repository.applications.get(registration.applicationId());
        assertEquals("PENDING_ADMIN_APPROVAL", registration.applicationStatus());
        assertFalse(registration.emailVerificationRequired());
        assertEquals("ACTIVE", repository.users.get(1L).status());
        assertEquals("PENDING_ADMIN_APPROVAL", application.status());
    }

    @Test
    void approvalAndRejectionWriteAuditLogEntries() {
        RegistrationResponse registration = service.register(reviewerRequest("audited_reviewer", "audited@example.com"));

        service.approveApplication(registration.applicationId(), adminPrincipal(), null);
        verify(auditLogService).recordRoleApproval(
                eq(1004L),
                eq(registration.applicationId()),
                eq(1L),
                eq("REVIEWER"),
                eq("ROLE_APPROVED"),
                any()
        );

        RegistrationResponse second = service.register(organizerRequest("audited_org", "audited_org@example.com"));
        service.rejectApplication(second.applicationId(), adminPrincipal(), "Insufficient organizing experience");
        verify(auditLogService).recordRoleApproval(
                eq(1004L),
                eq(second.applicationId()),
                eq(second.userId()),
                eq("ORGANIZER"),
                eq("ROLE_REJECTED"),
                eq("Insufficient organizing experience")
        );

        verify(auditLogService, never()).recordDecision(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void rejectedReviewerMayResubmitSameApplicationRow() {
        RegistrationResponse first = service.register(reviewerRequest("retry_reviewer", "retry@example.com"));
        service.rejectApplication(first.applicationId(), adminPrincipal(), "Evidence too thin");

        RegistrationResponse second = service.register(reviewerRequest("retry_reviewer", "retry@example.com"));

        assertEquals(first.userId(), second.userId());
        assertEquals(first.applicationId(), second.applicationId());
        assertEquals("PENDING_ADMIN_APPROVAL", second.applicationStatus());
        assertFalse(second.emailVerificationRequired());
        assertTrue(emailGateway.messages().isEmpty());
        RoleApplicationRecord application = repository.applications.get(first.applicationId());
        assertEquals("PENDING_ADMIN_APPROVAL", application.status());
        assertTrue(application.rejectionReason() == null);
    }

    @Test
    void resubmitIsRejectedWhenUsernameDoesNotMatch() {
        RegistrationResponse first = service.register(reviewerRequest("original_user", "dupe@example.com"));
        service.rejectApplication(first.applicationId(), adminPrincipal(), "Evidence too thin");

        RegistrationRequest mismatched = new RegistrationRequest(
                "REVIEWER",
                "hijacker",
                "demo123",
                "Hijacker",
                "dupe@example.com",
                "Somewhere",
                new AcademicProfileRequest("https://x.example.edu/", null, null, null,
                        List.of("Work"), List.of(), 3, null),
                List.of()
        );

        assertThrows(RegistrationValidationException.class, () -> service.register(mismatched));
    }

    @Test
    void reviewerRegistrationRequiresVerifiableAcademicEvidence() {
        RegistrationRequest request = new RegistrationRequest(
                "REVIEWER",
                "weak_reviewer",
                "demo123",
                "Weak Reviewer",
                "weak_reviewer@example.com",
                "Unknown",
                new AcademicProfileRequest(null, null, null, null, List.of(), List.of(), 3, null),
                List.of()
        );

        assertThrows(RegistrationValidationException.class, () -> service.register(request));
    }

    private RegistrationRequest authorRequest(String username, String email) {
        return new RegistrationRequest(
                "AUTHOR",
                username,
                "demo123",
                "New Author",
                email,
                "Southeast University",
                null,
                List.of()
        );
    }

    private RegistrationRequest reviewerRequest(String username, String email) {
        return new RegistrationRequest(
                "REVIEWER",
                username,
                "demo123",
                "New Reviewer",
                email,
                "Nanjing University",
                new AcademicProfileRequest(
                        "https://example.edu/new-reviewer",
                        "0000-0002-1825-0097",
                        "https://dblp.org/pid/00/0000",
                        null,
                        List.of("Representative Systems Paper"),
                        List.of("example.edu"),
                        3,
                        null
                ),
                List.of(new ResearchAreaRequest("NLP", "Natural Language Processing"))
        );
    }

    private RegistrationRequest organizerRequest(String username, String email) {
        return new RegistrationRequest(
                "ORGANIZER",
                username,
                "demo123",
                "New Organizer",
                email,
                "Fudan University",
                new AcademicProfileRequest(
                        "https://example.edu/new-organizer",
                        null,
                        null,
                        "https://scholar.google.com/citations?user=demo",
                        List.of("Conference Organization Record"),
                        List.of("example.edu"),
                        3,
                        "International Conference on Review Systems"
                ),
                List.of()
        );
    }

    private RegistrationRequest organizerRequestWithoutPlannedConferenceTitle(String username, String email) {
        return new RegistrationRequest(
                "ORGANIZER",
                username,
                "demo123",
                "New Organizer",
                email,
                "Fudan University",
                new AcademicProfileRequest(
                        "https://example.edu/new-organizer",
                        null,
                        null,
                        "https://scholar.google.com/citations?user=demo",
                        List.of("Conference Organization Record"),
                        List.of("example.edu"),
                        3,
                        null
                ),
                List.of()
        );
    }

    private CurrentUserPrincipal adminPrincipal() {
        return new CurrentUserPrincipal(1004L, "admin_demo", List.of("ADMIN"));
    }

    private CurrentUserPrincipal chairPrincipal() {
        return new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"));
    }

    private record FakeUser(long userId, String username, String passwordHash, String realName,
                            String email, String institution, String status) {
    }

    private static final class FakeRegistrationRepository implements RegistrationRepository {
        private long nextUserId = 1;
        private long nextApplicationId = 1;
        private long nextTokenId = 1;
        private final Map<Long, FakeUser> users = new HashMap<>();
        private final Map<Long, RoleApplicationRecord> applications = new HashMap<>();
        private final List<EmailVerificationTokenRecord> tokens = new ArrayList<>();
        private final Map<Long, List<String>> rolesByUser = new HashMap<>();

        @Override
        public boolean usernameExists(String username) {
            return users.values().stream().anyMatch(user -> user.username().equals(username));
        }

        @Override
        public boolean emailExists(String email) {
            return users.values().stream().anyMatch(user -> user.email().equals(email));
        }

        @Override
        public long createUser(UserRegistrationDraft draft) {
            long userId = nextUserId++;
            users.put(userId, new FakeUser(userId, draft.username(), draft.passwordHash(), draft.realName(),
                    draft.email(), draft.institution(), draft.status()));
            return userId;
        }

        @Override
        public void saveAcademicProfile(long userId, AcademicProfileRequest profile) {
        }

        @Override
        public void replaceResearchAreas(long userId, List<ResearchAreaRequest> researchAreas) {
        }

        @Override
        public long createRoleApplication(RoleApplicationDraft draft) {
            long applicationId = nextApplicationId++;
            applications.put(applicationId, new RoleApplicationRecord(applicationId, draft.userId(),
                    draft.registrationType(), draft.status(), null, null, null));
            return applicationId;
        }

        @Override
        public void createEmailVerificationToken(EmailVerificationTokenDraft draft) {
            tokens.add(new EmailVerificationTokenRecord(nextTokenId++, draft.userId(), draft.roleApplicationId(),
                    draft.tokenHash(), draft.purpose(), draft.expiresAt(), false));
        }

        @Override
        public Optional<EmailVerificationTokenRecord> findVerificationToken(String tokenHash) {
            return tokens.stream().filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean consumeVerificationToken(long tokenId) {
            EmailVerificationTokenRecord token = tokens.stream()
                    .filter(candidate -> candidate.tokenId() == tokenId)
                    .findFirst()
                    .orElseThrow();
            if (token.consumed()) {
                return false;
            }
            tokens.set(tokens.indexOf(token), new EmailVerificationTokenRecord(token.tokenId(), token.userId(),
                    token.roleApplicationId(), token.tokenHash(), token.purpose(), token.expiresAt(), true));
            return true;
        }

        @Override
        public void activateUser(long userId) {
            FakeUser user = users.get(userId);
            users.put(userId, new FakeUser(user.userId(), user.username(), user.passwordHash(),
                    user.realName(), user.email(), user.institution(), "ACTIVE"));
        }

        @Override
        public Optional<RoleApplicationRecord> findRoleApplication(long applicationId) {
            return Optional.ofNullable(applications.get(applicationId));
        }

        @Override
        public List<RoleApplicationRecord> listPendingAdminApplications() {
            return applications.values().stream()
                    .filter(application -> "PENDING_ADMIN_APPROVAL".equals(application.status()))
                    .toList();
        }

        @Override
        public List<RoleApplicationDetail> listPendingAdminApplicationDetails() {
            return applications.values().stream()
                    .filter(application -> "PENDING_ADMIN_APPROVAL".equals(application.status()))
                    .map(application -> {
                        FakeUser user = users.get(application.userId());
                        return new RoleApplicationDetail(
                                application.applicationId(),
                                application.userId(),
                                application.registrationType(),
                                application.status(),
                                application.rejectionReason(),
                                Instant.parse("2026-05-06T00:00:00Z"),
                                user == null ? null : user.username(),
                                user == null ? null : user.realName(),
                                user == null ? null : user.email(),
                                user == null ? null : user.institution(),
                                null, null, null, null, List.of(), List.of(), null, List.of()
                        );
                    })
                    .toList();
        }

        @Override
        public void updateRoleApplicationStatus(long applicationId, String status, Long reviewedBy, String rejectionReason) {
            RoleApplicationRecord application = applications.get(applicationId);
            applications.put(applicationId, new RoleApplicationRecord(application.applicationId(), application.userId(),
                    application.registrationType(), status, reviewedBy, Instant.parse("2026-05-06T00:00:00Z"), rejectionReason));
        }

        @Override
        public void grantRole(long userId, String roleCode) {
            rolesByUser.computeIfAbsent(userId, ignored -> new ArrayList<>());
            if (!rolesByUser.get(userId).contains(roleCode)) {
                rolesByUser.get(userId).add(roleCode);
            }
        }

        @Override
        public Optional<ExistingUserSummary> findUserByEmail(String email) {
            return users.values().stream()
                    .filter(user -> user.email().equalsIgnoreCase(email))
                    .findFirst()
                    .map(user -> new ExistingUserSummary(user.userId(), user.username(), user.email(), user.status()));
        }

        @Override
        public Optional<RoleApplicationRecord> findApplicationByUserAndType(long userId, String registrationType) {
            return applications.values().stream()
                    .filter(application -> application.userId() == userId
                            && application.registrationType().equals(registrationType))
                    .findFirst();
        }

        @Override
        public void resetUserForResubmit(long userId, String passwordHash, String realName, String institution) {
            FakeUser user = users.get(userId);
            users.put(userId, new FakeUser(user.userId(), user.username(), passwordHash, realName,
                    user.email(), institution, "ACTIVE"));
        }

        @Override
        public void resetRoleApplicationToPending(long applicationId, String payloadSnapshot) {
            RoleApplicationRecord application = applications.get(applicationId);
            applications.put(applicationId, new RoleApplicationRecord(application.applicationId(), application.userId(),
                    application.registrationType(), "PENDING_EMAIL_VERIFICATION", null, null, null));
        }

        @Override
        public void clearUnconsumedVerificationTokens(long applicationId) {
            for (int i = 0; i < tokens.size(); i++) {
                EmailVerificationTokenRecord token = tokens.get(i);
                if (token.roleApplicationId() == applicationId && !token.consumed()) {
                    tokens.set(i, new EmailVerificationTokenRecord(token.tokenId(), token.userId(),
                            token.roleApplicationId(), token.tokenHash(), token.purpose(), token.expiresAt(), true));
                }
            }
        }

        @Override
        public int deleteExpiredTokens(Instant cutoff) {
            int before = tokens.size();
            tokens.removeIf(token -> token.expiresAt().isBefore(cutoff));
            return before - tokens.size();
        }
    }
}
