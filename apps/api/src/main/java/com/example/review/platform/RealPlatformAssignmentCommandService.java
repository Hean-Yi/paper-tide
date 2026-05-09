package com.example.review.platform;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealPlatformAssignmentCommandService {
    private final RealPlatformAssignmentRepository repository;
    private final RealPlatformAccessService accessService;

    public RealPlatformAssignmentCommandService(
            RealPlatformAssignmentRepository repository,
            RealPlatformAccessService accessService
    ) {
        this.repository = repository;
        this.accessService = accessService;
    }

    @Transactional
    public ImportPreviewResponse previewReviewerInvitationImport(
            CurrentUserPrincipal principal,
            long conferenceId,
            BulkReviewerInvitationImportPreviewRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        BulkReviewerInvitationImportDocument preview = parseReviewerInvitationCsv(request.csvText());
        int rowCount = preview.validRows().size() + preview.errorRows().size();
        long batchId = repository.insertReviewerInvitationImportBatch(
                conference.conferenceId(),
                principal.userId(),
                rowCount,
                preview.validRows().size(),
                preview.errorRows().size(),
                preview
        );
        return new ImportPreviewResponse(batchId, rowCount, preview.validRows().size(), preview.errorRows().size());
    }

    @Transactional
    public ImportConfirmResponse confirmReviewerInvitationImport(CurrentUserPrincipal principal, long batchId) {
        PlatformReviewerInvitationImportBatchRow batch = repository.findReviewerInvitationImportBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reviewer invitation import batch not found"));
        accessService.requireConferenceOperator(principal, batch.conferenceId());
        if (!"REVIEWER_INVITATIONS".equals(batch.importType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported import type");
        }
        if (!"PREVIEWED".equals(batch.batchStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Import batch is not confirmable");
        }
        int appliedCount = 0;
        for (BulkReviewerInvitationValidRow row : batch.previewDocument().validRows()) {
            repository.upsertReviewerInvitation(
                    batch.conferenceId(),
                    row.reviewerId(),
                    row.invitationMessage(),
                    principal.userId(),
                    row.expiresAt() == null ? null : Timestamp.from(row.expiresAt())
            );
            appliedCount++;
        }
        repository.markImportApplied(batch.batchId());
        return new ImportConfirmResponse(batch.batchId(), appliedCount);
    }

    @Transactional
    public ReviewerInvitationResponse createReviewerInvitation(
            CurrentUserPrincipal principal,
            long conferenceId,
            ReviewerInvitationRequest request
    ) {
        PlatformConferenceRow conference = accessService.requireConferenceOperator(principal, conferenceId);
        long invitationId = repository.upsertReviewerInvitation(
                conference.conferenceId(),
                request.reviewerId(),
                request.invitationMessage(),
                principal.userId(),
                request.expiresAt() == null ? null : Timestamp.from(request.expiresAt())
        );
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitationId).orElseThrow());
    }

    @Transactional
    public ReviewerInvitationResponse acceptReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        PlatformReviewerInvitationRow invitation = requireInvitationInvitee(principal, invitationId);
        repository.updateReviewerInvitationStatus(invitation.invitationId(), "ACCEPTED");
        repository.upsertConferenceReviewer(invitation.conferenceId(), invitation.reviewerId(), invitation.invitedBy());
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitation.invitationId()).orElseThrow());
    }

    @Transactional
    public ReviewerInvitationResponse declineReviewerInvitation(CurrentUserPrincipal principal, long invitationId) {
        PlatformReviewerInvitationRow invitation = requireInvitationInvitee(principal, invitationId);
        repository.updateReviewerInvitationStatus(invitation.invitationId(), "DECLINED");
        return toReviewerInvitationResponse(repository.findReviewerInvitation(invitation.invitationId()).orElseThrow());
    }

    @Transactional
    public ExternalDelegationResponse requestExternalDelegation(
            CurrentUserPrincipal principal,
            long assignmentId,
            ExternalDelegationRequest request
    ) {
        PlatformAssignmentRow assignment = accessService.requireReviewerAssignment(principal, assignmentId);
        long delegationId = repository.insertExternalReviewerDelegation(
                assignment,
                principal.userId(),
                requireText(request.externalName(), "externalName"),
                requireText(request.externalEmail(), "externalEmail"),
                request.rationale()
        );
        return toExternalDelegationResponse(repository.findExternalDelegation(delegationId).orElseThrow());
    }

    @Transactional
    public ExternalDelegationResponse decideExternalDelegation(
            CurrentUserPrincipal principal,
            long delegationId,
            String delegationStatus,
            ExternalDelegationDecisionRequest request
    ) {
        PlatformExternalDelegationRow delegation = repository.findExternalDelegation(delegationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delegation not found"));
        accessService.requireConferenceOperator(principal, delegation.conferenceId());
        repository.decideExternalDelegation(delegation.delegationId(), delegationStatus, request.decisionNote(), principal.userId());
        return toExternalDelegationResponse(repository.findExternalDelegation(delegation.delegationId()).orElseThrow());
    }

    @Transactional
    public ConflictRelationshipResponse recordConflictRelationship(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ConflictRelationshipRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        String severity = requireEnum(request.severity(), "severity", List.of("SOFT", "HARD"));
        String source = requireEnum(request.conflictSource(), "conflictSource", List.of("MANUAL", "PROFILE", "IMPORT", "BID", "SYSTEM"));
        long relationshipId = repository.upsertConflictRelationship(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.reviewerId(),
                requireText(request.conflictType(), "conflictType"),
                source,
                severity,
                request.note(),
                principal.userId()
        );
        PlatformConflictRelationshipRow row = repository.findConflictRelationship(relationshipId).orElseThrow();
        return new ConflictRelationshipResponse(
                row.conflictRelationshipId(),
                row.manuscriptId(),
                row.reviewerId(),
                row.conflictType(),
                row.conflictSource(),
                row.severity()
        );
    }

    @Transactional
    public ReviewerMatchingScoreResponse recordReviewerMatchingScore(
            CurrentUserPrincipal principal,
            long manuscriptId,
            ReviewerMatchingScoreRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        if (request.matchingScore() == null || request.matchingScore() < 0 || request.matchingScore() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchingScore is invalid");
        }
        long matchingScoreId = repository.upsertReviewerMatchingScore(
                manuscript.conferenceId(),
                manuscript.manuscriptId(),
                request.reviewerId(),
                requireText(request.scoreSource(), "scoreSource"),
                request.matchingScore(),
                request.rationale(),
                principal.userId()
        );
        return new ReviewerMatchingScoreResponse(
                matchingScoreId,
                manuscript.manuscriptId(),
                request.reviewerId(),
                request.matchingScore()
        );
    }

    @Transactional
    public ImportPreviewResponse previewMatchingScoreImport(
            CurrentUserPrincipal principal,
            long manuscriptId,
            BulkMatchingScoreImportPreviewRequest request
    ) {
        PlatformManuscriptRow manuscript = accessService.requireManuscriptOperator(principal, manuscriptId);
        BulkMatchingScoreImportDocument preview = parseMatchingScoreCsv(manuscript.manuscriptId(), request.csvText());
        int rowCount = preview.validRows().size() + preview.errorRows().size();
        long batchId = repository.insertMatchingScoreImportBatch(
                manuscript.conferenceId(),
                principal.userId(),
                rowCount,
                preview.validRows().size(),
                preview.errorRows().size(),
                preview
        );
        return new ImportPreviewResponse(batchId, rowCount, preview.validRows().size(), preview.errorRows().size());
    }

    @Transactional
    public ImportConfirmResponse confirmMatchingScoreImport(CurrentUserPrincipal principal, long batchId) {
        PlatformMatchingScoreImportBatchRow batch = repository.findMatchingScoreImportBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matching score import batch not found"));
        accessService.requireConferenceOperator(principal, batch.conferenceId());
        if (!"MATCHING_SCORES".equals(batch.importType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported import type");
        }
        if (!"PREVIEWED".equals(batch.batchStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Import batch is not confirmable");
        }
        int appliedCount = 0;
        for (BulkMatchingScoreValidRow row : batch.previewDocument().validRows()) {
            PlatformManuscriptRow manuscript = repository.findManuscript(batch.previewDocument().manuscriptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previewed manuscript no longer exists"));
            if (manuscript.conferenceId() != batch.conferenceId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previewed manuscript does not belong to batch conference");
            }
            repository.upsertReviewerMatchingScore(
                    batch.conferenceId(),
                    manuscript.manuscriptId(),
                    row.reviewerId(),
                    row.scoreSource(),
                    row.matchingScore(),
                    row.rationale(),
                    principal.userId()
            );
            appliedCount++;
        }
        repository.markImportApplied(batch.batchId());
        return new ImportConfirmResponse(batch.batchId(), appliedCount);
    }

    @Transactional
    public AssignmentProposalBundleResponse createAssignmentProposalBundle(
            CurrentUserPrincipal principal,
            long roundId,
            AssignmentProposalRequest request
    ) {
        PlatformReviewRoundRow round = repository.findReviewRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        accessService.requireConferenceOperator(principal, round.conferenceId());
        String proposalName = requireText(request.proposalName(), "proposalName");
        int limit = request.limit() == null ? 10 : Math.max(0, Math.min(request.limit(), 50));
        long bundleId = repository.insertAssignmentProposalBundle(round, proposalName, principal.userId());
        List<PlatformReviewerCandidateRow> candidates = repository.listEligibleProposalCandidates(
                round.conferenceId(),
                round.manuscriptId(),
                round.roundId(),
                limit
        );
        int rank = 1;
        for (PlatformReviewerCandidateRow candidate : candidates) {
            repository.insertAssignmentProposal(
                    bundleId,
                    candidate.reviewerId(),
                    rank++,
                    candidate.matchingScore(),
                    candidate.eligibilityStatus(),
                    "Generated from active reviewer pool and conflict checks"
            );
        }
        return new AssignmentProposalBundleResponse(bundleId, round.roundId(), round.manuscriptId(), candidates.size());
    }

    @Transactional
    public AssignmentOverrideResponse recordAssignmentOverride(
            CurrentUserPrincipal principal,
            long bundleId,
            AssignmentOverrideRequest request
    ) {
        PlatformAssignmentProposalBundleRow bundle = repository.findAssignmentProposalBundle(bundleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment proposal bundle not found"));
        accessService.requireConferenceOperator(principal, bundle.conferenceId());
        long overrideId = repository.insertAssignmentOverrideAudit(
                bundle.bundleId(),
                request.reviewerId(),
                requireText(request.overrideReason(), "overrideReason"),
                principal.userId()
        );
        return new AssignmentOverrideResponse(overrideId, bundle.bundleId(), request.reviewerId());
    }

    @Transactional
    public AssignmentProposalConfirmDraftsResponse confirmAssignmentProposalDrafts(CurrentUserPrincipal principal, long bundleId) {
        PlatformAssignmentProposalBundleRow bundle = repository.findAssignmentProposalBundle(bundleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment proposal bundle not found"));
        accessService.requireConferenceOperator(principal, bundle.conferenceId());
        if (!"PROPOSED".equals(bundle.bundleStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment proposal bundle is not confirmable");
        }
        int createdCount = 0;
        for (PlatformAssignmentProposalRow proposal : repository.listAssignmentProposalsForBundle(bundle.bundleId())) {
            if (!List.of("ELIGIBLE", "OVERRIDDEN").contains(proposal.eligibilityStatus())) {
                continue;
            }
            PlatformProposalReviewerValidationRow validation = repository.validateProposalReviewer(
                    bundle.manuscriptId(),
                    bundle.roundId(),
                    proposal.reviewerId()
            );
            if (validation.manuscriptAuthorCount() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Manuscript author cannot be assigned as reviewer");
            }
            if (validation.hardConflictCount() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hard conflict blocks proposal confirmation");
            }
            if (validation.currentLoad() >= validation.maxLoad()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewer max load would be exceeded");
            }
            if (validation.roundAssignmentCount() > 0 || validation.openDraftCount() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewer already has assignment or draft for this round");
            }
            repository.insertAssignmentDraftFromProposal(bundle, proposal, validation, principal.userId());
            createdCount++;
        }
        repository.markAssignmentProposalBundleConfirmed(bundle.bundleId());
        return new AssignmentProposalConfirmDraftsResponse(bundle.bundleId(), createdCount);
    }

    private PlatformReviewerInvitationRow requireInvitationInvitee(CurrentUserPrincipal principal, long invitationId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        PlatformReviewerInvitationRow invitation = repository.findReviewerInvitation(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reviewer invitation not found"));
        if (invitation.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer invitation access is not allowed");
        }
        return invitation;
    }

    private BulkReviewerInvitationImportDocument parseReviewerInvitationCsv(String csvText) {
        String text = requireText(csvText, "csvText");
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !"reviewerId,invitationMessage,expiresAt".equals(lines[0].strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV header must be reviewerId,invitationMessage,expiresAt");
        }
        List<BulkReviewerInvitationValidRow> validRows = new ArrayList<>();
        List<ImportErrorRow> errorRows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            int rowNumber = i + 1;
            if (columns.length != 3) {
                errorRows.add(new ImportErrorRow(rowNumber, line, "Expected 3 columns"));
                continue;
            }
            try {
                long reviewerId = Long.parseLong(columns[0].strip());
                if (!repository.userExists(reviewerId)) {
                    throw new IllegalArgumentException("reviewerId does not exist");
                }
                validRows.add(new BulkReviewerInvitationValidRow(
                        rowNumber,
                        reviewerId,
                        requireCsvText(columns[1], "invitationMessage"),
                        Instant.parse(requireCsvText(columns[2], "expiresAt"))
                ));
            } catch (RuntimeException ex) {
                errorRows.add(new ImportErrorRow(rowNumber, line, ex.getMessage()));
            }
        }
        return new BulkReviewerInvitationImportDocument(validRows, errorRows);
    }

    private BulkMatchingScoreImportDocument parseMatchingScoreCsv(long manuscriptId, String csvText) {
        String text = requireText(csvText, "csvText");
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !"reviewerId,scoreSource,matchingScore,rationale".equals(lines[0].strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV header must be reviewerId,scoreSource,matchingScore,rationale");
        }
        List<BulkMatchingScoreValidRow> validRows = new ArrayList<>();
        List<ImportErrorRow> errorRows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            int rowNumber = i + 1;
            if (columns.length != 4) {
                errorRows.add(new ImportErrorRow(rowNumber, line, "Expected 4 columns"));
                continue;
            }
            try {
                long reviewerId = Long.parseLong(columns[0].strip());
                double score = Double.parseDouble(columns[2].strip());
                if (score < 0 || score > 1) {
                    throw new IllegalArgumentException("matchingScore is invalid");
                }
                validRows.add(new BulkMatchingScoreValidRow(
                        rowNumber,
                        reviewerId,
                        requireCsvText(columns[1], "scoreSource"),
                        score,
                        columns[3].strip()
                ));
            } catch (RuntimeException ex) {
                errorRows.add(new ImportErrorRow(rowNumber, line, ex.getMessage()));
            }
        }
        return new BulkMatchingScoreImportDocument(manuscriptId, validRows, errorRows);
    }

    private String requireCsvText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.strip();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.strip();
    }

    private String requireEnum(String value, String fieldName, List<String> allowedValues) {
        String text = requireText(value, fieldName);
        if (!allowedValues.contains(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is invalid");
        }
        return text;
    }

    private ReviewerInvitationResponse toReviewerInvitationResponse(PlatformReviewerInvitationRow row) {
        return new ReviewerInvitationResponse(
                row.invitationId(),
                row.conferenceId(),
                row.reviewerId(),
                row.invitationStatus()
        );
    }

    private ExternalDelegationResponse toExternalDelegationResponse(PlatformExternalDelegationRow row) {
        return new ExternalDelegationResponse(
                row.delegationId(),
                row.assignmentId(),
                row.manuscriptId(),
                row.externalName(),
                row.externalEmail(),
                row.delegationStatus()
        );
    }
}
