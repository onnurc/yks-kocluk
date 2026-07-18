package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionCancelRenewalIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

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

    private Subscription seedActiveSubscription(String studentEmail, int coachProfileId) {
        User student = userRepository.findByEmail(studentEmail).orElseThrow();
        CoachProfile coach = coachProfileRepository.findById((long) coachProfileId).orElseThrow();
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);

        Subscription subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(pkg);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscription.setEndAt(Instant.now().plus(29, ChronoUnit.DAYS));
        return subscriptionRepository.saveAndFlush(subscription);
    }

    @Test
    void cancelRenewal_ownActiveSubscription_disablesAutoRenewAndKeepsAccess() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-cancel@example.com", admin);
        String studentEmail = "student-cancel@example.com";
        String student = register(studentEmail, "STUDENT");
        Subscription saved = seedActiveSubscription(studentEmail, coachId);

        mockMvc.perform(post("/api/v1/subscriptions/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.coachProfileId").value(coachId));

        Subscription reloaded = subscriptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isAutoRenew()).isFalse();
        assertThat(reloaded.getCancelledAt()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void cancelRenewal_otherStudent_forbidden() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-cancel-other@example.com", admin);
        String ownerEmail = "student-owner@example.com";
        String owner = register(ownerEmail, "STUDENT");
        Subscription saved = seedActiveSubscription(ownerEmail, coachId);
        String otherStudent = register("student-other@example.com", "STUDENT");

        mockMvc.perform(post("/api/v1/subscriptions/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + otherStudent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_SUBSCRIPTION_OWNER"));
    }

    @Test
    void cancelRenewal_nonStudent_forbiddenBySecurity() throws Exception {
        String coach = register("coach-cancel-authz@example.com", "COACH");

        mockMvc.perform(post("/api/v1/subscriptions/1/cancel")
                        .header("Authorization", "Bearer " + coach))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelRenewal_pendingPayment_isRejected() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-cancel-pending@example.com", admin);
        String student = register("student-cancel-pending@example.com", "STUDENT");
        long packageId = ((Number) JsonPath.read(
                mockMvc.perform(get("/api/v1/packages").header("Authorization", "Bearer " + student))
                        .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        String created = mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int subscriptionId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/subscriptions/" + subscriptionId + "/cancel")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SUBSCRIPTION_NOT_CANCELLABLE"));
    }
}