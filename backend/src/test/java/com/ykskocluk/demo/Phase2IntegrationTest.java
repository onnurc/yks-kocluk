package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    /** Coaches no longer self-register (see AuthService.register) — build the fixture directly and log it in. */
    private String registerCoach(String email) throws Exception {
        TestUsers.createWithPassword(userRepository, passwordEncoder, Role.COACH, email, "TestPassword123!");
        return loginToken(email, "TestPassword123!");
    }

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"TestPassword123!","fullName":"Test User","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        TestUsers.verifyEmail(userRepository, email);
        return loginToken(email, "TestPassword123!");
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
        String coachToken = registerCoach("coach1@example.com");
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
        String studentToken = register("student1@example.com");
        String coachToken = registerCoach("coach2@example.com");

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
        String coachToken = registerCoach("coach3@example.com");
        mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"\",\"universityId\":null,\"tracks\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void coachBiography_isOwnerScopedPersistedValidatedAndPubliclyVisible() throws Exception {
        String ownerToken = registerCoach("biography-owner@example.com");
        String otherCoachToken = registerCoach("biography-other@example.com");
        long universityId = firstUniversityId(ownerToken);

        String ownerProfile = mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(coachProfileBody(universityId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ownerProfileId = ((Number) JsonPath.read(ownerProfile, "$.id")).longValue();

        mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + otherCoachToken)
                        .contentType(MediaType.APPLICATION_JSON).content(coachProfileBody(universityId)))
                .andExpect(status().isCreated());

        String adminToken = loginToken("admin@yks.local", "admin1234");
        mockMvc.perform(post("/api/v1/admin/coaches/" + ownerProfileId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String savedBiography = "Öğrencilerle hedef odaklı ve sürdürülebilir çalışma sistemleri kurarım.";
        mockMvc.perform(put("/api/v1/coach/profile/me/education")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university":"Boğaziçi Üniversitesi","department":"Bilgisayar",
                                 "yksRanking":1420,"bio":"%s"}
                                """.formatted(savedBiography)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(savedBiography))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/coach/profile/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(savedBiography));
        mockMvc.perform(get("/api/v1/public/coaches/" + ownerProfileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(savedBiography));

        mockMvc.perform(put("/api/v1/coach/profile/me/education")
                        .header("Authorization", "Bearer " + otherCoachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university":"Boğaziçi Üniversitesi","department":null,
                                 "yksRanking":null,"bio":"Başka koçun biyografisi"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/coaches/" + ownerProfileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(savedBiography));

        mockMvc.perform(put("/api/v1/coach/profile/me/education")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university":"Boğaziçi Üniversitesi","department":null,
                                 "yksRanking":null,"bio":"%s"}
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
