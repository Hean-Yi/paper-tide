package com.example.review.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class RealPlatformWaveEightServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWaveEightTables() {
        jdbcTemplate.update("DELETE FROM COMMUNICATION_REMINDER");
        jdbcTemplate.update("DELETE FROM COMMUNICATION_COMPOSE_BATCH");
        jdbcTemplate.update("DELETE FROM DOI_INDEX_ADAPTER_SUBMISSION");
        jdbcTemplate.update("DELETE FROM PROCEEDINGS_EXPORT_FILE");
        jdbcTemplate.update("DELETE FROM STORED_FILE");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL_CONTEXT");
        jdbcTemplate.update("DELETE FROM BULK_OPERATION_ROW");
        jdbcTemplate.update("DELETE FROM BULK_OPERATION_BATCH");
        jdbcTemplate.update("DELETE FROM WORKBENCH_EXPORT_BATCH");
        jdbcTemplate.update("DELETE FROM WORKBENCH_SAVED_FILTER");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_OVERRIDE_AUDIT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL_BUNDLE");
        jdbcTemplate.update("DELETE FROM REVIEWER_MATCHING_SCORE");
        jdbcTemplate.update("DELETE FROM CONFLICT_RELATIONSHIP");
        jdbcTemplate.update("DELETE FROM EXTERNAL_REVIEWER_DELEGATION");
        jdbcTemplate.update("DELETE FROM REVIEWER_INVITATION");
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE_REVISION");
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE");
        jdbcTemplate.update("DELETE FROM WORKFLOW_FORM_RESPONSE");
        jdbcTemplate.update("DELETE FROM AUTHOR_FEEDBACK");
        jdbcTemplate.update("DELETE FROM PAPER_TAG");
        jdbcTemplate.update("DELETE FROM IMPORT_BATCH");
        jdbcTemplate.update("DELETE FROM PAPER_ROLE_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_FORM_FIELD");
        jdbcTemplate.update("DELETE FROM CONFERENCE_FORM_DEFINITION");
        jdbcTemplate.update("DELETE FROM REVIEW_DISCUSSION_MESSAGE");
        jdbcTemplate.update("DELETE FROM CAMERA_READY_SUBMISSION");
        jdbcTemplate.update("DELETE FROM COMMUNICATION_LOG");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEWER_BID");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_DRAFT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_REVIEWER");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        seedLegacyConference();
    }

    @Test
    void reviewerInvitationCanBeAcceptedAndDeclinedByInviteeOnly() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        long invitationId = createReviewerInvitation(chairToken, 1002);

        mockMvc.perform(post("/api/reviewer-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationStatus").value("ACCEPTED"));

        String membershipStatus = jdbcTemplate.queryForObject(
                "SELECT MEMBERSHIP_STATUS FROM CONFERENCE_REVIEWER WHERE CONFERENCE_ID = 0 AND REVIEWER_ID = 1002",
                String.class
        );
        assertThat(membershipStatus).isEqualTo("ACTIVE");

        long declinedInvitationId = createReviewerInvitation(chairToken, 1004);
        mockMvc.perform(post("/api/reviewer-invitations/{invitationId}/decline", declinedInvitationId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerDelegatesExternalReviewerAndChairApprovesWithoutCreatingAssignment() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        MvcResult delegationResult = mockMvc.perform(post("/api/review-assignments/{assignmentId}/external-delegations", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "externalName": "External Reviewer",
                                  "externalEmail": "external.reviewer@example.com",
                                  "rationale": "Specialized systems expertise"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegationStatus").value("REQUESTED"))
                .andReturn();

        long delegationId = objectMapper.readTree(delegationResult.getResponse().getContentAsString())
                .path("delegationId")
                .asLong();

        mockMvc.perform(post("/api/external-delegations/{delegationId}/approve", delegationId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"decisionNote\":\"Approved for advisory review.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegationStatus").value("APPROVED"));

        MvcResult rejectedDelegationResult = mockMvc.perform(post("/api/review-assignments/{assignmentId}/external-delegations", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "externalName": "Second External Reviewer",
                                  "externalEmail": "second.external@example.com",
                                  "rationale": "Backup advisory reviewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long rejectedDelegationId = objectMapper.readTree(rejectedDelegationResult.getResponse().getContentAsString())
                .path("delegationId")
                .asLong();
        mockMvc.perform(post("/api/external-delegations/{delegationId}/reject", rejectedDelegationId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"decisionNote\":\"Not needed.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegationStatus").value("REJECTED"));

        Integer assignmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEW_ASSIGNMENT WHERE MANUSCRIPT_ID = ?",
                Integer.class,
                fixture.manuscriptId()
        );
        assertThat(assignmentCount).isEqualTo(1);
    }

    @Test
    void conflictRelationshipBlocksAssignmentProposalAndRecordsOverrideAudit() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        addConferenceReviewer(1004, 3);

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/conflicts", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "conflictType": "INSTITUTION",
                                  "conflictSource": "MANUAL",
                                  "severity": "HARD",
                                  "note": "Same institution"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("HARD"));

        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "scoreSource": "TPMS_IMPORT",
                                  "matchingScore": 0.92,
                                  "rationale": "Imported topic match"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchingScore").value(0.92));

        MvcResult proposalResult = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-proposals", fixture.roundId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"proposalName\":\"Wave 8 proposal\",\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalCount").value(0))
                .andReturn();

        long bundleId = objectMapper.readTree(proposalResult.getResponse().getContentAsString())
                .path("bundleId")
                .asLong();
        Integer assignmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEW_ASSIGNMENT WHERE REVIEWER_ID = 1004",
                Integer.class
        );
        assertThat(assignmentCount).isZero();

        mockMvc.perform(post("/api/assignment-proposals/{bundleId}/overrides", bundleId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "overrideReason": "Emergency expertise override after chair review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerId").value(1004));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ASSIGNMENT_OVERRIDE_AUDIT WHERE BUNDLE_ID = ? AND REVIEWER_ID = 1004",
                Integer.class,
                bundleId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void bulkInvitationPreviewDoesNotMutateUntilConfirmAndSkipsInvalidRows() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        MvcResult previewResult = mockMvc.perform(post("/api/conferences/{conferenceId}/reviewer-invitations/imports/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "reviewerId,invitationMessage,expiresAt\\n1002,Please join,2099-01-01T00:00:00Z\\n999999,Missing user,2099-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.validRowCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andReturn();

        Integer invitationsBeforeConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEWER_INVITATION",
                Integer.class
        );
        assertThat(invitationsBeforeConfirm).isZero();

        long batchId = objectMapper.readTree(previewResult.getResponse().getContentAsString())
                .path("batchId")
                .asLong();
        mockMvc.perform(post("/api/reviewer-invitation-imports/{batchId}/confirm", batchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        Integer invitationsAfterConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEWER_INVITATION WHERE CONFERENCE_ID = 0 AND REVIEWER_ID = 1002",
                Integer.class
        );
        assertThat(invitationsAfterConfirm).isEqualTo(1);
    }

    @Test
    void bulkMatchingScoreImportPreviewConfirmsOnlyValidRows() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        addConferenceReviewer(1004, 3);

        MvcResult previewResult = mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores/imports/preview", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "reviewerId,scoreSource,matchingScore,rationale\\n1004,TPMS_IMPORT,0.91,Strong subject match\\n1002,TPMS_IMPORT,1.50,Out of range"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.validRowCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andReturn();

        Integer scoresBeforeConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEWER_MATCHING_SCORE WHERE MANUSCRIPT_ID = ?",
                Integer.class,
                fixture.manuscriptId()
        );
        assertThat(scoresBeforeConfirm).isZero();

        long batchId = objectMapper.readTree(previewResult.getResponse().getContentAsString())
                .path("batchId")
                .asLong();
        mockMvc.perform(post("/api/matching-score-imports/{batchId}/confirm", batchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        Double matchingScore = jdbcTemplate.queryForObject(
                "SELECT MATCHING_SCORE FROM REVIEWER_MATCHING_SCORE WHERE MANUSCRIPT_ID = ? AND REVIEWER_ID = 1004",
                Double.class,
                fixture.manuscriptId()
        );
        assertThat(matchingScore).isEqualTo(0.91);
    }

    @Test
    void proposalConfirmWritesAssignmentDraftsAndRevalidatesHardConflictAtConfirmTime() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        addConferenceReviewer(1004, 3);
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "scoreSource": "TPMS_IMPORT",
                                  "matchingScore": 0.88,
                                  "rationale": "Good match"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult proposalResult = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-proposals", fixture.roundId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"proposalName\":\"Confirmable proposal\",\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalCount").value(1))
                .andReturn();

        long bundleId = objectMapper.readTree(proposalResult.getResponse().getContentAsString())
                .path("bundleId")
                .asLong();
        mockMvc.perform(post("/api/assignment-proposals/{bundleId}/confirm-drafts", bundleId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));

        Integer draftCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ASSIGNMENT_DRAFT WHERE ROUND_ID = ? AND REVIEWER_ID = 1004 AND DRAFT_STATUS = 'PROPOSED'",
                Integer.class,
                fixture.roundId()
        );
        Integer finalAssignmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEW_ASSIGNMENT WHERE ROUND_ID = ? AND REVIEWER_ID = 1004",
                Integer.class,
                fixture.roundId()
        );
        assertThat(draftCount).isEqualTo(1);
        assertThat(finalAssignmentCount).isZero();

        long blockedBundleId = createProposalBundleForReviewer(chairToken, fixture, 1004);
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/conflicts", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "conflictType": "EMPLOYMENT",
                                  "conflictSource": "MANUAL",
                                  "severity": "HARD",
                                  "note": "Detected before confirmation"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/assignment-proposals/{bundleId}/confirm-drafts", blockedBundleId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isConflict());
    }

    @Test
    void assignmentOperationsReadModelListsInvitationsDelegationsImportsAndProposals() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        addConferenceReviewer(1004, 3);

        createReviewerInvitation(chairToken, 1004);
        mockMvc.perform(post("/api/review-assignments/{assignmentId}/external-delegations", fixture.assignmentId())
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "externalName": "External Reader",
                                  "externalEmail": "external.reader@example.com",
                                  "rationale": "Needs a systems specialist"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores/imports/preview", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "reviewerId,scoreSource,matchingScore,rationale\\n1004,TPMS_IMPORT,0.91,Strong subject match"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "scoreSource": "TPMS_IMPORT",
                                  "matchingScore": 0.88,
                                  "rationale": "Good match"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-proposals", fixture.roundId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"proposalName\":\"Workbench proposal\",\"limit\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conferences/{conferenceId}/assignment-operations", 0)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conferenceId").value(0))
                .andExpect(jsonPath("$.reviewerInvitations[0].reviewerId").value(1004))
                .andExpect(jsonPath("$.externalDelegations[0].externalEmail").value("external.reader@example.com"))
                .andExpect(jsonPath("$.importBatches[0].importType").value("MATCHING_SCORES"))
                .andExpect(jsonPath("$.assignmentProposals[0].proposalName").value("Workbench proposal"))
                .andExpect(jsonPath("$.matchingScores[0].reviewerId").value(1004));
    }

    @Test
    void chairSavesFormulaFilterExportsFilteredCsvAndConfirmsBulkConflictPreview() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        addConferenceReviewer(1004, 3);
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/tags", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tagName\":\"needs-shepherd\",\"tagValue\":\"yes\"}"))
                .andExpect(status().isOk());

        MvcResult filterResult = mockMvc.perform(post("/api/conferences/{conferenceId}/workbench-filters", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "filterScope": "ASSIGNMENT",
                                  "filterName": "High risk shepherd papers",
                                  "filterFormula": "tag:needs-shepherd=yes AND status:UNDER_REVIEW",
                                  "criteria": {
                                    "tagName": "needs-shepherd",
                                    "tagValue": "yes",
                                    "status": "UNDER_REVIEW"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterScope").value("ASSIGNMENT"))
                .andExpect(jsonPath("$.filterFormula").value("tag:needs-shepherd=yes AND status:UNDER_REVIEW"))
                .andReturn();
        long savedFilterId = objectMapper.readTree(filterResult.getResponse().getContentAsString())
                .path("savedFilterId")
                .asLong();

        mockMvc.perform(get("/api/conferences/{conferenceId}/assignment-operations", 0)
                        .queryParam("savedFilterId", String.valueOf(savedFilterId))
                        .queryParam("formula", "tag:needs-shepherd=yes AND status:UNDER_REVIEW")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedFilters[0].filterName").value("High risk shepherd papers"))
                .andExpect(jsonPath("$.filteredPapers[0].manuscriptId").value(fixture.manuscriptId()));

        MvcResult exportResult = mockMvc.perform(post("/api/conferences/{conferenceId}/workbench-exports", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "exportScope": "PAPERS",
                                  "exportFormat": "CSV",
                                  "savedFilterId": %d
                                }
                                """.formatted(savedFilterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportStatus").value("GENERATED"))
                .andExpect(jsonPath("$.downloadFileName").value("papers-high-risk-shepherd-papers.csv"))
                .andReturn();
        long exportBatchId = objectMapper.readTree(exportResult.getResponse().getContentAsString())
                .path("exportBatchId")
                .asLong();

        mockMvc.perform(get("/api/workbench-exports/{exportBatchId}/download", exportBatchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("text/csv"))
                .andExpect(jsonPath("$.fileContents").value(org.hamcrest.Matchers.containsString("manuscriptId,title,status,tags")));

        MvcResult bulkPreviewResult = mockMvc.perform(post("/api/conferences/{conferenceId}/bulk-operations/conflicts/preview", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "csvText": "manuscriptId,reviewerId,conflictType,severity,note\\n%d,1004,INSTITUTION,HARD,Same institution\\n999999,1004,INSTITUTION,HARD,Missing manuscript"
                                }
                                """.formatted(fixture.manuscriptId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.validRowCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andReturn();
        Integer conflictsBeforeConfirm = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CONFLICT_RELATIONSHIP", Integer.class);
        assertThat(conflictsBeforeConfirm).isZero();

        long bulkBatchId = objectMapper.readTree(bulkPreviewResult.getResponse().getContentAsString())
                .path("bulkBatchId")
                .asLong();
        mockMvc.perform(post("/api/bulk-operations/{bulkBatchId}/confirm", bulkBatchId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        Integer conflictsAfterConfirm = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CONFLICT_RELATIONSHIP WHERE MANUSCRIPT_ID = ? AND REVIEWER_ID = 1004 AND SEVERITY = 'HARD'",
                Integer.class,
                fixture.manuscriptId()
        );
        assertThat(conflictsAfterConfirm).isEqualTo(1);
    }

    @Test
    void proposalOperationsExposeMatchingContextAndOverrideAuditHistory() throws Exception {
        AssignmentFixture fixture = seedAcceptedAssignment();
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        addConferenceReviewer(1004, 3);
        mockMvc.perform(post("/api/manuscripts/{manuscriptId}/matching-scores", fixture.manuscriptId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "scoreSource": "TPMS_IMPORT",
                                  "matchingScore": 0.94,
                                  "rationale": "Machine learning and systems subject-area overlap"
                                }
                                """))
                .andExpect(status().isOk());
        MvcResult proposalResult = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-proposals", fixture.roundId())
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"proposalName\":\"Context-rich proposal\",\"limit\":1}"))
                .andExpect(status().isOk())
                .andReturn();
        long bundleId = objectMapper.readTree(proposalResult.getResponse().getContentAsString()).path("bundleId").asLong();

        mockMvc.perform(post("/api/assignment-proposals/{bundleId}/overrides", bundleId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 1004,
                                  "overrideReason": "Chair reviewed soft constraints and approved context"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/assignment-proposals/{bundleId}/context", bundleId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").value(bundleId))
                .andExpect(jsonPath("$.candidates[0].reviewerId").value(1004))
                .andExpect(jsonPath("$.candidates[0].matchingRationale").value("Machine learning and systems subject-area overlap"))
                .andExpect(jsonPath("$.candidates[0].subjectAreas[0]").value("machine learning"))
                .andExpect(jsonPath("$.candidates[0].agentContext.matchingScore").value(0.94))
                .andExpect(jsonPath("$.overrideAudits[0].overrideReason").value("Chair reviewed soft constraints and approved context"));
    }

    private long createReviewerInvitation(String chairToken, long reviewerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conferences/{conferenceId}/reviewer-invitations", 0)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": %d,
                                  "invitationMessage": "Please join the PC",
                                  "expiresAt": "2099-01-01T00:00:00Z"
                                }
                                """.formatted(reviewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationStatus").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("invitationId").asLong();
    }

    private long createProposalBundleForReviewer(String chairToken, AssignmentFixture fixture, long reviewerId) {
        long bundleId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL_BUNDLE.NEXTVAL FROM DUAL", Long.class);
        long proposalId = jdbcTemplate.queryForObject("SELECT SEQ_ASSIGNMENT_PROPOSAL.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL_BUNDLE (
                  BUNDLE_ID, ROUND_ID, CONFERENCE_ID, MANUSCRIPT_ID, PROPOSAL_NAME,
                  BUNDLE_STATUS, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 0, ?, 'Manual bundle', 'PROPOSED', 1003, CURRENT_TIMESTAMP)
                """,
                bundleId,
                fixture.roundId(),
                fixture.manuscriptId()
        );
        jdbcTemplate.update(
                """
                INSERT INTO ASSIGNMENT_PROPOSAL (
                  PROPOSAL_ID, BUNDLE_ID, REVIEWER_ID, RANK_ORDER, MATCHING_SCORE,
                  ELIGIBILITY_STATUS, RATIONALE, CREATED_AT
                ) VALUES (?, ?, ?, 1, 0.77, 'ELIGIBLE', 'seeded confirm-time revalidation candidate', CURRENT_TIMESTAMP)
                """,
                proposalId,
                bundleId,
                reviewerId
        );
        return bundleId;
    }

    private AssignmentFixture seedAcceptedAssignment() {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        long assignmentId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ASSIGNMENT.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CONFERENCE_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, 0, NULL, 'UNDER_REVIEW', 1, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Wave 8 Seed', 'seed abstract', 'seed', 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY, SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 1, ?, 'IN_PROGRESS', 'REALLOCATE_REVIEWERS', 0, ?, 1003, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plusSeconds(86400))
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (
                  ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT, ACCEPTED_AT, DECLINED_AT, DECLINE_REASON, DEADLINE_AT, SUBMITTED_AT, REASSIGNED_FROM_ID
                ) VALUES (?, ?, ?, ?, 1002, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL, ?, NULL, NULL)
                """,
                assignmentId,
                roundId,
                manuscriptId,
                versionId,
                Timestamp.from(Instant.now().plusSeconds(86400))
        );
        return new AssignmentFixture(manuscriptId, roundId, assignmentId);
    }

    private void addConferenceReviewer(long reviewerId, int maxLoad) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_REVIEWER (
                  CONFERENCE_REVIEWER_ID, CONFERENCE_ID, REVIEWER_ID, MEMBERSHIP_STATUS, MAX_LOAD, RESEARCH_AREAS_JSON, INVITED_BY, JOINED_AT, UPDATED_AT
                ) VALUES (
                  SEQ_CONFERENCE_REVIEWER.NEXTVAL, 0, ?, 'ACTIVE', ?, '[]', 1003, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                reviewerId,
                maxLoad
        );
    }

    private void seedLegacyConference() {
        jdbcTemplate.update(
                """
                MERGE INTO CONFERENCE C
                USING (
                  SELECT 0 AS CONFERENCE_ID, 'Legacy / Platform Default' AS NAME, 'LEGACY' AS ACRONYM,
                         2026 AS CONFERENCE_YEAR, 1003 AS ORGANIZER_USER_ID, 'OPEN_FOR_SUBMISSION' AS CONFERENCE_STATUS,
                         'DOUBLE_BLIND' AS BLIND_MODE, 'Default conference for tests.' AS CFP_TEXT,
                         '["LEGACY"]' AS TOPIC_AREAS_JSON, 3 AS TARGET_REVIEWS_PER_PAPER,
                         3 AS DEFAULT_REVIEWER_MAX_LOAD, 'legacy-platform-default' AS PUBLIC_SLUG, 0 AS CFP_PUBLISHED
                  FROM DUAL
                ) S
                ON (C.CONFERENCE_ID = S.CONFERENCE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (
                    CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                    CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                    TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                  ) VALUES (
                    S.CONFERENCE_ID, S.NAME, S.ACRONYM, S.CONFERENCE_YEAR, S.ORGANIZER_USER_ID,
                    S.CONFERENCE_STATUS, S.BLIND_MODE, S.CFP_TEXT, S.TOPIC_AREAS_JSON,
                    S.TARGET_REVIEWS_PER_PAPER, S.DEFAULT_REVIEWER_MAX_LOAD, S.PUBLIC_SLUG, S.CFP_PUBLISHED
                  )
                """
        );
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private record AssignmentFixture(long manuscriptId, long roundId, long assignmentId) {
    }
}
