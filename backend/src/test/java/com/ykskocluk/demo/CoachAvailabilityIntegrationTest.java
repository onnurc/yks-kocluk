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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 4b: a coach manages own slots (create / list / delete-unbooked),
 * validation (past, end<=start, duplicate start), the student open-slots view, and authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CoachAvailabilityIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","role":"%s","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email, email.split("@")[0], role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String adminToken() throws Exception {
        return loginToken("admin@yks.local", "admin1234");
    }

    private String loginToken(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    /** Registers a coach, creates+approves a profile; returns [coachToken, profileId]. */
    private Object[] approvedCoach(String email) throws Exception {
        String admin = adminToken();
        // Coaches no longer self-register (see AuthService.register) — build the fixture
        // directly and log it in for a real token.
        TestUsers.createWithPassword(userRepository, passwordEncoder, Role.COACH, email, "password123");
        String coachToken = loginToken(email, "password123");
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

    private String slotBody(Instant start, Instant end) {
        return "{\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(start, end);
    }

    @Test
    void coach_create_list_delete_andStudentView() throws Exception {
        Object[] c = approvedCoach("coach-avail@example.com");
        String coach = (String) c[0];
        int profileId = (int) c[1];

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        // create -> 201 unbooked
        String created = mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody(start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booked").value(false))
                .andExpect(jsonPath("$.coachProfileId").value(profileId))
                .andReturn().getResponse().getContentAsString();
        int slotId = JsonPath.read(created, "$.id");

        // Same start time -> any two ranges sharing a start point overlap, so the application-level
        // existsOverlapping check (CoachAvailabilityService.createOwn) catches this deterministically
        // before the insert -> SLOT_OVERLAP. SLOT_DUPLICATE is the DB UNIQUE(coach_profile_id,
        // start_time) constraint's fallback for a genuine concurrent-insert race, which a
        // sequential request pair like this one can't trigger.
        mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody(start, end.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_OVERLAP"));

        // coach lists own slots
        mockMvc.perform(get("/api/v1/coach/availability").header("Authorization", "Bearer " + coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(slotId));

        // student sees the open future slot
        String student = register("student-avail@example.com", "STUDENT");
        mockMvc.perform(get("/api/v1/coaches/" + profileId + "/availability")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(slotId))
                .andExpect(jsonPath("$[0].booked").value(false));

        // coach deletes unbooked slot -> 204
        mockMvc.perform(delete("/api/v1/coach/availability/" + slotId)
                        .header("Authorization", "Bearer " + coach))
                .andExpect(status().isNoContent());

        // now student sees no open slots
        mockMvc.perform(get("/api/v1/coaches/" + profileId + "/availability")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validation_pastAndBadRange() throws Exception {
        String coach = (String) approvedCoach("coach-avail-val@example.com")[0];

        // past slot -> 400 SLOT_IN_PAST
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody(past, past.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SLOT_IN_PAST"));

        // end <= start -> 400 INVALID_SLOT_RANGE
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody(start, start)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SLOT_RANGE"));

        // missing startTime -> 400 VALIDATION_ERROR (bean validation)
        mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endTime\":\"%s\"}".formatted(start)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorization_studentCannotManageSlots() throws Exception {
        String student = register("student-avail-authz@example.com", "STUDENT");
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);

        // STUDENT cannot create coach slots -> 403
        mockMvc.perform(post("/api/v1/coach/availability")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());

        // unauthenticated cannot view coach open slots -> 401
        mockMvc.perform(get("/api/v1/coaches/1/availability"))
                .andExpect(status().isUnauthorized());
    }
}
