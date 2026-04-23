package com.example.review.agent;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.review.support.LegacyAgentArtifactsCleanup;

@SpringBootTest
@AutoConfigureMockMvc
class AgentIntegrationServiceTest {
    private static final byte[] PDF_BYTES = "%PDF-1.4\n% test pdf\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanAgentTables() {
        LegacyAgentArtifactsCleanup.deleteLegacyAgentArtifacts(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM ANALYSIS_OUTBOX");
        jdbcTemplate.update("DELETE FROM ANALYSIS_PROJECTION");
        jdbcTemplate.update("DELETE FROM ANALYSIS_INTENT");
        jdbcTemplate.update("DELETE FROM REVIEW_REPORT");
        jdbcTemplate.update("DELETE FROM CONFLICT_CHECK_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ASSIGNMENT");
        jdbcTemplate.update("DELETE FROM SYS_NOTIFICATION");
        jdbcTemplate.update("UPDATE MANUSCRIPT_VERSION SET SOURCE_DECISION_ID = NULL");
        jdbcTemplate.update("DELETE FROM DECISION_RECORD");
        jdbcTemplate.update("DELETE FROM REVIEW_ROUND");
        jdbcTemplate.update("DELETE FROM AUDIT_LOG");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_AUTHOR");
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = NULL");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT_VERSION");
        jdbcTemplate.update("DELETE FROM MANUSCRIPT");
    }

    @Test
    void screeningAnalysisRequestCreatesIntentOutboxWithoutLegacyTaskSubmission() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(true);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post(
                        "/api/manuscripts/{id}/versions/{versionId}/screening-analysis",
                        fixture.manuscriptId(),
                        fixture.versionId()
                )
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "force": false
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisType").value("SCREENING"))
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.externalTaskId").doesNotExist());

        JsonNode message = objectMapper.readTree(
                jdbcTemplate.queryForObject("SELECT MESSAGE_PAYLOAD FROM ANALYSIS_OUTBOX", String.class)
        );
        Assertions.assertEquals("SCREENING", message.get("analysisType").asText());
        Assertions.assertTrue(message.hasNonNull("idempotencyKey"));
        Assertions.assertEquals(fixture.manuscriptId(), message.at("/requestPayload/screening/manuscriptId").asLong());
        Assertions.assertEquals(fixture.versionId(), message.at("/requestPayload/screening/versionId").asLong());
    }

    @Test
    void screeningAnalysisRequestFailsWhenPdfMissing() throws Exception {
        ManuscriptFixture fixture = seedSubmittedManuscript(false);
        String chairToken = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(post(
                        "/api/manuscripts/{id}/versions/{versionId}/screening-analysis",
                        fixture.manuscriptId(),
                        fixture.versionId()
                )
                        .header("Authorization", "Bearer " + chairToken)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ANALYSIS_OUTBOX", Integer.class);
        Assertions.assertEquals(0, outboxCount);
    }

    private ManuscriptFixture seedSubmittedManuscript(boolean withPdf) {
        long manuscriptId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT.NEXTVAL FROM DUAL", Long.class);
        long versionId = jdbcTemplate.queryForObject("SELECT SEQ_MANUSCRIPT_VERSION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT (MANUSCRIPT_ID, SUBMITTER_ID, CURRENT_VERSION_ID, CURRENT_STATUS, CURRENT_ROUND_NO, BLIND_MODE, SUBMITTED_AT, LAST_DECISION_CODE)
                VALUES (?, 1001, NULL, 'SUBMITTED', 0, 'DOUBLE_BLIND', ?, NULL)
                """,
                manuscriptId,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_VERSION (VERSION_ID, MANUSCRIPT_ID, VERSION_NO, VERSION_TYPE, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE, SUBMITTED_BY, SUBMITTED_AT, SOURCE_DECISION_ID)
                VALUES (?, ?, 1, 'INITIAL', 'Workflow Seed', 'workflow abstract', 'workflow,pdf', ?, ?, ?, 1001, ?, NULL)
                """,
                versionId,
                manuscriptId,
                withPdf ? PDF_BYTES : null,
                withPdf ? "workflow.pdf" : null,
                withPdf ? PDF_BYTES.length : null,
                Timestamp.from(Instant.now())
        );
        jdbcTemplate.update("UPDATE MANUSCRIPT SET CURRENT_VERSION_ID = ? WHERE MANUSCRIPT_ID = ?", versionId, manuscriptId);
        jdbcTemplate.update(
                """
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID, MANUSCRIPT_ID, VERSION_ID, USER_ID, AUTHOR_NAME, EMAIL, INSTITUTION, AUTHOR_ORDER, IS_CORRESPONDING, IS_EXTERNAL
                ) VALUES (
                  SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, 1001, 'Author Demo', 'author_demo@example.com', 'Southeast University', 1, 1, 0
                )
                """,
                manuscriptId,
                versionId
        );
        return new ManuscriptFixture(manuscriptId, versionId);
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

    private record ManuscriptFixture(long manuscriptId, long versionId) {
    }
}
