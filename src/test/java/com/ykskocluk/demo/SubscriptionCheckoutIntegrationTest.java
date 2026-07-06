package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
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
class SubscriptionCheckoutIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

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
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    @Test
    void checkout_createsPendingSubscriptionAndPendingPayment() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-checkout@example.com", admin);
        String studentEmail = "student-checkout@example.com";
        String student = register(studentEmail, "STUDENT");
        long packageId = firstPackageId(student);

        String body = "{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId);
        String response = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.checkoutToken").exists())
                .andExpect(jsonPath("$.checkoutUrl").exists())
                .andReturn().getResponse().getContentAsString();

        long subscriptionId = JsonPath.read(response, "$.subscriptionId");
        long paymentId = JsonPath.read(response, "$.paymentId");

        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(subscriptionRepository.findLiveSubscription(subscription.getStudent().getId(), Long.valueOf(coachId))).isEmpty();
        assertThat(coachProfileRepository.findById((long) coachId).orElseThrow().getActiveStudentCount()).isZero();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void checkout_duplicatePendingIsRejected() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-checkout-dupe@example.com", admin);
        String student = register("student-checkout-dupe@example.com", "STUDENT");
        long packageId = firstPackageId(student);
        String body = "{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId);

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_SUBSCRIBED"));
    }

    @Test
    void checkout_nonStudent_forbidden() throws Exception {
        String coach = register("coach-checkout-authz@example.com", "COACH");

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":1,\"packageId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void succeedPayment_e2e_flow_success_andIdempotency() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-succeed@example.com", admin);
        String student = register("student-succeed@example.com", "STUDENT");
        long packageId = firstPackageId(student);

        String body = "{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId);
        String checkoutRes = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long subscriptionId = JsonPath.read(checkoutRes, "$.subscriptionId");
        long paymentId = JsonPath.read(checkoutRes, "$.paymentId");

        // Verify initial state
        Subscription initialSub = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(initialSub.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(coachProfileRepository.findById((long) coachId).orElseThrow().getActiveStudentCount()).isZero();

        // Perform succeed payment
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/stub/succeed")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify updated state
        Subscription activeSub = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(activeSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(activeSub.getStartAt()).isNotNull();
        assertThat(activeSub.getEndAt()).isNotNull();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getProviderReference()).isNotNull();
        assertThat(coachProfileRepository.findById((long) coachId).orElseThrow().getActiveStudentCount()).isEqualTo(1);

        // Perform succeed payment again (idempotency)
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/stub/succeed")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Capacity should not be double incremented
        assertThat(coachProfileRepository.findById((long) coachId).orElseThrow().getActiveStudentCount()).isEqualTo(1);
    }

    @Test
    void succeedPayment_unauthorizedStudent_forbidden() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-succeed-authz@example.com", admin);
        String studentA = register("student-succeed-a@example.com", "STUDENT");
        String studentB = register("student-succeed-b@example.com", "STUDENT");
        long packageId = firstPackageId(studentA);

        String body = "{\"coachId\":%d,\"packageId\":%d}".formatted(coachId, packageId);
        String checkoutRes = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + studentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long paymentId = JsonPath.read(checkoutRes, "$.paymentId");

        // Try to succeed using studentB's token
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/stub/succeed")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_PAYMENT_OWNER"));
    }
}