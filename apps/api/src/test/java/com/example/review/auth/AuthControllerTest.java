package com.example.review.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRegisteredAuthFlowUsers() {
        jdbcTemplate.update(
                """
                DELETE FROM EMAIL_VERIFICATION_TOKEN
                WHERE USER_ID IN (SELECT USER_ID FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%')
                """
        );
        jdbcTemplate.update(
                """
                DELETE FROM ROLE_APPLICATION
                WHERE USER_ID IN (SELECT USER_ID FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%')
                """
        );
        jdbcTemplate.update(
                """
                DELETE FROM USER_RESEARCH_AREA
                WHERE USER_ID IN (SELECT USER_ID FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%')
                """
        );
        jdbcTemplate.update(
                """
                DELETE FROM USER_ACADEMIC_PROFILE
                WHERE USER_ID IN (SELECT USER_ID FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%')
                """
        );
        jdbcTemplate.update(
                """
                DELETE FROM SYS_USER_ROLE
                WHERE USER_ID IN (SELECT USER_ID FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%')
                """
        );
        jdbcTemplate.update("DELETE FROM SYS_USER WHERE USERNAME LIKE 'auth_flow_%'");
    }

    @Test
    void loginReturnsJwtWhenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "author_demo",
                                  "password": "demo123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())));
    }

    @Test
    void registeredAuthorCanLoginImmediatelyWithAuthorRole() throws Exception {
        registerAuthor("auth_flow_author");

        String token = loginAndExtractToken("auth_flow_author", "demo123");
        JsonNode claims = tokenClaims(token);

        org.junit.jupiter.api.Assertions.assertEquals("auth_flow_author", claims.path("sub").asText());
        org.junit.jupiter.api.Assertions.assertEquals("AUTHOR", claims.path("roles").get(0).asText());
    }

    @Test
    void registeredReviewerCanLoginImmediatelyWhileRoleApplicationAwaitsApproval() throws Exception {
        registerReviewer("auth_flow_reviewer");

        String token = loginAndExtractToken("auth_flow_reviewer", "demo123");
        JsonNode claims = tokenClaims(token);
        String applicationStatus = jdbcTemplate.queryForObject(
                """
                SELECT RA.APPLICATION_STATUS
                FROM ROLE_APPLICATION RA
                JOIN SYS_USER U ON U.USER_ID = RA.USER_ID
                WHERE U.USERNAME = ?
                """,
                String.class,
                "auth_flow_reviewer"
        );

        org.junit.jupiter.api.Assertions.assertEquals("auth_flow_reviewer", claims.path("sub").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, claims.path("roles").size());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_ADMIN_APPROVAL", applicationStatus);
    }

    @Test
    void registeredOrganizerCanLoginImmediatelyWhileChairRoleAwaitsApproval() throws Exception {
        registerOrganizer("auth_flow_organizer");

        String token = loginAndExtractToken("auth_flow_organizer", "demo123");
        JsonNode claims = tokenClaims(token);
        String applicationStatus = jdbcTemplate.queryForObject(
                """
                SELECT RA.APPLICATION_STATUS
                FROM ROLE_APPLICATION RA
                JOIN SYS_USER U ON U.USER_ID = RA.USER_ID
                WHERE U.USERNAME = ?
                """,
                String.class,
                "auth_flow_organizer"
        );

        org.junit.jupiter.api.Assertions.assertEquals("auth_flow_organizer", claims.path("sub").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, claims.path("roles").size());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_ADMIN_APPROVAL", applicationStatus);
    }

    @Test
    void legacyPendingEmailAuthorIsActivatedOnFirstSuccessfulLogin() throws Exception {
        registerLegacyPendingEmailUser("auth_flow_legacy_author", "AUTHOR");

        String token = loginAndExtractToken("auth_flow_legacy_author", "demo123");
        JsonNode claims = tokenClaims(token);

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", userStatus("auth_flow_legacy_author"));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", applicationStatus("auth_flow_legacy_author"));
        org.junit.jupiter.api.Assertions.assertEquals("AUTHOR", claims.path("roles").get(0).asText());
    }

    @Test
    void legacyPendingEmailReviewerIsActivatedOnFirstSuccessfulLoginAndQueuedForApproval() throws Exception {
        registerLegacyPendingEmailUser("auth_flow_legacy_reviewer", "REVIEWER");

        String token = loginAndExtractToken("auth_flow_legacy_reviewer", "demo123");
        JsonNode claims = tokenClaims(token);

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", userStatus("auth_flow_legacy_reviewer"));
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_ADMIN_APPROVAL", applicationStatus("auth_flow_legacy_reviewer"));
        org.junit.jupiter.api.Assertions.assertEquals(0, claims.path("roles").size());
    }

    @Test
    void legacyPendingEmailOrganizerIsActivatedOnFirstSuccessfulLoginAndQueuedForApproval() throws Exception {
        registerLegacyPendingEmailUser("auth_flow_legacy_organizer", "ORGANIZER");

        String token = loginAndExtractToken("auth_flow_legacy_organizer", "demo123");
        JsonNode claims = tokenClaims(token);

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", userStatus("auth_flow_legacy_organizer"));
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_ADMIN_APPROVAL", applicationStatus("auth_flow_legacy_organizer"));
        org.junit.jupiter.api.Assertions.assertEquals(0, claims.path("roles").size());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "author_demo",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())));
    }

    @Test
    void loginRejectsDisabledUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "disabled_demo",
                                  "password": "demo123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manuscriptEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/manuscripts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())));
    }

    @Test
    void nonApiFallbackRoutesAreDenied() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");

        mockMvc.perform(get("/internal/debug")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void manuscriptEndpointAcceptsValidJwt() throws Exception {
        String token = loginAndExtractToken("author_demo", "demo123");

        mockMvc.perform(get("/api/manuscripts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void decisionEndpointRejectsNonChairRoles() throws Exception {
        String token = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(get("/api/decisions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void decisionEndpointAcceptsChairRole() throws Exception {
        String token = loginAndExtractToken("chair_demo", "demo123");

        mockMvc.perform(get("/api/decisions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void auditLogEndpointAcceptsOnlyAdminRole() throws Exception {
        String chairToken = loginAndExtractToken("chair_demo", "demo123");
        String adminToken = loginAndExtractToken("admin_demo", "demo123");

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + chairToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
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

    private void registerAuthor(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationType": "AUTHOR",
                                  "username": "%s",
                                  "password": "demo123",
                                  "realName": "Auth Flow Author",
                                  "email": "%s@example.com",
                                  "institution": "Southeast University",
                                  "academicProfile": null,
                                  "researchAreas": []
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false));
    }

    private void registerReviewer(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationType": "REVIEWER",
                                  "username": "%s",
                                  "password": "demo123",
                                  "realName": "Auth Flow Reviewer",
                                  "email": "%s@example.com",
                                  "institution": "Nanjing University",
                                  "academicProfile": {
                                    "homepageUrl": "https://example.edu/auth-flow-reviewer",
                                    "orcid": null,
                                    "dblpUrl": null,
                                    "googleScholarUrl": null,
                                    "representativeWorks": ["Representative Systems Paper"],
                                    "conflictDomains": ["example.edu"],
                                    "defaultMaxLoad": 3,
                                    "plannedConferenceTitle": null
                                  },
                                  "researchAreas": [
                                    { "areaCode": "NLP", "areaName": "Natural Language Processing" }
                                  ]
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("PENDING_ADMIN_APPROVAL"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false));
    }

    private void registerOrganizer(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationType": "ORGANIZER",
                                  "username": "%s",
                                  "password": "demo123",
                                  "realName": "Auth Flow Organizer",
                                  "email": "%s@example.com",
                                  "institution": "Fudan University",
                                  "academicProfile": {
                                    "homepageUrl": "https://example.edu/auth-flow-organizer",
                                    "orcid": null,
                                    "dblpUrl": null,
                                    "googleScholarUrl": "https://scholar.google.com/citations?user=demo",
                                    "representativeWorks": ["Conference Organization Record"],
                                    "conflictDomains": ["example.edu"],
                                    "defaultMaxLoad": 3,
                                    "plannedConferenceTitle": "International Conference on Review Systems"
                                  },
                                  "researchAreas": []
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("PENDING_ADMIN_APPROVAL"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false));
    }

    private JsonNode tokenClaims(String token) throws Exception {
        String[] parts = token.split("\\.");
        String payload = parts[1]
                .replace('-', '+')
                .replace('_', '/');
        int padding = (4 - payload.length() % 4) % 4;
        payload = payload + "=".repeat(padding);
        return objectMapper.readTree(java.util.Base64.getDecoder().decode(payload));
    }

    private void registerLegacyPendingEmailUser(String username, String registrationType) {
        Long userId = jdbcTemplate.queryForObject("SELECT SEQ_SYS_USER.NEXTVAL FROM DUAL", Long.class);
        Long applicationId = jdbcTemplate.queryForObject("SELECT SEQ_ROLE_APPLICATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO SYS_USER (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS, CREATED_AT)
                VALUES (
                  ?, ?,
                  '$2a$10$Al2Fi5T2ZEwE2Yi2ds6gp.7qKpiXar4e9.VBDPgU.8XtAfoe7UUDq',
                  'Legacy Pending User', ?, 'Legacy University', 'PENDING_EMAIL_VERIFICATION', CURRENT_TIMESTAMP
                )
                """,
                userId,
                username,
                username + "@example.com"
        );
        jdbcTemplate.update(
                """
                INSERT INTO ROLE_APPLICATION (
                  APPLICATION_ID, USER_ID, REGISTRATION_TYPE, APPLICATION_STATUS, SUBMITTED_PAYLOAD_JSON, SUBMITTED_AT
                ) VALUES (?, ?, ?, 'PENDING_EMAIL_VERIFICATION', '{}', CURRENT_TIMESTAMP)
                """,
                applicationId,
                userId,
                registrationType
        );
    }

    private String userStatus(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT STATUS FROM SYS_USER WHERE USERNAME = ?",
                String.class,
                username
        );
    }

    private String applicationStatus(String username) {
        return jdbcTemplate.queryForObject(
                """
                SELECT RA.APPLICATION_STATUS
                FROM ROLE_APPLICATION RA
                JOIN SYS_USER U ON U.USER_ID = RA.USER_ID
                WHERE U.USERNAME = ?
                """,
                String.class,
                username
        );
    }
}
