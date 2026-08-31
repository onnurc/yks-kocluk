package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow against the real stack (security filters + JWT + Flyway +
 * Testcontainers PostgreSQL). Covers the mandatory auth/security critical path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"SecurePassphrase42!","fullName":"Test User","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                """.formatted(email);
    }

    @Test
    void fullLifecycle_register_me_refresh_rotation_logout() throws Exception {
        // Public registration is enumeration-safe and never returns authentication material.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("flow@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        TestUsers.verifyEmail(userRepository, "flow@example.com");

        var registerResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@example.com\",\"password\":\"SecurePassphrase42!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("flow@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();
        String registerJson = registerResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(registerJson, "$.accessToken");
        MockCookie refreshCookie = MockCookie.parse(
                registerResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        // /me with bearer -> 200
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow@example.com"));

        // refresh -> 200 with new tokens
        var refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String refreshJson = refreshResult.getResponse().getContentAsString();
        MockCookie newRefreshCookie = MockCookie.parse(
                refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        String newAccess = JsonPath.read(refreshJson, "$.accessToken");

        // old refresh token is now revoked (rotation) -> 401
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // logout (authenticated) with the new refresh -> 204, then it cannot be refreshed
        mockMvc.perform(post("/api/v1/auth/logout").cookie(newRefreshCookie)
                        .header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(newRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_returns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void register_duplicateEmail_returnsSameAcceptedPublicResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("dup@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("dup@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_wrongPassword_returns401Problem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("login@example.com")))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void register_invalidPayload_returns400ValidationProblem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\",\"dateOfBirth\":\"2005-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
