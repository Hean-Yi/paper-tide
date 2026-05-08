package com.example.review.review;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AssignmentDraftServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAssignmentDraftTables() {
        cleanWaveEightNineTables();
        jdbcTemplate.update("DELETE FROM REVIEW_FORM_RESPONSE");
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
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_DRAFT");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM REVIEWER_BID");
        jdbcTemplate.update("DELETE FROM CONFERENCE_REVIEWER");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_PHASE");
        jdbcTemplate.update("DELETE FROM CONFERENCE");
        seedReviewerUser(1022L, "assignment_conflicted_reviewer_demo", "Southeast University");
        seedReviewerUser(1023L, "assignment_loaded_reviewer_demo", "Reviewer University");
    }

    private void cleanWaveEightNineTables() {
        jdbcTemplate.update("DELETE FROM PROCEEDINGS_EXPORT_BATCH");
        jdbcTemplate.update("DELETE FROM PUBLICATION_METADATA");
        jdbcTemplate.update("DELETE FROM CAMERA_READY_FILE");
        jdbcTemplate.update("DELETE FROM OFFLINE_REVIEW_IMPORT_ROW");
        jdbcTemplate.update("DELETE FROM OFFLINE_REVIEW_IMPORT_BATCH");
        jdbcTemplate.update("DELETE FROM OUTBOUND_EMAIL_HISTORY");
        jdbcTemplate.update("UPDATE EMAIL_TEMPLATE SET ACTIVE_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM EMAIL_TEMPLATE_VERSION");
        jdbcTemplate.update("DELETE FROM EMAIL_TEMPLATE");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_OVERRIDE_AUDIT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_PROPOSAL_BUNDLE");
        jdbcTemplate.update("DELETE FROM REVIEWER_MATCHING_SCORE");
        jdbcTemplate.update("DELETE FROM CONFLICT_RELATIONSHIP");
        jdbcTemplate.update("DELETE FROM EXTERNAL_REVIEWER_DELEGATION");
        jdbcTemplate.update("DELETE FROM REVIEWER_INVITATION");
    }

    @Test
    void generateDraftsFiltersConflictsAndOverloadedReviewersThenConfirmCreatesAssignments() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        long conferenceId = seedConference();
        ManuscriptSeed manuscript = seedSubmittedManuscript(conferenceId);
        long roundId = seedReviewRound(manuscript);
        addConferenceReviewer(conferenceId, 1002L, 2);
        addConferenceReviewer(conferenceId, 1022L, 2);
        addConferenceReviewer(conferenceId, 1023L, 1);
        seedBid(conferenceId, manuscript.manuscriptId(), 1002L, "WANT_TO_REVIEW");
        seedConflict(manuscript.manuscriptId(), 1022L, "INSTITUTION");
        seedExistingAssignmentForLoad(1023L);

        MvcResult generateResult = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/generate", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"limit\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reviewerId").value(1002))
                .andExpect(jsonPath("$[0].rankOrder").value(1))
                .andReturn();

        long draftId = objectMapper.readTree(generateResult.getResponse().getContentAsString())
                .path(0)
                .path("draftId")
                .asLong();

        mockMvc.perform(get("/api/review-rounds/{roundId}/assignment-drafts", roundId)
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].draftStatus").value("PROPOSED"));

        mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/confirm", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "draftIds", List.of(draftId),
                                "deadlineAt", "2026-08-01T00:00:00Z"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reviewerId").value(1002))
                .andExpect(jsonPath("$[0].taskStatus").value("ASSIGNED"));

        Integer assignmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEW_ASSIGNMENT WHERE ROUND_ID = ? AND REVIEWER_ID = 1002",
                Integer.class,
                roundId
        );
        String draftStatus = jdbcTemplate.queryForObject(
                "SELECT DRAFT_STATUS FROM ASSIGNMENT_DRAFT WHERE ASSIGNMENT_DRAFT_ID = ?",
                String.class,
                draftId
        );
        String roundStatus = jdbcTemplate.queryForObject(
                "SELECT ROUND_STATUS FROM REVIEW_ROUND WHERE ROUND_ID = ?",
                String.class,
                roundId
        );

        org.junit.jupiter.api.Assertions.assertEquals(1, assignmentCount);
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED", draftStatus);
        org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", roundStatus);
    }

    @Test
    void confirmDraftsRevalidatesReviewerLoadInsideConfirmation() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        long conferenceId = seedConference();
        ManuscriptSeed manuscript = seedSubmittedManuscript(conferenceId);
        long roundId = seedReviewRound(manuscript);
        addConferenceReviewer(conferenceId, 1002L, 1);

        MvcResult generateResult = mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/generate", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"limit\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();
        long draftId = objectMapper.readTree(generateResult.getResponse().getContentAsString())
                .path(0)
                .path("draftId")
                .asLong();

        seedExistingAssignmentForLoad(1002L);

        mockMvc.perform(post("/api/review-rounds/{roundId}/assignment-drafts/confirm", roundId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "draftIds", List.of(draftId),
                                "deadlineAt", "2026-08-01T00:00:00Z"
                        ))))
                .andExpect(status().isConflict());
    }

    private long seedConference() {
        long conferenceId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE (
                  CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                  CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                  TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                ) VALUES (?, 'Assignment Conference', ?, 2026, 1003, 'REVIEW_ASSIGNMENT',
                  'DOUBLE_BLIND', 'CFP', '["NLP"]', 3, 2, ?, 1)
                """,
                conferenceId,
                "AC" + conferenceId,
                "assignment-" + conferenceId
        );
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_PHASE (
                  PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, SUBMISSION_CLOSE_AT,
                  BIDDING_OPEN_AT, BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                ) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)
                """,
                conferenceId,
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-02T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-10T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-01T00:00:00Z"))
        );
        return conferenceId;
    }

    private ManuscriptSeed seedSubmittedManuscript(long conferenceId) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (
                  MANUSCRIPT_ID, SUBMITTER_ID, CONFERENCE_ID, CURRENT_VERSION_ID,
                  CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT
                ) VALUES (?, 1001, ?, NULL, 'SUBMITTED', 0, 'DOUBLE_BLIND', CURRENT_TIMESTAMP)
                """,
                manuscriptId,
                conferenceId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (
                  VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY
                ) VALUES (?, ?, 1, 'INITIAL', 'Assignment Draft Paper', 'NLP systems abstract', 'nlp,systems', 1001)
                """,
                versionId,
                manuscriptId
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID, MANUSCRIPT_ID, VERSION_ID, USER_ID, AUTHOR_NAME, EMAIL,
                  INSTITUTION, AUTHOR_ORDER, IS_CORRESPONDING, IS_EXTERNAL
                ) VALUES (SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, 1001, 'Author Demo',
                  'author_demo@example.com', 'Southeast University', 1, 1, 0)
                """,
                manuscriptId,
                versionId
        );
        return new ManuscriptSeed(manuscriptId, versionId);
    }

    private long seedReviewRound(ManuscriptSeed manuscript) {
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY,
                  SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 1, ?, 'PENDING', 'REALLOCATE_REVIEWERS', 1, ?, 1003, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscript.manuscriptId(),
                manuscript.versionId(),
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z"))
        );
        return roundId;
    }

    private void addConferenceReviewer(long conferenceId, long reviewerId, int maxLoad) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_REVIEWER (
                  CONFERENCE_REVIEWER_ID, CONFERENCE_ID, REVIEWER_ID, MAX_LOAD, RESEARCH_AREAS_JSON,
                  MEMBERSHIP_STATUS, INVITED_BY, JOINED_AT, UPDATED_AT
                ) VALUES (SEQ_CONFERENCE_REVIEWER.NEXTVAL, ?, ?, ?, '[{"areaCode":"NLP","areaName":"Natural Language Processing"}]',
                  'ACTIVE', 1003, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                conferenceId,
                reviewerId,
                maxLoad
        );
    }

    private void seedBid(long conferenceId, long manuscriptId, long reviewerId, String bidValue) {
        jdbcTemplate.update(
                """
                INSERT INTO REVIEWER_BID (
                  BID_ID, CONFERENCE_ID, MANUSCRIPT_ID, REVIEWER_ID, BID_VALUE, CONFLICT_DECLARED, BID_AT
                ) VALUES (SEQ_REVIEWER_BID.NEXTVAL, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)
                """,
                conferenceId,
                manuscriptId,
                reviewerId,
                bidValue
        );
    }

    private void seedConflict(long manuscriptId, long reviewerId, String conflictType) {
        jdbcTemplate.update(
                """
                INSERT INTO CONFLICT_CHECK_RECORD (
                  CONFLICT_ID, ASSIGNMENT_ID, MANUSCRIPT_ID, REVIEWER_ID, CONFLICT_TYPE,
                  CONFLICT_DESC, SOURCE, DECLARED_BY, DECLARED_AT, DETECTED_AT
                ) VALUES (SEQ_CONFLICT_CHECK_RECORD.NEXTVAL, NULL, ?, ?, ?, 'declared conflict',
                  'SELF_DECLARED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                manuscriptId,
                reviewerId,
                conflictType,
                reviewerId
        );
    }

    private void seedExistingAssignmentForLoad(long reviewerId) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        long roundId = jdbcTemplate.queryForObject("SELECT SEQ_REVIEW_ROUND.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (
                  MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO,
                  BLIND_MODE, SUBMITTED_AT
                ) VALUES (?, 1001, NULL, 'UNDER_REVIEW', 1, 'DOUBLE_BLIND', CURRENT_TIMESTAMP)
                """,
                manuscriptId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (
                  VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, SUBMITTED_BY
                ) VALUES (?, ?, 1, 'INITIAL', 'Existing Load Paper', 'load', 'load', 1001)
                """,
                versionId,
                manuscriptId
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ROUND (
                  ROUND_ID, MANUSCRIPT_ID, ROUND_NO, VERSION_ID, ROUND_STATUS, ASSIGNMENT_STRATEGY,
                  SCREENING_REQUIRED, DEADLINE_AT, CREATED_BY, CREATED_AT
                ) VALUES (?, ?, 1, ?, 'IN_PROGRESS', 'REALLOCATE_REVIEWERS', 1, NULL, 1003, CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                versionId
        );
        jdbcTemplate.update(
                """
                INSERT INTO REVIEW_ASSIGNMENT (
                  ASSIGNMENT_ID, ROUND_ID, MANUSCRIPT_ID, VERSION_ID, REVIEWER_ID, TASK_STATUS, ASSIGNED_AT
                ) VALUES (SEQ_REVIEW_ASSIGNMENT.NEXTVAL, ?, ?, ?, ?, 'ASSIGNED', CURRENT_TIMESTAMP)
                """,
                roundId,
                manuscriptId,
                versionId,
                reviewerId
        );
    }

    private void seedReviewerUser(long userId, String username, String institution) {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER U
                USING (
                  SELECT ? AS USER_ID, ? AS USERNAME, '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq' AS PASSWORD_HASH,
                         ? AS REAL_NAME, ? AS EMAIL, ? AS INSTITUTION, 'ACTIVE' AS STATUS
                  FROM DUAL
                ) S
                ON (U.USERNAME = S.USERNAME)
                WHEN MATCHED THEN
                  UPDATE SET U.PASSWORD_HASH = S.PASSWORD_HASH, U.REAL_NAME = S.REAL_NAME, U.EMAIL = S.EMAIL,
                    U.INSTITUTION = S.INSTITUTION, U.STATUS = S.STATUS
                WHEN NOT MATCHED THEN
                  INSERT (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS, CREATED_AT)
                  VALUES (S.USER_ID, S.USERNAME, S.PASSWORD_HASH, S.REAL_NAME, S.EMAIL, S.INSTITUTION, S.STATUS, CURRENT_TIMESTAMP)
                """,
                userId,
                username,
                username,
                username + "@example.com",
                institution
        );
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT ? AS USER_ROLE_ID, ? AS USER_ID, 2 AS ROLE_ID FROM DUAL) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (S.USER_ROLE_ID, S.USER_ID, S.ROLE_ID)
                """,
                2000 + userId,
                userId
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

    private record ManuscriptSeed(long manuscriptId, long versionId) {
    }
}
