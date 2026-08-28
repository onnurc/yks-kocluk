package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CoachDashboardTrialIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void coachSelfAuthorizationAndTrialLifecycle() throws Exception {
        String admin = login("admin@yks.local", "admin1234");
        Object[] firstCoach = approvedCoach("dashboard-trial-coach@example.com", admin);
        String coach = (String) firstCoach[0];
        long coachId = (long) firstCoach[1];
        Object[] secondCoach = approvedCoach("dashboard-trial-other@example.com", admin);
        String otherCoach = (String) secondCoach[0];
        String student = register("dashboard-trial-student@example.com", "STUDENT");
        String secondStudent = register("dashboard-trial-student-2@example.com", "STUDENT");

        mockMvc.perform(get("/api/v1/coach/dashboard/summary").header("Authorization", "Bearer " + coach))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeStudentCount").value(0))
                .andExpect(jsonPath("$.unreadMessageCount").value(0));
        mockMvc.perform(get("/api/v1/coach/dashboard/summary").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/coach/dashboard/summary").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());

        long firstSlot = createSlot(coach, Instant.now().plus(4, ChronoUnit.DAYS));
        long secondSlot = createSlot(coach, Instant.now().plus(5, ChronoUnit.DAYS));
        long subscriptionCount = subscriptions.count();
        long paymentCount = payments.count();
        String created = mockMvc.perform(post("/api/v1/trial-consultations")
                        .header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityId\":%d}".formatted(firstSlot)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        long trialId = ((Number) JsonPath.read(created, "$.id")).longValue();

        org.junit.jupiter.api.Assertions.assertEquals(subscriptionCount, subscriptions.count());
        org.junit.jupiter.api.Assertions.assertEquals(paymentCount, payments.count());
        mockMvc.perform(post("/api/v1/trial-consultations")
                        .header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityId\":%d}".formatted(secondSlot)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("TRIAL_ALREADY_EXISTS"));
        mockMvc.perform(get("/api/v1/coach/trial-consultations").header("Authorization", "Bearer " + coach))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(trialId));
        mockMvc.perform(post("/api/v1/coach/trial-consultations/" + trialId + "/confirm")
                        .header("Authorization", "Bearer " + otherCoach))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/trial-consultations/" + trialId + "/cancel")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/trial-consultations")
                        .header("Authorization", "Bearer " + secondStudent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityId\":%d}".formatted(firstSlot)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/coaches/" + coachId + "/trial-availability")
                        .header("Authorization", "Bearer " + secondStudent))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %d)]".formatted(firstSlot)).isEmpty());
    }

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"TestPassword123!","fullName":"Test User","role":"%s","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email, role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        TestUsers.verifyEmail(userRepository, email);
        return JsonPath.read(json, "$.accessToken");
    }

    private String login(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private Object[] approvedCoach(String email, String admin) throws Exception {
        // Coaches no longer self-register (see AuthService.register) — build the fixture
        // directly and log it in for a real token.
        TestUsers.createWithPassword(userRepository, passwordEncoder, Role.COACH, email, "TestPassword123!");
        String token = login(email, "TestPassword123!");
        Number university = JsonPath.read(mockMvc.perform(get("/api/v1/universities")
                        .header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsString(), "$[0].id");
        String profile = mockMvc.perform(post("/api/v1/coach/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Coach\",\"universityId\":%d,\"tracks\":[\"NUMERICAL\"]}".formatted(university.longValue())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(profile, "$.id");
        mockMvc.perform(post("/api/v1/admin/coaches/" + id.longValue() + "/approve")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        return new Object[]{token, id.longValue()};
    }

    private long createSlot(String coach, Instant start) throws Exception {
        String json = mockMvc.perform(post("/api/v1/coach/availability").header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
