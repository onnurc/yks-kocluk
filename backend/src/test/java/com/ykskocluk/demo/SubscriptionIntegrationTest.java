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
 * End-to-end Phase 4a: subscribe to a coach (pending payment), duplicate guard, listing,
 * package listing, and the authorization rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","role":"%s","dateOfBirth":"2005-01-01"}
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

    /** Registers a coach, creates and approves a profile, returns the coach profile id. */
    private int approvedCoachProfileId(String email, String admin) throws Exception {
        String coachToken = register(email, "COACH");
        long universityId = ((Number) JsonPath.read(
                mockMvc.perform(get("/api/v1/universities").header("Authorization", "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        String created = mockMvc.perform(post("/api/v1/coach/profile")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Koç","universityId":%d,"tracks":["NUMERICAL"]}
                                """.formatted(universityId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int profileId = JsonPath.read(created, "$.id");
        mockMvc.perform(post("/api/v1/admin/coaches/" + profileId + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        return profileId;
    }

    private long firstPackageId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/packages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weeklySessions").exists())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    @Test
    void subscribe_flow_andDuplicateGuard() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-sub@example.com", admin);
        String student = register("student-sub@example.com", "STUDENT");
        long packageId = firstPackageId(student);

        String body = "{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId);

        // subscribe -> 201 PENDING_PAYMENT
        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.coachProfileId").value(coachId))
                .andExpect(jsonPath("$.weeklySessions").value(1));

        // duplicate subscription -> 409
        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_SUBSCRIBED"));

        // listing
        mockMvc.perform(get("/api/v1/subscriptions/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coachProfileId").value(coachId))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void authorization() throws Exception {
        String coach = register("coach-authz@example.com", "COACH");
        // COACH cannot subscribe -> 403
        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"coachId\":1,\"packageId\":1}"))
                .andExpect(status().isForbidden());
        // unauthenticated cannot list packages -> 401
        mockMvc.perform(get("/api/v1/packages"))
                .andExpect(status().isUnauthorized());
    }
}
