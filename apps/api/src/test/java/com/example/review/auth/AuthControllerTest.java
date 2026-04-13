package com.example.review.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "author_demo",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isUnauthorized());
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
}
