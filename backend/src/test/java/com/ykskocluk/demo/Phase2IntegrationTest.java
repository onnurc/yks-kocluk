package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 2: coach profile creation → admin approval, the authorization matrix,
 * and (by logging in as the V3-seeded admin) validation of the admin bootstrap seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class Phase2IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Test User","role":"%s","dateOfBirth":"2005-01-01"}
                                """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String loginToken(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private long firstUniversityId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/universities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    private String coachProfileBody(long universityId) {
        return """
                {"headline":"Deneyimli koç","bio":"YKS deneyimi","universityId":%d,
                 "department":"Bilgisayar","graduationYear":2020,"tracks":["NUMERICAL","EQUAL_WEIGHT"]}
                """.formatted(universityId);
    }

    @Test
    void coachApprovalFlow_withSeededAdmin() throws Exception {
        String coachToken = register("coach1@example.com", "COACH");
        long universityId = firstUniversityId(coachToken);

        // coach creates profile -> PENDING
        String created = mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON).content(coachProfileBody(universityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.maxStudentCapacity").value(10))
                .andExpect(jsonPath("$.tracks").isArray())
                .andReturn().getResponse().getContentAsString();
        int profileId = JsonPath.read(created, "$.id");

        // login as the V3-seeded admin (validates the bootstrap seed + BCrypt hash)
        String adminToken = loginToken("admin@yks.local", "admin1234");

        // admin sees it in the PENDING list
        mockMvc.perform(get("/api/v1/admin/coaches?status=PENDING").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + profileId + ")]").exists())
                .andExpect(jsonPath("$.size").value(20));

        // admin approves
        mockMvc.perform(post("/api/v1/admin/coaches/" + profileId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // coach now sees APPROVED
        mockMvc.perform(get("/api/v1/coach/profile/me").header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void authorizationMatrix() throws Exception {
        String studentToken = register("student1@example.com", "STUDENT");
        String coachToken = register("coach2@example.com", "COACH");

        // student cannot create a coach profile -> 403
        mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(coachProfileBody(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // coach cannot use admin endpoints -> 403
        mockMvc.perform(get("/api/v1/admin/coaches").header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isForbidden());

        // unauthenticated cannot list universities -> 401
        mockMvc.perform(get("/api/v1/universities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void coachProfile_invalidPayload_returns400() throws Exception {
        String coachToken = register("coach3@example.com", "COACH");
        mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"\",\"universityId\":null,\"tracks\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
