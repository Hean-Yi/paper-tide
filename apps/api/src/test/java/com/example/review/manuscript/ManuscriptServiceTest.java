package com.example.review.manuscript;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ManuscriptServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanManuscriptTables() {
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
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
        seedSecondaryAuthor();
    }

    @Test
    void createDraftManuscriptPersistsAggregate() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        long conferenceId = seedConference("submission-open-2026", "SINGLE_BLIND", "OPEN_FOR_SUBMISSION",
                Instant.parse("2026-12-31T00:00:00Z"));

        MvcResult result = mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createManuscriptPayload("Graph-Aware Ranking", "DOUBLE_BLIND", conferenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manuscriptId").isNumber())
                .andExpect(jsonPath("$.conferenceId").value((int) conferenceId))
                .andExpect(jsonPath("$.currentVersionId").isNumber())
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.blindMode").value("SINGLE_BLIND"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long manuscriptId = root.path("manuscriptId").asLong();
        long versionId = root.path("currentVersionId").asLong();

        Map<String, Object> manuscriptRow = jdbcTemplate.queryForMap(
                """
                SELECT MANUSCRIPT_ID, SUBMITTER_ID, CONFERENCE_ID, CURRENT_VERSION_ID, CURRENT_STATUS, BLIND_MODE
                FROM MANUSCRIPT
                WHERE MANUSCRIPT_ID = ?
                """,
                manuscriptId
        );
        Map<String, Object> versionRow = jdbcTemplate.queryForMap(
                """
                SELECT VERSION_ID, VERSION_NO, VERSION_TYPE, TITLE, SOURCE_DECISION_ID
                FROM MANUSCRIPT_VERSION
                WHERE VERSION_ID = ?
                """,
                versionId
        );

        Integer authorCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM MANUSCRIPT_AUTHOR
                WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ?
                """,
                Integer.class,
                manuscriptId,
                versionId
        );

        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", manuscriptRow.get("CURRENT_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("SINGLE_BLIND", manuscriptRow.get("BLIND_MODE"));
        org.junit.jupiter.api.Assertions.assertEquals(conferenceId, ((Number) manuscriptRow.get("CONFERENCE_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(1001L, ((Number) manuscriptRow.get("SUBMITTER_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(versionId, ((Number) manuscriptRow.get("CURRENT_VERSION_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(1L, ((Number) versionRow.get("VERSION_NO")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("INITIAL", versionRow.get("VERSION_TYPE"));
        org.junit.jupiter.api.Assertions.assertNull(versionRow.get("SOURCE_DECISION_ID"));
        org.junit.jupiter.api.Assertions.assertEquals(2, authorCount);
    }

    @Test
    void createDraftRequiresOpenConferenceAndCopiesConferenceBlindMode() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        long closedConferenceId = seedConference("closed-2026", "DOUBLE_BLIND", "SUBMISSION_CLOSED",
                Instant.parse("2026-12-31T00:00:00Z"));
        long expiredConferenceId = seedConference("expired-2026", "OPEN", "OPEN_FOR_SUBMISSION",
                Instant.parse("2020-01-01T00:00:00Z"));

        mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createManuscriptPayload("Missing Conference", "DOUBLE_BLIND", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createManuscriptPayload("Closed Conference", "DOUBLE_BLIND", closedConferenceId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createManuscriptPayload("Expired Conference", "DOUBLE_BLIND", expiredConferenceId)))
                .andExpect(status().isConflict());
    }

    @Test
    void submitAndPdfReplacementRejectAfterSubmissionClose() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        long conferenceId = seedConference("deadline-2026", "DOUBLE_BLIND", "OPEN_FOR_SUBMISSION",
                Instant.parse("2026-12-31T00:00:00Z"));
        ManuscriptIds ids = createDraftManuscript(token, "Deadline Guard", "OPEN", conferenceId);
        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "paper.pdf", "application/pdf", validPdfBytes("deadline"))
                .andExpect(status().isOk());
        closeSubmission(conferenceId, Instant.parse("2020-01-01T00:00:00Z"));

        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "replacement.pdf", "application/pdf", validPdfBytes("late"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", ids.manuscriptId(), ids.versionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void submitDraftVersionMovesStatusToSubmitted() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Temporal Graphs", "OPEN");

        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "paper.pdf", "application/pdf", validPdfBytes("draft pdf"));

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", ids.manuscriptId(), ids.versionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"));

        Map<String, Object> manuscriptRow = jdbcTemplate.queryForMap(
                "SELECT CURRENT_STATUS, SUBMITTED_AT FROM MANUSCRIPT WHERE MANUSCRIPT_ID = ?",
                ids.manuscriptId()
        );
        Map<String, Object> versionRow = jdbcTemplate.queryForMap(
                "SELECT SUBMITTED_AT FROM MANUSCRIPT_VERSION WHERE VERSION_ID = ?",
                ids.versionId()
        );

        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", manuscriptRow.get("CURRENT_STATUS"));
        org.junit.jupiter.api.Assertions.assertNotNull(manuscriptRow.get("SUBMITTED_AT"));
        org.junit.jupiter.api.Assertions.assertNotNull(versionRow.get("SUBMITTED_AT"));
    }

    @Test
    void submitDraftWithoutPdfIsRejected() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "No PDF Yet", "SINGLE_BLIND");

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", ids.manuscriptId(), ids.versionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRevisionRequiresRevisionRequiredStatus() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Revision Guard", "DOUBLE_BLIND");

        mockMvc.perform(post("/api/manuscripts/{id}/versions", ids.manuscriptId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createRevisionPayload("Revision Guard v2")))
                .andExpect(status().isConflict());
    }

    @Test
    void submitRevisionMovesStatusToRevisedSubmitted() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds initialIds = createDraftManuscript(token, "Needs Revision", "DOUBLE_BLIND");

        jdbcTemplate.update(
                """
                UPDATE MANUSCRIPT
                SET CURRENT_STATUS = ?, LAST_DECISION_CODE = ?, SUBMITTED_AT = ?
                WHERE MANUSCRIPT_ID = ?
                """,
                "REVISION_REQUIRED",
                "MAJOR_REVISION",
                Timestamp.from(Instant.now()),
                initialIds.manuscriptId()
        );

        MvcResult createRevision = mockMvc.perform(post("/api/manuscripts/{id}/versions", initialIds.manuscriptId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createRevisionPayload("Needs Revision v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("REVISION_REQUIRED"))
                .andExpect(jsonPath("$.currentVersionId").isNumber())
                .andReturn();

        long revisionVersionId = objectMapper.readTree(createRevision.getResponse().getContentAsString())
                .path("currentVersionId")
                .asLong();

        uploadPdf(token, initialIds.manuscriptId(), revisionVersionId, "revision.pdf", "application/pdf", validPdfBytes("revision pdf"));

        mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/submit", initialIds.manuscriptId(), revisionVersionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("REVISED_SUBMITTED"));

        Map<String, Object> manuscriptRow = jdbcTemplate.queryForMap(
                "SELECT CURRENT_STATUS, CURRENT_VERSION_ID FROM MANUSCRIPT WHERE MANUSCRIPT_ID = ?",
                initialIds.manuscriptId()
        );
        Map<String, Object> versionRow = jdbcTemplate.queryForMap(
                "SELECT VERSION_NO, VERSION_TYPE FROM MANUSCRIPT_VERSION WHERE VERSION_ID = ?",
                revisionVersionId
        );

        org.junit.jupiter.api.Assertions.assertEquals("REVISED_SUBMITTED", manuscriptRow.get("CURRENT_STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals(revisionVersionId, ((Number) manuscriptRow.get("CURRENT_VERSION_ID")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(2L, ((Number) versionRow.get("VERSION_NO")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("REVISION", versionRow.get("VERSION_TYPE"));
    }

    @Test
    void uploadPdfRejectsNonPdfFile() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Wrong File", "DOUBLE_BLIND");

        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "notes.txt", "text/plain", "not a pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadPdfRejectsPdfContentWithoutPdfMagic() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Spoofed PDF", "DOUBLE_BLIND");

        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "spoofed.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
        void uploadPdfRejectsFilesOverOneHundredMegabytesBeforeReadingBody() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Huge PDF", "DOUBLE_BLIND");
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge.pdf",
                "application/pdf",
                validPdfBytes("small body")
        ) {
            @Override
            public long getSize() {
                                return 100L * 1024L * 1024L + 1L;
            }
        };

        mockMvc.perform(multipart("/api/manuscripts/{id}/versions/{versionId}/pdf", ids.manuscriptId(), ids.versionId())
                        .file(largeFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadPdfReturnsStoredFileForSubmitter() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(token, "Downloadable", "DOUBLE_BLIND");
        byte[] pdfBytes = validPdfBytes("stored pdf");

        uploadPdf(token, ids.manuscriptId(), ids.versionId(), "downloadable.pdf", "application/pdf", pdfBytes)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/pdf", ids.manuscriptId(), ids.versionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("downloadable.pdf")))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void onlySubmitterCanAccessManuscript() throws Exception {
        String authorToken = loginAndExtractToken("author_demo", "demo123");
        String otherAuthorToken = loginAndExtractToken("second_author_demo", "demo123");
        ManuscriptIds ids = createDraftManuscript(authorToken, "Private Paper", "DOUBLE_BLIND");

        mockMvc.perform(get("/api/manuscripts/{id}", ids.manuscriptId())
                        .header("Authorization", "Bearer " + otherAuthorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/manuscripts/{id}/versions", ids.manuscriptId())
                        .header("Authorization", "Bearer " + otherAuthorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/manuscripts/{id}/versions/{versionId}/pdf", ids.manuscriptId(), ids.versionId())
                        .header("Authorization", "Bearer " + otherAuthorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listManuscriptsAndVersionsReturnAuthorOwnedData() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");
        ManuscriptIds firstIds = createDraftManuscript(token, "First Paper", "DOUBLE_BLIND");
        ManuscriptIds secondIds = createDraftManuscript(token, "Second Paper", "OPEN");

        jdbcTemplate.update(
                """
                UPDATE MANUSCRIPT
                SET CURRENT_STATUS = ?, LAST_DECISION_CODE = ?, SUBMITTED_AT = ?
                WHERE MANUSCRIPT_ID = ?
                """,
                "REVISION_REQUIRED",
                "MINOR_REVISION",
                Timestamp.from(Instant.now()),
                secondIds.manuscriptId()
        );

        MvcResult revisionResult = mockMvc.perform(post("/api/manuscripts/{id}/versions", secondIds.manuscriptId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createRevisionPayload("Second Paper v2")))
                .andExpect(status().isOk())
                .andReturn();

        long revisionVersionId = objectMapper.readTree(revisionResult.getResponse().getContentAsString())
                .path("currentVersionId")
                .asLong();

        mockMvc.perform(get("/api/manuscripts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].manuscriptId").isNumber())
                .andExpect(jsonPath("$[0].currentStatus", not(blankOrNullString())))
                .andExpect(jsonPath("$[0].currentVersionTitle", not(blankOrNullString())));

        mockMvc.perform(get("/api/manuscripts/{id}/versions", secondIds.manuscriptId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo", is(1)))
                .andExpect(jsonPath("$[1].versionId", is((int) revisionVersionId)))
                .andExpect(jsonPath("$[1].versionType", is("REVISION")));

        mockMvc.perform(get("/api/manuscripts/{id}", firstIds.manuscriptId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manuscriptId", is((int) firstIds.manuscriptId())))
                .andExpect(jsonPath("$.currentVersionId", is((int) firstIds.versionId())))
                .andExpect(jsonPath("$.blindMode", is("DOUBLE_BLIND")));
    }

    private org.springframework.test.web.servlet.ResultActions uploadPdf(
            String token,
            long manuscriptId,
            long versionId,
            String fileName,
            String contentType,
            byte[] bytes
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, bytes);

        return mockMvc.perform(multipart("/api/manuscripts/{id}/versions/{versionId}/pdf", manuscriptId, versionId)
                .file(file)
                .header("Authorization", "Bearer " + token));
    }

    private byte[] validPdfBytes(String marker) {
        return ("%PDF-1.4\n% " + marker + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private ManuscriptIds createDraftManuscript(String token, String title, String blindMode) throws Exception {
        long conferenceId = seedConference("test-conf-" + title.toLowerCase().replaceAll("[^a-z0-9]+", "-"),
                blindMode,
                "OPEN_FOR_SUBMISSION",
                Instant.parse("2026-12-31T00:00:00Z"));
        return createDraftManuscript(token, title, blindMode, conferenceId);
    }

    private ManuscriptIds createDraftManuscript(String token, String title, String blindMode, long conferenceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/manuscripts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createManuscriptPayload(title, blindMode, conferenceId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new ManuscriptIds(root.path("manuscriptId").asLong(), root.path("currentVersionId").asLong());
    }

    private String createManuscriptPayload(String title, String blindMode) throws Exception {
        long conferenceId = seedConference("payload-conf-" + title.toLowerCase().replaceAll("[^a-z0-9]+", "-"),
                blindMode,
                "OPEN_FOR_SUBMISSION",
                Instant.parse("2026-12-31T00:00:00Z"));
        return createManuscriptPayload(title, blindMode, conferenceId);
    }

    private String createManuscriptPayload(String title, String blindMode, Long conferenceId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("abstract", title + " abstract");
        payload.put("keywords", "graphs,ranking");
        payload.put("blindMode", blindMode);
        payload.put("conferenceId", conferenceId);
        payload.put("authors", List.of(
                Map.of(
                        "authorName", "Author Demo",
                        "email", "author_demo@example.com",
                        "institution", "Southeast University",
                        "authorOrder", 1,
                        "userId", 1001,
                        "isCorresponding", true,
                        "isExternal", false
                ),
                Map.of(
                        "authorName", "External Collaborator",
                        "email", "external@example.com",
                        "institution", "Zhejiang University",
                        "authorOrder", 2,
                        "isCorresponding", false,
                        "isExternal", true
                )
        ));
        return objectMapper.writeValueAsString(payload);
    }

    private long seedConference(String slug, String blindMode, String status, Instant submissionCloseAt) {
        long conferenceId = jdbcTemplate.queryForObject("SELECT SEQ_CONFERENCE.NEXTVAL FROM DUAL", Long.class);
        Instant submissionOpenAt = submissionCloseAt.minusSeconds(31536000);
        jdbcTemplate.update(
                """
                INSERT INTO CONFERENCE (
                  CONFERENCE_ID, NAME, ACRONYM, CONFERENCE_YEAR, ORGANIZER_USER_ID,
                  CONFERENCE_STATUS, BLIND_MODE, CFP_TEXT, TOPIC_AREAS_JSON,
                  TARGET_REVIEWS_PER_PAPER, DEFAULT_REVIEWER_MAX_LOAD, PUBLIC_SLUG, CFP_PUBLISHED
                ) VALUES (?, ?, ?, 2026, 1003, ?, ?, 'Call for papers', '["NLP"]', 3, 3, ?, 1)
                """,
                conferenceId,
                "Test Conference " + conferenceId,
                "TC" + conferenceId,
                status,
                blindMode,
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
                Timestamp.from(submissionOpenAt),
                Timestamp.from(submissionCloseAt),
                Timestamp.from(submissionCloseAt.plusSeconds(86400)),
                Timestamp.from(submissionCloseAt.plusSeconds(172800)),
                Timestamp.from(submissionCloseAt.plusSeconds(259200)),
                Timestamp.from(submissionCloseAt.plusSeconds(345600))
        );
        return conferenceId;
    }

    private void closeSubmission(long conferenceId, Instant submissionCloseAt) {
        jdbcTemplate.update(
                """
                UPDATE CONFERENCE_PHASE
                SET SUBMISSION_OPEN_AT = ?, SUBMISSION_CLOSE_AT = ?, BIDDING_OPEN_AT = ?, BIDDING_CLOSE_AT = ?,
                    REVIEW_DEADLINE_AT = ?, DECISION_RELEASE_AT = ?
                WHERE CONFERENCE_ID = ?
                """,
                Timestamp.from(Instant.parse("2019-01-01T00:00:00Z")),
                Timestamp.from(submissionCloseAt),
                Timestamp.from(submissionCloseAt.plusSeconds(86400)),
                Timestamp.from(submissionCloseAt.plusSeconds(172800)),
                Timestamp.from(submissionCloseAt.plusSeconds(259200)),
                Timestamp.from(submissionCloseAt.plusSeconds(345600)),
                conferenceId
        );
    }

    private String createRevisionPayload(String title) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "abstract", title + " abstract",
                "keywords", "graphs,revision",
                "authors", List.of(
                        Map.of(
                                "authorName", "Author Demo",
                                "email", "author_demo@example.com",
                                "institution", "Southeast University",
                                "authorOrder", 1,
                                "userId", 1001,
                                "isCorresponding", true,
                                "isExternal", false
                        ),
                        Map.of(
                                "authorName", "External Collaborator",
                                "email", "external@example.com",
                                "institution", "Zhejiang University",
                                "authorOrder", 2,
                                "isCorresponding", false,
                                "isExternal", true
                        )
                )
        ));
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

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("token").asText();
    }

    private void seedSecondaryAuthor() {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER U
                USING (
                  SELECT 1011 AS USER_ID, 'second_author_demo' AS USERNAME, '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq' AS PASSWORD_HASH,
                         'Second Author Demo' AS REAL_NAME, 'second_author_demo@example.com' AS EMAIL, 'Suzhou University' AS INSTITUTION, 'ACTIVE' AS STATUS
                  FROM DUAL
                ) S
                ON (U.USERNAME = S.USERNAME)
                WHEN MATCHED THEN
                  UPDATE SET U.PASSWORD_HASH = S.PASSWORD_HASH, U.REAL_NAME = S.REAL_NAME, U.EMAIL = S.EMAIL, U.INSTITUTION = S.INSTITUTION, U.STATUS = S.STATUS
                WHEN NOT MATCHED THEN
                  INSERT (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS, CREATED_AT)
                  VALUES (S.USER_ID, S.USERNAME, S.PASSWORD_HASH, S.REAL_NAME, S.EMAIL, S.INSTITUTION, S.STATUS, CURRENT_TIMESTAMP)
                """
        );
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (SELECT 1111 AS USER_ROLE_ID, 1011 AS USER_ID, 1 AS ROLE_ID FROM DUAL) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (S.USER_ROLE_ID, S.USER_ID, S.ROLE_ID)
                """
        );
    }

    private record ManuscriptIds(long manuscriptId, long versionId) {
    }
}
