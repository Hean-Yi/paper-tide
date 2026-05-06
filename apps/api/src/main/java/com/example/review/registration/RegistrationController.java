package com.example.review.registration;

import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/auth/register")
    public RegistrationResponse register(@RequestBody RegistrationRequest request) {
        try {
            return registrationService.register(request);
        } catch (RegistrationValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/auth/verify-email")
    public EmailVerificationResponse verifyEmail(@RequestBody EmailVerificationRequest request) {
        try {
            return registrationService.verifyEmail(request.token());
        } catch (RegistrationValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RegistrationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/admin/role-applications")
    public List<RoleApplicationRecord> listPendingRoleApplications(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        try {
            return registrationService.listPendingAdminApplications(principal);
        } catch (RegistrationAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @PostMapping("/admin/role-applications/{applicationId}/approve")
    public ApplicationReviewResponse approveRoleApplication(
            @PathVariable long applicationId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody(required = false) ApplicationReviewRequest request
    ) {
        try {
            return registrationService.approveApplication(
                    applicationId,
                    principal,
                    request == null ? null : request.rejectionReason()
            );
        } catch (RegistrationValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RegistrationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (RegistrationAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @PostMapping("/admin/role-applications/{applicationId}/reject")
    public ApplicationReviewResponse rejectRoleApplication(
            @PathVariable long applicationId,
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestBody ApplicationReviewRequest request
    ) {
        try {
            return registrationService.rejectApplication(applicationId, principal, request.rejectionReason());
        } catch (RegistrationValidationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RegistrationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (RegistrationAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }
}
