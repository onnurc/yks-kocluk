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

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","role":"%s"}
                                """.formatted(email, email.split("@")[0], role)))
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

    /** Registers a coach, creates a profile with the given track, returns the profile id. */
    private int createCoachProfile(String email, long universityId, String track) throws Exception {
        String token = register(email, "COACH");
        String json = mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Koç %s","universityId":%d,"tracks":["%s"]}
                                """.formatted(email, universityId, track)))
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
        int pending = createCoachProfile("pend@example.com", universityId, "NUMERICAL");

        // approve two, leave one PENDING
        mockMvc.perform(post("/api/v1/admin/coaches/" + approvedNumeric + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/coaches/" + approvedVerbal + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        String student = register("student@example.com", "STUDENT");

        // search returns only the two APPROVED coaches, with placeholder derived fields
        mockMvc.perform(get("/api/v1/coaches").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].rating").doesNotExist())
                .andExpect(jsonPath("$.content[0].totalSessions").value(0));

        // track filter narrows to the NUMERICAL coach only
        mockMvc.perform(get("/api/v1/coaches?track=NUMERICAL").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approvedNumeric))
                .andExpect(jsonPath("$.content[0].tracks[0]").value("NUMERICAL"));

        // detail: APPROVED coach visible, PENDING coach hidden (404)
        mockMvc.perform(get("/api/v1/coaches/" + approvedNumeric).header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvedNumeric));
        mockMvc.perform(get("/api/v1/coaches/" + pending).header("Authorization", "Bearer " + student))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COACH_NOT_FOUND"));

        // authorization: COACH cannot search (403), unauthenticated cannot (401)
        String coach = register("browsing-coach@example.com", "COACH");
        mockMvc.perform(get("/api/v1/coaches").header("Authorization", "Bearer " + coach))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/coaches"))
                .andExpect(status().isUnauthorized());
    }
}
