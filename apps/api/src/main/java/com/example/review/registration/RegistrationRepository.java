package com.example.review.registration;

import java.util.List;
import java.util.Optional;

interface RegistrationRepository {
    boolean usernameExists(String username);

    boolean emailExists(String email);

    long createUser(UserRegistrationDraft draft);

    void saveAcademicProfile(long userId, AcademicProfileRequest profile);

    void replaceResearchAreas(long userId, List<ResearchAreaRequest> researchAreas);

    long createRoleApplication(RoleApplicationDraft draft);

    void createEmailVerificationToken(EmailVerificationTokenDraft draft);

    Optional<EmailVerificationTokenRecord> findVerificationToken(String tokenHash);

    void consumeVerificationToken(long tokenId);

    void activateUser(long userId);

    Optional<RoleApplicationRecord> findRoleApplication(long applicationId);

    List<RoleApplicationRecord> listPendingAdminApplications();

    void updateRoleApplicationStatus(long applicationId, String status, Long reviewedBy, String rejectionReason);

    void grantRole(long userId, String roleCode);
}
