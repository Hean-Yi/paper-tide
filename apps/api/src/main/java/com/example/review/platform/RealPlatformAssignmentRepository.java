package com.example.review.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RealPlatformAssignmentRepository extends RealPlatformRepository {
    public RealPlatformAssignmentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        super(jdbcTemplate, objectMapper);
    }

    public long upsertReviewerInvitation(
            long conferenceId,
            long reviewerId,
            String invitationMessage,
            long invitedBy,
            Timestamp expiresAt
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT INVITATION_ID
                FROM REVIEWER_INVITATION
                WHERE CONFERENCE_ID = ?
                  AND REVIEWER_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("INVITATION_ID") : null,
                conferenceId,
                reviewerId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE REVIEWER_INVITATION
                    SET INVITATION_STATUS = 'PENDING',
                        INVITATION_MESSAGE = ?,
                        INVITED_BY = ?,
                        INVITED_AT = CURRENT_TIMESTAMP,
                        RESPONDED_AT = NULL,
                        EXPIRES_AT = ?
                    WHERE INVITATION_ID = ?
                    """,
                    invitationMessage,
                    invitedBy,
                    expiresAt,
                    existingId
            );
            return existingId;
        }
        long invitationId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEWER_INVITATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEWER_INVITATION (
                  INVITATION_ID, CONFERENCE_ID, REVIEWER_ID, INVITATION_STATUS,
                  INVITATION_MESSAGE, INVITED_BY, INVITED_AT, RESPONDED_AT, EXPIRES_AT
                ) VALUES (?, ?, ?, 'PENDING', ?, ?, CURRENT_TIMESTAMP, NULL, ?)
                """,
                invitationId,
                conferenceId,
                reviewerId,
                invitationMessage,
                invitedBy,
                expiresAt
        );
        return invitationId;
    }

    public Optional<PlatformReviewerInvitationRow> findReviewerInvitation(long invitationId) {
        List<PlatformReviewerInvitationRow> rows = jdbcTemplate.query(
                """
                SELECT INVITATION_ID, CONFERENCE_ID, REVIEWER_ID, INVITATION_STATUS, INVITATION_MESSAGE,
                       INVITED_BY, INVITED_AT, RESPONDED_AT, EXPIRES_AT
                FROM REVIEWER_INVITATION
                WHERE INVITATION_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewerInvitationRow(
                        rs.getLong("INVITATION_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("INVITATION_STATUS"),
                        rs.getString("INVITATION_MESSAGE"),
                        rs.getLong("INVITED_BY"),
                        rs.getTimestamp("INVITED_AT"),
                        rs.getTimestamp("RESPONDED_AT"),
                        rs.getTimestamp("EXPIRES_AT")
                ),
                invitationId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void updateReviewerInvitationStatus(long invitationId, String invitationStatus) {
        jdbcTemplate.update(
                """
                UPDATE REVIEWER_INVITATION
                SET INVITATION_STATUS = ?,
                    RESPONDED_AT = CURRENT_TIMESTAMP
                WHERE INVITATION_ID = ?
                """,
                invitationStatus,
                invitationId
        );
    }

    public void upsertConferenceReviewer(long conferenceId, long reviewerId, long invitedBy) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT CONFERENCE_REVIEWER_ID
                FROM CONFERENCE_REVIEWER
                WHERE CONFERENCE_ID = ?
                  AND REVIEWER_ID = ?
                """,
                rs -> rs.next() ? rs.getLong("CONFERENCE_REVIEWER_ID") : null,
                conferenceId,
                reviewerId
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE CONFERENCE_REVIEWER
                    SET MEMBERSHIP_STATUS = 'ACTIVE',
                        INVITED_BY = ?,
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE CONFERENCE_REVIEWER_ID = ?
                    """,
                    invitedBy,
                    existingId
            );
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_REVIEWER (
                  CONFERENCE_REVIEWER_ID, CONFERENCE_ID, REVIEWER_ID, MEMBERSHIP_STATUS,
                  MAX_LOAD, RESEARCH_AREAS_JSON, INVITED_BY, JOINED_AT, UPDATED_AT
                ) VALUES (
                  SEQ_CONFERENCE_REVIEWER.NEXTVAL, ?, ?, 'ACTIVE', 3, '[]', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                conferenceId,
                reviewerId,
                invitedBy
        );
    }

    public long insertExternalReviewerDelegation(
            PlatformAssignmentRow assignment,
            long requestedBy,
            String externalName,
            String externalEmail,
            String rationale
    ) {
        long delegationId = jdbcTemplate.queryForObject("SELECT SEQ_EXTERNAL_REVIEWER_DELEGATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO EXTERNAL_REVIEWER_DELEGATION (
                  DELEGATION_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REQUESTED_BY,
                  EXTERNAL_NAME, EXTERNAL_EMAIL, RATIONALE, DELEGATION_STATUS,
                  DECISION_NOTE, DECIDED_BY, REQUESTED_AT, DECIDED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'REQUESTED', NULL, NULL, CURRENT_TIMESTAMP, NULL)
                """,
                delegationId,
                assignment.assignmentId(),
                assignment.manuscriptId(),
                requestedBy,
                externalName,
                externalEmail,
                rationale
        );
        return delegationId;
    }

    public Optional<PlatformExternalDelegationRow> findExternalDelegation(long delegationId) {
        List<PlatformExternalDelegationRow> rows = jdbcTemplate.query(
                """
                SELECT D.DELEGATION_ID,
                       D.ASSIGNMENT_ID,
                       D.MANUSCRIPT_ID,
                       D.REQUESTED_BY,
                       D.EXTERNAL_NAME,
                       D.EXTERNAL_EMAIL,
                       D.DELEGATION_STATUS,
                       D.DECISION_NOTE,
                       COALESCE(M.CONFERENCE_ID, 0) AS CONFERENCE_ID,
                       C.ORGANIZER_USER_ID
                FROM EXTERNAL_REVIEWER_DELEGATION D
                JOIN MANUSCRIPT M ON M.MANUSCRIPT_ID = D.MANUSCRIPT_ID
                LEFT JOIN CONFERENCE C ON C.CONFERENCE_ID = COALESCE(M.CONFERENCE_ID, 0)
                WHERE D.DELEGATION_ID = ?
                """,
                (rs, rowNum) -> new PlatformExternalDelegationRow(
                        rs.getLong("DELEGATION_ID"),
                        rs.getLong("ASSIGNMENT_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REQUESTED_BY"),
                        rs.getString("EXTERNAL_NAME"),
                        rs.getString("EXTERNAL_EMAIL"),
                        rs.getString("DELEGATION_STATUS"),
                        rs.getString("DECISION_NOTE"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getObject("ORGANIZER_USER_ID", Long.class)
                ),
                delegationId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void decideExternalDelegation(long delegationId, String delegationStatus, String decisionNote, long decidedBy) {
        jdbcTemplate.update(
                """
                UPDATE EXTERNAL_REVIEWER_DELEGATION
                SET DELEGATION_STATUS = ?,
                    DECISION_NOTE = ?,
                    DECIDED_BY = ?,
                    DECIDED_AT = CURRENT_TIMESTAMP
                WHERE DELEGATION_ID = ?
                """,
                delegationStatus,
                decisionNote,
                decidedBy,
                delegationId
        );
    }

    public long insertReviewerInvitationImportBatch(
            long conferenceId,
            long submittedBy,
            int rowCount,
            int validRowCount,
            int errorCount,
            BulkReviewerInvitationImportDocument previewDocument
    ) {
        return insertGenericImportBatch(
                conferenceId,
                "REVIEWER_INVITATIONS",
                submittedBy,
                rowCount,
                validRowCount,
                errorCount,
                previewDocument
        );
    }

    public long insertMatchingScoreImportBatch(
            long conferenceId,
            long submittedBy,
            int rowCount,
            int validRowCount,
            int errorCount,
            BulkMatchingScoreImportDocument previewDocument
    ) {
        return insertGenericImportBatch(
                conferenceId,
                "MATCHING_SCORES",
                submittedBy,
                rowCount,
                validRowCount,
                errorCount,
                previewDocument
        );
    }

    private long insertGenericImportBatch(
            long conferenceId,
            String importType,
            long submittedBy,
            int rowCount,
            int validRowCount,
            int errorCount,
            Object previewDocument
    ) {
        long batchId = jdbcTemplate.queryForObject("SELECT SEQ_IMPORT_BATCH.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO IMPORT_BATCH (
                  IMPORT_BATCH_ID, CONFERENCE_ID, IMPORT_TYPE, SUBMITTED_BY, BATCH_STATUS,
                  ROW_COUNT, VALID_ROW_COUNT, ERROR_COUNT, PREVIEW_JSON, CREATED_AT, APPLIED_AT
                ) VALUES (?, ?, ?, ?, 'PREVIEWED', ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """,
                batchId,
                conferenceId,
                importType,
                submittedBy,
                rowCount,
                validRowCount,
                errorCount,
                toJson(previewDocument)
        );
        return batchId;
    }

    public Optional<PlatformReviewerInvitationImportBatchRow> findReviewerInvitationImportBatch(long batchId) {
        List<PlatformReviewerInvitationImportBatchRow> rows = jdbcTemplate.query(
                """
                SELECT IMPORT_BATCH_ID, CONFERENCE_ID, IMPORT_TYPE, SUBMITTED_BY, BATCH_STATUS, PREVIEW_JSON
                FROM IMPORT_BATCH
                WHERE IMPORT_BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformReviewerInvitationImportBatchRow(
                        rs.getLong("IMPORT_BATCH_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("IMPORT_TYPE"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("BATCH_STATUS"),
                        fromJson(rs.getString("PREVIEW_JSON"), BulkReviewerInvitationImportDocument.class)
                ),
                batchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<PlatformMatchingScoreImportBatchRow> findMatchingScoreImportBatch(long batchId) {
        List<PlatformMatchingScoreImportBatchRow> rows = jdbcTemplate.query(
                """
                SELECT IMPORT_BATCH_ID, CONFERENCE_ID, IMPORT_TYPE, SUBMITTED_BY, BATCH_STATUS, PREVIEW_JSON
                FROM IMPORT_BATCH
                WHERE IMPORT_BATCH_ID = ?
                """,
                (rs, rowNum) -> new PlatformMatchingScoreImportBatchRow(
                        rs.getLong("IMPORT_BATCH_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getString("IMPORT_TYPE"),
                        rs.getLong("SUBMITTED_BY"),
                        rs.getString("BATCH_STATUS"),
                        fromJson(rs.getString("PREVIEW_JSON"), BulkMatchingScoreImportDocument.class)
                ),
                batchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void markImportApplied(long batchId) {
        jdbcTemplate.update(
                "UPDATE IMPORT_BATCH SET BATCH_STATUS = 'APPLIED', APPLIED_AT = CURRENT_TIMESTAMP WHERE IMPORT_BATCH_ID = ?",
                batchId
        );
    }

    public long upsertConflictRelationship(
            long conferenceId,
            long manuscriptId,
            long reviewerId,
            String conflictType,
            String conflictSource,
            String severity,
            String note,
            long createdBy
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT CONFLICT_RELATIONSHIP_ID
                FROM CONFLICT_RELATIONSHIP
                WHERE CONFERENCE_ID = ?
                  AND MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = ?
                  AND CONFLICT_TYPE = ?
                """,
                rs -> rs.next() ? rs.getLong("CONFLICT_RELATIONSHIP_ID") : null,
                conferenceId,
                manuscriptId,
                reviewerId,
                conflictType
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE CONFLICT_RELATIONSHIP
                    SET CONFLICT_SOURCE = ?,
                        SEVERITY = ?,
                        NOTE = ?,
                        CREATED_BY = ?,
                        CREATED_AT = CURRENT_TIMESTAMP
                    WHERE CONFLICT_RELATIONSHIP_ID = ?
                    """,
                    conflictSource,
                    severity,
                    note,
                    createdBy,
                    existingId
            );
            return existingId;
        }
        long relationshipId = jdbcTemplate.queryForObject("SELECT SEQ_CONFLICT_RELATIONSHIP.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_RELATIONSHIP (
                  CONFLICT_RELATIONSHIP_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  CONFLICT_TYPE, CONFLICT_SOURCE, SEVERITY, NOTE, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                relationshipId,
                conferenceId,
                manuscriptId,
                reviewerId,
                conflictType,
                conflictSource,
                severity,
                note,
                createdBy
        );
        return relationshipId;
    }

    public Optional<PlatformConflictRelationshipRow> findConflictRelationship(long relationshipId) {
        List<PlatformConflictRelationshipRow> rows = jdbcTemplate.query(
                """
                SELECT CONFLICT_RELATIONSHIP_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                       CONFLICT_TYPE, CONFLICT_SOURCE, SEVERITY, NOTE
                FROM CONFLICT_RELATIONSHIP
                WHERE CONFLICT_RELATIONSHIP_ID = ?
                """,
                (rs, rowNum) -> new PlatformConflictRelationshipRow(
                        rs.getLong("CONFLICT_RELATIONSHIP_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getString("CONFLICT_TYPE"),
                        rs.getString("CONFLICT_SOURCE"),
                        rs.getString("SEVERITY"),
                        rs.getString("NOTE")
                ),
                relationshipId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long upsertReviewerMatchingScore(
            long conferenceId,
            long manuscriptId,
            long reviewerId,
            String scoreSource,
            double matchingScore,
            String rationale,
            long importedBy
    ) {
        Long existingId = jdbcTemplate.query(
                """
                SELECT MATCHING_SCORE_ID
                FROM REVIEWER_MATCHING_SCORE
                WHERE CONFERENCE_ID = ?
                  AND MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = ?
                  AND SCORE_SOURCE = ?
                """,
                rs -> rs.next() ? rs.getLong("MATCHING_SCORE_ID") : null,
                conferenceId,
                manuscriptId,
                reviewerId,
                scoreSource
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE REVIEWER_MATCHING_SCORE
                    SET MATCHING_SCORE = ?,
                        RATIONALE = ?,
                        IMPORTED_BY = ?,
                        IMPORTED_AT = CURRENT_TIMESTAMP
                    WHERE MATCHING_SCORE_ID = ?
                    """,
                    matchingScore,
                    rationale,
                    importedBy,
                    existingId
            );
            return existingId;
        }
        long matchingScoreId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEWER_MATCHING_SCORE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEWER_MATCHING_SCORE (
                  MATCHING_SCORE_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID,
                  SCORE_SOURCE, MATCHING_SCORE, RATIONALE, IMPORTED_BY, IMPORTED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                matchingScoreId,
                conferenceId,
                manuscriptId,
                reviewerId,
                scoreSource,
                matchingScore,
                rationale,
                importedBy
        );
        return matchingScoreId;
    }

    public long insertAssignmentProposalBundle(
            PlatformReviewRoundRow round,
            String proposalName,
            long createdBy
    ) {
        long bundleId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL_BUNDLE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL_BUNDLE (
                  BUNDLE_ID, ROUND_ID, CONFERENCE_ID, MANUSCRIPT_ID, PROPOSAL_NAME,
                  BUNDLE_STATUS, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, 'PROPOSED', ?, CURRENT_TIMESTAMP)
                """,
                bundleId,
                round.roundId(),
                round.conferenceId(),
                round.manuscriptId(),
                proposalName,
                createdBy
        );
        return bundleId;
    }

    public List<PlatformReviewerCandidateRow> listEligibleProposalCandidates(
            long conferenceId,
            long manuscriptId,
            long roundId,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                SELECT CR.REVIEWER_ID,
                       COALESCE(MAX(RMS.MATCHING_SCORE), 0) AS MATCHING_SCORE,
                       CASE
                         WHEN COUNT(CASE WHEN CRL.SEVERITY = 'SOFT' THEN 1 END) > 0 THEN 'SOFT_CONFLICT'
                         ELSE 'ELIGIBLE'
                       END AS ELIGIBILITY_STATUS
                FROM CONFERENCE_REVIEWER CR
                LEFT JOIN REVIEWER_MATCHING_SCORE RMS
                  ON RMS.CONFERENCE_ID = CR.CONFERENCE_ID
                 AND RMS.MANUSCRIPT_ID = ?
                 AND RMS.REVIEWER_ID = CR.REVIEWER_ID
                LEFT JOIN CONFLICT_RELATIONSHIP CRL
                  ON CRL.CONFERENCE_ID = CR.CONFERENCE_ID
                 AND CRL.MANUSCRIPT_ID = ?
                 AND CRL.REVIEWER_ID = CR.REVIEWER_ID
                WHERE CR.CONFERENCE_ID = ?
                  AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM REVIEW_ASSIGNMENT A
                    WHERE A.ROUND_ID = ?
                      AND A.REVIEWER_ID = CR.REVIEWER_ID
                      AND A.TASK_STATUS <> 'CANCELLED'
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM CONFLICT_RELATIONSHIP HARD
                    WHERE HARD.CONFERENCE_ID = CR.CONFERENCE_ID
                      AND HARD.MANUSCRIPT_ID = ?
                      AND HARD.REVIEWER_ID = CR.REVIEWER_ID
                      AND HARD.SEVERITY = 'HARD'
                  )
                GROUP BY CR.REVIEWER_ID
                ORDER BY COALESCE(MAX(RMS.MATCHING_SCORE), 0) DESC, CR.REVIEWER_ID
                FETCH FIRST ? ROWS ONLY
                """,
                (rs, rowNum) -> new PlatformReviewerCandidateRow(
                        rs.getLong("REVIEWER_ID"),
                        rs.getDouble("MATCHING_SCORE"),
                        rs.getString("ELIGIBILITY_STATUS")
                ),
                manuscriptId,
                manuscriptId,
                conferenceId,
                roundId,
                manuscriptId,
                limit
        );
    }

    public long insertAssignmentProposal(
            long bundleId,
            long reviewerId,
            int rankOrder,
            double matchingScore,
            String eligibilityStatus,
            String rationale
    ) {
        long proposalId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL (
                  PROPOSAL_ID, BUNDLE_ID, REVIEWER_ID, RANK_ORDER, MATCHING_SCORE,
                  ELIGIBILITY_STATUS, RATIONALE, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                proposalId,
                bundleId,
                reviewerId,
                rankOrder,
                matchingScore,
                eligibilityStatus,
                rationale
        );
        return proposalId;
    }

    public Optional<PlatformAssignmentProposalBundleRow> findAssignmentProposalBundle(long bundleId) {
        List<PlatformAssignmentProposalBundleRow> rows = jdbcTemplate.query(
                """
                SELECT BUNDLE_ID, ROUND_ID, CONFERENCE_ID, MANUSCRIPT_ID, PROPOSAL_NAME, BUNDLE_STATUS, CREATED_BY
                FROM ASSIGNMENT_PROPOSAL_BUNDLE
                WHERE BUNDLE_ID = ?
                """,
                (rs, rowNum) -> new PlatformAssignmentProposalBundleRow(
                        rs.getLong("BUNDLE_ID"),
                        rs.getLong("ROUND_ID"),
                        rs.getLong("CONFERENCE_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getString("PROPOSAL_NAME"),
                        rs.getString("BUNDLE_STATUS"),
                        rs.getLong("CREATED_BY")
                ),
                bundleId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformAssignmentProposalRow> listAssignmentProposalsForBundle(long bundleId) {
        return jdbcTemplate.query(
                """
                SELECT PROPOSAL_ID, BUNDLE_ID, REVIEWER_ID, RANK_ORDER, MATCHING_SCORE, ELIGIBILITY_STATUS, RATIONALE
                FROM ASSIGNMENT_PROPOSAL
                WHERE BUNDLE_ID = ?
                ORDER BY RANK_ORDER, PROPOSAL_ID
                """,
                (rs, rowNum) -> new PlatformAssignmentProposalRow(
                        rs.getLong("PROPOSAL_ID"),
                        rs.getLong("BUNDLE_ID"),
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("RANK_ORDER"),
                        rs.getDouble("MATCHING_SCORE"),
                        rs.getString("ELIGIBILITY_STATUS"),
                        rs.getString("RATIONALE")
                ),
                bundleId
        );
    }

    public PlatformProposalReviewerValidationRow validateProposalReviewer(long manuscriptId, long roundId, long reviewerId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT CR.REVIEWER_ID,
                       CR.MAX_LOAD,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM REVIEW_ASSIGNMENT A
                         WHERE A.REVIEWER_ID = CR.REVIEWER_ID
                           AND A.TASK_STATUS IN ('ASSIGNED', 'ACCEPTED', 'IN_REVIEW', 'SUBMITTED', 'OVERDUE')
                       ), 0) AS CURRENT_LOAD,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM CONFLICT_RELATIONSHIP C
                         WHERE C.MANUSCRIPT_ID = ?
                           AND C.REVIEWER_ID = CR.REVIEWER_ID
                           AND C.SEVERITY = 'HARD'
                       ), 0) AS HARD_CONFLICT_COUNT,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM REVIEW_ASSIGNMENT A
                         WHERE A.ROUND_ID = ?
                           AND A.REVIEWER_ID = CR.REVIEWER_ID
                           AND A.TASK_STATUS <> 'CANCELLED'
                       ), 0) AS ROUND_ASSIGNMENT_COUNT,
                       COALESCE((
                         SELECT COUNT(*)
                         FROM ASSIGNMENT_DRAFT D
                         WHERE D.ROUND_ID = ?
                           AND D.REVIEWER_ID = CR.REVIEWER_ID
                           AND D.DRAFT_STATUS <> 'DISMISSED'
                       ), 0) AS OPEN_DRAFT_COUNT
                FROM MANUSCRIPT M
                JOIN CONFERENCE_REVIEWER CR
                  ON CR.CONFERENCE_ID = M.CONFERENCE_ID
                 AND CR.REVIEWER_ID = ?
                 AND CR.MEMBERSHIP_STATUS = 'ACTIVE'
                WHERE M.MANUSCRIPT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new PlatformProposalReviewerValidationRow(
                        rs.getLong("REVIEWER_ID"),
                        rs.getInt("CURRENT_LOAD"),
                        rs.getInt("MAX_LOAD"),
                        rs.getInt("HARD_CONFLICT_COUNT"),
                        rs.getInt("ROUND_ASSIGNMENT_COUNT"),
                        rs.getInt("OPEN_DRAFT_COUNT")
                ),
                manuscriptId,
                roundId,
                roundId,
                reviewerId,
                manuscriptId
        );
    }

    public void insertAssignmentDraftFromProposal(
            PlatformAssignmentProposalBundleRow bundle,
            PlatformAssignmentProposalRow proposal,
            PlatformProposalReviewerValidationRow validation,
            long createdBy
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_DRAFT (
                  ASSIGNMENT_DRAFT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID,
                  RANK_ORDER, SCORE, CURRENT_LOAD, MAX_LOAD, BID_VALUE, REASON, DRAFT_STATUS,
                  CREATED_BY, CREATED_AT, UPDATED_AT
                )
                SELECT SEQ_ASSIGNMENT_DRAFT.NEXTVAL, B.ROUND_ID, B.MANUSCRIPT_ID, R.VERSION_ID, ?,
                       ?, ?, ?, ?, NULL, ?, 'PROPOSED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM ASSIGNMENT_PROPOSAL_BUNDLE B
                JOIN REVIEW_ROUND R ON R.ROUND_ID = B.ROUND_ID
                WHERE B.BUNDLE_ID = ?
                """,
                proposal.reviewerId(),
                proposal.rankOrder(),
                Math.round(proposal.matchingScore() * 100),
                validation.currentLoad(),
                validation.maxLoad(),
                "proposal=" + bundle.proposalName() + "; eligibility=" + proposal.eligibilityStatus(),
                createdBy,
                bundle.bundleId()
        );
    }

    public void markAssignmentProposalBundleConfirmed(long bundleId) {
        jdbcTemplate.update(
                """
                UPDATE ASSIGNMENT_PROPOSAL_BUNDLE
                SET BUNDLE_STATUS = 'CONFIRMED'
                WHERE BUNDLE_ID = ?
                """,
                bundleId
        );
    }

    public long insertAssignmentOverrideAudit(long bundleId, long reviewerId, String overrideReason, long overriddenBy) {
        long overrideId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_OVERRIDE_AUDIT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_OVERRIDE_AUDIT (
                  OVERRIDE_ID, BUNDLE_ID, REVIEWER_ID, OVERRIDE_REASON, OVERRIDDEN_BY, OVERRIDDEN_AT
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                overrideId,
                bundleId,
                reviewerId,
                overrideReason,
                overriddenBy
        );
        return overrideId;
    }
}
