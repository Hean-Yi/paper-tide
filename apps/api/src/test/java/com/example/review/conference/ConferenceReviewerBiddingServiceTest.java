package com.example.review.conference;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class ConferenceReviewerBiddingServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBiddingTables() {
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEWER_BID");
        jdbcTemplate.update("DELETE FROM CONFERENCE_REVIEWER");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM ASSIGNMENT_DRAFT");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
        jdbcTemplate.update("DELETE FROM CONFERENCE_PHASE");
        jdbcTemplate.update("DELETE FROM CONFERENCE");
    }

    @Test
    void chairAddsPlatformReviewerToConferencePoolWithResearchAreaSnapshot() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        long conferenceId = seedConference("pool-2026", "BIDDING_OPEN", Instant.parse("2026-12-31T00:00:00Z"));

        mockMvc.perform(post("/api/chair/conferences/{conferenceId}/reviewers", conferenceId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewerId", 1002,
                                "maxLoad", 5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conferenceId").value((int) conferenceId))
                .andExpect(jsonPath("$.reviewerId").value(1002))
                .andExpect(jsonPath("$.maxLoad").value(5))
                .andExpect(jsonPath("$.researchAreas[0].areaCode").value("NLP"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT MAX_LOAD, RESEARCH_AREAS_JSON
                FROM CONFERENCE_REVIEWER
                WHERE CONFERENCE_ID = ? AND REVIEWER_ID = 1002
                """,
                conferenceId
        );
        org.junit.jupiter.api.Assertions.assertEquals(5L, ((Number) row.get("MAX_LOAD")).longValue());
        org.junit.jupiter.api.Assertions.assertTrue(row.get("RESEARCH_AREAS_JSON").toString().contains("Natural Language Processing"));
    }

    @Test
    void reviewerBiddingViewIsConferenceScopedAndDeidentified() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        long visibleConferenceId = seedConference("visible-2026", "BIDDING_OPEN", Instant.parse("2026-12-31T00:00:00Z"));
        long hiddenConferenceId = seedConference("hidden-2026", "BIDDING_OPEN", Instant.parse("2026-12-31T00:00:00Z"));
        long visibleManuscriptId = seedSubmittedManuscript(visibleConferenceId, "Visible Paper",
                "This paper cites Author Demo from Southeast University.", "nlp,self");
        seedSubmittedManuscript(hiddenConferenceId, "Hidden Paper", "Should not be visible.", "ml");

        addReviewer(chairToken, visibleConferenceId, 1002L);

        mockMvc.perform(get("/api/reviewer/conferences/{conferenceId}/bids/open", visibleConferenceId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].manuscriptId").value((int) visibleManuscriptId))
                .andExpect(jsonPath("$[0].title").value("Visible Paper"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Hidden Paper"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Author Demo"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Southeast University"))));
    }

    @Test
    void reviewerBidPersistsAndReusesConflictCheckRecordWithoutAssignment() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        long conferenceId = seedConference("bid-2026", "BIDDING_OPEN", Instant.parse("2026-12-31T00:00:00Z"));
        long manuscriptId = seedSubmittedManuscript(conferenceId, "Bid Paper", "Bid abstract", "nlp");
        addReviewer(chairToken, conferenceId, 1002L);

        mockMvc.perform(post("/api/reviewer/conferences/{conferenceId}/bids", conferenceId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manuscriptId", manuscriptId,
                                "bidValue", "WANT_TO_REVIEW",
                                "conflictDeclared", true,
                                "conflictType", "INSTITUTION",
                                "conflictDescription", "Recent institutional overlap"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidValue").value("WANT_TO_REVIEW"))
                .andExpect(jsonPath("$.conflictDeclared").value(true));

        Integer bidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM REVIEWER_BID WHERE CONFERENCE_ID = ? AND MANUSCRIPT_ID = ? AND REVIEWER_ID = 1002",
                Integer.class,
                conferenceId,
                manuscriptId
        );
        Integer conflictCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM CONFLICT_CHECK_RECORD
                WHERE ASSIGNMENT_ID IS NULL
                  AND MANUSCRIPT_ID = ?
                  AND REVIEWER_ID = 1002
                  AND CONFLICT_TYPE = 'INSTITUTION'
                """,
                Integer.class,
                manuscriptId
        );

        org.junit.jupiter.api.Assertions.assertEquals(1, bidCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, conflictCount);
    }

    @Test
    void biddingRejectsAfterBiddingCloseEvenIfStatusStillOpen() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");
        long conferenceId = seedConference("late-bid-2026", "BIDDING_OPEN", Instant.parse("2020-01-02T00:00:00Z"));
        long manuscriptId = seedSubmittedManuscript(conferenceId, "Late Paper", "Late abstract", "systems");
        addReviewer(chairToken, conferenceId, 1002L);

        mockMvc.perform(post("/api/reviewer/conferences/{conferenceId}/bids", conferenceId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manuscriptId", manuscriptId,
                                "bidValue", "NEUTRAL"
                        ))))
                .andExpect(status().isConflict());
    }

    private void addReviewer(String chairToken, long conferenceId, long reviewerId) throws Exception {
        mockMvc.perform(post("/api/chair/conferences/{conferenceId}/reviewers", conferenceId)
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewerId", reviewerId,
                                "maxLoad", 4
                        ))))
                .andExpect(status().isOk());
    }

    private long seedConference(String slug, String status, Instant biddingCloseAt) {
        long conferenceId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE (
                  CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                  CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                  TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                ) VALUES (?, ?, ?, 2026, 1003, ?, 'DOUBLE_BLIND', 'Call for papers', '["NLP"]', 3, 4, ?, 1)
                """,
                conferenceId,
                "Bidding Conference " + conferenceId,
                "BC" + conferenceId,
                status,
                slug
        );
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE_PHASE (
                  PHASE_ID, CONFERENCE_ID, SUBMISSION_OPEN_AT, SUBMISSION_CLOSE_AT,
                  BIDDING_OPEN_AT, BIDDING_CLOSE_AT, REVIEW_DEADLINE_AT, DECISION_RELEASE_AT
                ) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)
                """,
                conferenceId,
                Timestamp.from(biddingCloseAt.minusSeconds(31_536_000)),
                Timestamp.from(biddingCloseAt.minusSeconds(172_800)),
                Timestamp.from(biddingCloseAt.minusSeconds(86_400)),
                Timestamp.from(biddingCloseAt),
                Timestamp.from(biddingCloseAt.plusSeconds(86_400)),
                Timestamp.from(biddingCloseAt.plusSeconds(172_800))
        );
        return conferenceId;
    }

    private long seedSubmittedManuscript(long conferenceId, String title, String abstractText, String keywords) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (
                  MANUSCRIPT_ID, SUBMITTER_ID, CONFERENCE_ID, CURRENT_VERSION_ID, CURRENT_STATUS,
                  CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT
                ) VALUES (?, 1001, ?, NULL, 'SUBMITTED', 0, 'DOUBLE_BLIND', CURRENT_TIMESTAMP)
                """,
                manuscriptId,
                conferenceId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (
                  VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT,
                  KEYWORDS, SUBMITTED_BY
                ) VALUES (?, ?, 1, 'INITIAL', ?, ?, ?, 1001)
                """,
                versionId,
                manuscriptId,
                title,
                abstractText,
                keywords
        );
        jdbcTemplate.update(
                "UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?",
                versionId,
                manuscriptId
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID, MANUSCRIPT_ID, VERSION_ID, USER_ID, AUTHOR_NAME,
                  EMAIL, INSTITUTION, AUTHOR_ORDER, IS_CORRESPONDING, IS_EXTERNAL
                ) VALUES (SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, 1001, 'Author Demo',
                  'author_demo@example.com', 'Southeast University', 1, 1, 0)
                """,
                manuscriptId,
                versionId
        );
        return manuscriptId;
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
}
