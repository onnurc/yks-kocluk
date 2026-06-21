package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 4c: a subscribed student books an open slot (201 PLANNED, is_booked
 * flips so the slot leaves the open view), the no-subscription gate, listings, and authz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SessionIntegrationTest {

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

    /** Registers a coach, creates+approves a profile; returns [coachToken, profileId]. */
    private Object[] approvedCoach(String email) throws Exception {
        String admin = adminToken();
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
        return new Object[]{coachToken, profileId};
    }

    private int createSlot(String coachToken) throws Exception {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String body = "{\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(start, start.plus(1, ChronoUnit.HOURS));
        String json = mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private long firstPackageId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/packages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    private void subscribe(String studentToken, int coachId, long packageId) throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId)))
                .andExpect(status().isCreated());
    }

    @Test
    void book_flow_listings_andSlotLeavesOpenView() throws Exception {
        Object[] c = approvedCoach("coach-sess@example.com");
        String coach = (String) c[0];
        int coachId = (int) c[1];
        int slotId = createSlot(coach);

        String student = register("student-sess@example.com", "STUDENT");
        subscribe(student, coachId, firstPackageId(student));

        // book -> 201 PLANNED
        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityId\":%d}".formatted(slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.coachProfileId").value(coachId))
                .andExpect(jsonPath("$.availabilityId").value(slotId));

        // slot now leaves the student-facing open-slot view (is_booked flipped)
        mockMvc.perform(get("/api/v1/coaches/" + coachId + "/availability")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // student lists own sessions — meetLink was populated by the AFTER_COMMIT listener (stub Meet client)
        mockMvc.perform(get("/api/v1/sessions/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availabilityId").value(slotId))
                .andExpect(jsonPath("$[0].meetLink").value(Matchers.containsString("meet.stub.local")));

        // coach lists sessions booked against them
        mockMvc.perform(get("/api/v1/coach/sessions").header("Authorization", "Bearer " + coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coachProfileId").value(coachId));
    }

    @Test
    void book_withoutSubscription_isRejected() throws Exception {
        Object[] c = approvedCoach("coach-sess-nosub@example.com");
        String coach = (String) c[0];
        int slotId = createSlot(coach);

        // student has NO subscription with this coach -> 409
        String student = register("student-nosub@example.com", "STUDENT");
        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityId\":%d}".formatted(slotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_SUBSCRIPTION"));
    }

    @Test
    void authorization_coachCannotBook() throws Exception {
        String coach = register("coach-sess-authz@example.com", "COACH");
        // COACH cannot book sessions -> 403
        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availabilityId\":1}"))
                .andExpect(status().isForbidden());

        // unauthenticated cannot list -> 401
        mockMvc.perform(get("/api/v1/sessions/me"))
                .andExpect(status().isUnauthorized());
    }
}
