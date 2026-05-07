package com.example.review.operations;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessOperationsService {
    private final BusinessOperationsRepository repository;

    public BusinessOperationsService(BusinessOperationsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DiscussionMessageResponse postRoundDiscussion(
            CurrentUserPrincipal principal,
            long roundId,
            DiscussionMessageRequest request
    ) {
        OperationsRoundRow round = repository.findRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        String senderRole = resolveDiscussionSenderRole(principal, round, null);
        String messageText = requireText(request.messageText(), "messageText");
        long messageId = repository.insertDiscussionMessage(
                round.roundId(),
                null,
                round.manuscriptId(),
                principal.userId(),
                senderRole,
                request.messageScope() == null || request.messageScope().isBlank() ? "ROUND" : request.messageScope(),
                messageText
        );
        return listRoundDiscussion(principal, roundId).stream()
                .filter(message -> message.messageId() == messageId)
                .findFirst()
                .orElseThrow();
    }

    public List<DiscussionMessageResponse> listRoundDiscussion(CurrentUserPrincipal principal, long roundId) {
        OperationsRoundRow round = repository.findRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        resolveDiscussionSenderRole(principal, round, null);
        return repository.listDiscussionMessages(roundId, null).stream()
                .map(this::toDiscussionResponse)
                .toList();
    }

    @Transactional
    public DiscussionMessageResponse postAssignmentDiscussion(
            CurrentUserPrincipal principal,
            long assignmentId,
            DiscussionMessageRequest request
    ) {
        OperationsAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        OperationsRoundRow round = repository.findRound(assignment.roundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        String senderRole = resolveDiscussionSenderRole(principal, round, assignment);
        long messageId = repository.insertDiscussionMessage(
                assignment.roundId(),
                assignment.assignmentId(),
                assignment.manuscriptId(),
                principal.userId(),
                senderRole,
                "ASSIGNMENT",
                requireText(request.messageText(), "messageText")
        );
        return listAssignmentDiscussion(principal, assignmentId).stream()
                .filter(message -> message.messageId() == messageId)
                .findFirst()
                .orElseThrow();
    }

    public List<DiscussionMessageResponse> listAssignmentDiscussion(CurrentUserPrincipal principal, long assignmentId) {
        OperationsAssignmentRow assignment = repository.findAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        OperationsRoundRow round = repository.findRound(assignment.roundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        resolveDiscussionSenderRole(principal, round, assignment);
        return repository.listDiscussionMessages(assignment.roundId(), assignment.assignmentId()).stream()
                .map(this::toDiscussionResponse)
                .toList();
    }

    @Transactional
    public CameraReadyResponse submitCameraReady(
            CurrentUserPrincipal principal,
            long manuscriptId,
            CameraReadyRequest request
    ) {
        RoleGuard.requireRole(principal, "AUTHOR");
        OperationsManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (manuscript.submitterId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden camera-ready access");
        }
        if (!"ACCEPTED".equals(manuscript.currentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camera-ready submission requires an accepted manuscript");
        }
        if (manuscript.currentVersionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted manuscript has no current version");
        }
        String fileName = requireText(request.fileName(), "fileName");
        if (request.fileSize() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSize must be non-negative");
        }
        long cameraReadyId = repository.upsertCameraReadySubmission(
                manuscript.manuscriptId(),
                manuscript.currentVersionId(),
                principal.userId(),
                fileName,
                request.fileSize(),
                request.copyrightConfirmed(),
                request.licenseType()
        );
        return toCameraReadyResponse(repository.findCameraReady(manuscriptId)
                .filter(row -> row.cameraReadyId() == cameraReadyId)
                .orElseThrow());
    }

    public CameraReadyResponse getCameraReady(CurrentUserPrincipal principal, long manuscriptId) {
        OperationsManuscriptRow manuscript = repository.findManuscript(manuscriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript not found"));
        if (!canSeeManuscriptOperations(principal, manuscript)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden camera-ready access");
        }
        return repository.findCameraReady(manuscriptId)
                .map(this::toCameraReadyResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camera-ready submission not found"));
    }

    public List<CommunicationLogResponse> listCommunicationLogs(CurrentUserPrincipal principal, long conferenceId) {
        requireConferenceOperator(principal, conferenceId);
        return repository.listCommunicationLogs(conferenceId).stream()
                .map(row -> new CommunicationLogResponse(
                        row.communicationId(),
                        row.conferenceId(),
                        row.manuscriptId(),
                        row.recipientId(),
                        row.channel(),
                        row.templateKey(),
                        row.subject(),
                        row.deliveryStatus(),
                        row.bizType(),
                        row.bizId(),
                        row.sentAt()
                ))
                .toList();
    }

    public GovernanceReportResponse governanceReport(CurrentUserPrincipal principal, long conferenceId) {
        GovernanceReportRow row = requireConferenceOperator(principal, conferenceId);
        return new GovernanceReportResponse(
                row.conferenceId(),
                row.submissionCount(),
                row.acceptedCount(),
                row.assignmentCount(),
                row.submittedReviewCount(),
                row.overdueReviewCount(),
                row.conflictCount(),
                row.cameraReadyCount()
        );
    }

    public void recordDecisionCommunication(
            Long conferenceId,
            long manuscriptId,
            long recipientId,
            long decisionId,
            String decisionCode
    ) {
        repository.insertCommunicationLog(
                conferenceId,
                manuscriptId,
                recipientId,
                "decision.release",
                "Decision released: " + decisionCode,
                "DECISION",
                decisionId
        );
    }

    private String resolveDiscussionSenderRole(
            CurrentUserPrincipal principal,
            OperationsRoundRow round,
            OperationsAssignmentRow assignment
    ) {
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, round.organizerUserId())) {
            return "CHAIR";
        }
        if (assignment != null && RoleGuard.hasRole(principal, "REVIEWER") && assignment.reviewerId() == principal.userId()) {
            return "REVIEWER";
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Discussion access is not allowed");
    }

    private GovernanceReportRow requireConferenceOperator(CurrentUserPrincipal principal, long conferenceId) {
        GovernanceReportRow row = repository.governanceReport(conferenceId);
        if (RoleGuard.hasRole(principal, "ADMIN") || isConferenceOrganizer(principal, row.organizerUserId())) {
            return row;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conference operations access is not allowed");
    }

    private boolean canSeeManuscriptOperations(CurrentUserPrincipal principal, OperationsManuscriptRow manuscript) {
        return RoleGuard.hasRole(principal, "ADMIN")
                || isConferenceOrganizer(principal, manuscript.organizerUserId())
                || (RoleGuard.hasRole(principal, "AUTHOR") && manuscript.submitterId() == principal.userId());
    }

    private boolean isConferenceOrganizer(CurrentUserPrincipal principal, Long organizerUserId) {
        return RoleGuard.hasRole(principal, "CHAIR")
                && organizerUserId != null
                && organizerUserId.equals(principal.userId());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.strip();
    }

    private DiscussionMessageResponse toDiscussionResponse(DiscussionMessageRow row) {
        return new DiscussionMessageResponse(
                row.messageId(),
                row.roundId(),
                row.assignmentId(),
                row.manuscriptId(),
                row.senderRole(),
                row.messageScope(),
                row.messageText(),
                row.createdAt()
        );
    }

    private CameraReadyResponse toCameraReadyResponse(CameraReadyRow row) {
        return new CameraReadyResponse(
                row.cameraReadyId(),
                row.manuscriptId(),
                row.versionId(),
                row.fileName(),
                row.fileSize(),
                row.copyrightConfirmed(),
                row.licenseType(),
                row.status(),
                row.submittedAt(),
                row.updatedAt()
        );
    }
}

record DiscussionMessageRequest(String messageText, String messageScope) {
}

record DiscussionMessageResponse(
        long messageId,
        long roundId,
        Long assignmentId,
        long manuscriptId,
        String senderRole,
        String messageScope,
        String messageText,
        Timestamp createdAt
) {
}

record CameraReadyRequest(
        String fileName,
        long fileSize,
        boolean copyrightConfirmed,
        String licenseType
) {
}

record CameraReadyResponse(
        long cameraReadyId,
        long manuscriptId,
        long versionId,
        String fileName,
        long fileSize,
        boolean copyrightConfirmed,
        String licenseType,
        String status,
        Timestamp submittedAt,
        Timestamp updatedAt
) {
}

record CommunicationLogResponse(
        long communicationId,
        Long conferenceId,
        Long manuscriptId,
        long recipientId,
        String channel,
        String templateKey,
        String subject,
        String deliveryStatus,
        String bizType,
        Long bizId,
        Timestamp sentAt
) {
}

record GovernanceReportResponse(
        long conferenceId,
        long submissionCount,
        long acceptedCount,
        long assignmentCount,
        long submittedReviewCount,
        long overdueReviewCount,
        long conflictCount,
        long cameraReadyCount
) {
}
