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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 3 discovery: only APPROVED coaches are searchable, track filtering,
 * placeholder derived fields, detail visibility, and the authorization rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class Phase3IntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    /** Coaches no longer self-register (see AuthService.register) — build the fixture directly and log it in. */
    private String registerCoach(String email) throws Exception {
        TestUsers.createWithPassword(userRepository, passwordEncoder, Role.COACH, email, "TestPassword123!");
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"TestPassword123!","fullName":"%s","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email, email.split("@")[0])))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String adminToken() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@yks.local\",\"password\":\"admin1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private long firstUniversityId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/universities").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    /** Registers a coach, creates a profile with the given tracks, returns the profile id. */
    private int createCoachProfile(String email, long universityId, String... tracks) throws Exception {
        String token = registerCoach(email);
        String json = mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Koç %s","universityId":%d,"tracks":["%s"]}
                                """.formatted(email, universityId, String.join("\",\"", tracks))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void discovery_onlyApprovedVisible_withTrackFilterAndAuthz() throws Exception {
        String admin = adminToken();
        long universityId = firstUniversityId(admin);

        int approvedNumeric = createCoachProfile("num@example.com", universityId, "NUMERICAL");
        int approvedVerbal = createCoachProfile("verb@example.com", universityId, "VERBAL");
        int approvedMultiTrack = createCoachProfile("multi@example.com", universityId, "NUMERICAL", "VERBAL");
        int pending = createCoachProfile("pend@example.com", universityId, "NUMERICAL");

        // approve three, leave one PENDING
        mockMvc.perform(post("/api/v1/admin/coaches/" + approvedNumeric + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/coaches/" + approvedVerbal + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/coaches/" + approvedMultiTrack + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        String student = register("student@example.com");

        // shared CI fixtures may add other approved coaches; these approved fixtures must be present and PENDING absent
        mockMvc.perform(get("/api/v1/coaches?size=100").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItems(approvedNumeric, approvedVerbal, approvedMultiTrack)))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(pending))));

        // each track includes its single-track coach plus the multi-track coach, without cross-track leakage
        mockMvc.perform(get("/api/v1/coaches?track=NUMERICAL&size=100").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItems(approvedNumeric, approvedMultiTrack)))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(approvedVerbal))))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(pending))));
        mockMvc.perform(get("/api/v1/coaches?track=VERBAL&size=100").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItems(approvedVerbal, approvedMultiTrack)))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(approvedNumeric))))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(pending))));

        // detail: APPROVED coach visible, PENDING coach hidden (404)
        mockMvc.perform(get("/api/v1/coaches/" + approvedNumeric).header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvedNumeric));
        mockMvc.perform(get("/api/v1/coaches/" + pending).header("Authorization", "Bearer " + student))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COACH_NOT_FOUND"));

        // authorization: COACH cannot search (403), unauthenticated cannot (401)
        String coach = registerCoach("browsing-coach@example.com");
        mockMvc.perform(get("/api/v1/coaches").header("Authorization", "Bearer " + coach))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/coaches"))
                .andExpect(status().isUnauthorized());
    }
}
