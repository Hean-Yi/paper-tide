package com.example.review.registration;

import java.time.Instant;
import java.util.List;

record RegistrationRequest(
        String registrationType,
        String username,
        String password,
        String realName,
        String email,
        String institution,
        AcademicProfileRequest academicProfile,
        List<ResearchAreaRequest> researchAreas
) {
}

record AcademicProfileRequest(
        String homepageUrl,
        String orcid,
        String dblpUrl,
        String googleScholarUrl,
        List<String> representativeWorks,
        List<String> conflictDomains,
        Integer defaultMaxLoad,
        String plannedConferenceTitle
) {
}

record ResearchAreaRequest(String areaCode, String areaName) {
}

record RegistrationResponse(
        long userId,
        long applicationId,
        String registrationType,
        String applicationStatus,
        boolean emailVerificationRequired
) {
}

record EmailVerificationRequest(String token) {
}

record EmailVerificationResponse(
        long userId,
        long applicationId,
        String registrationType,
        String userStatus,
        String applicationStatus
) {
}

record ApplicationReviewRequest(String rejectionReason) {
}

record ApplicationReviewResponse(
        long applicationId,
        long userId,
        String registrationType,
        String applicationStatus,
        String grantedRole
) {
}

record RoleApplicationRecord(
        long applicationId,
        long userId,
        String registrationType,
        String status,
        Long reviewedBy,
        Instant reviewedAt,
        String rejectionReason
) {
}

record UserRegistrationDraft(
        String username,
        String passwordHash,
        String realName,
        String email,
        String institution,
        String status
) {
}

record UserRegistrationRecord(
        long userId,
        String username,
        String passwordHash,
        String realName,
        String email,
        String institution,
        String status
) {
}

record RoleApplicationDraft(
        long userId,
        String registrationType,
        String status,
        String submittedPayloadSnapshot
) {
}

record EmailVerificationTokenDraft(
        long userId,
        long roleApplicationId,
        String tokenHash,
        String purpose,
        Instant expiresAt
) {
}

record EmailVerificationTokenRecord(
        long tokenId,
        long userId,
        long roleApplicationId,
        String tokenHash,
        String purpose,
        Instant expiresAt,
        boolean consumed
) {
}

record VerificationEmailMessage(
        long userId,
        long applicationId,
        String email,
        String rawToken,
        Instant expiresAt
) {
}
