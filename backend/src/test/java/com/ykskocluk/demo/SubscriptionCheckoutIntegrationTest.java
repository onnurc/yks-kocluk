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
import com.ykskocluk.demo.repository.LegalAcceptanceRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// payments.stub.success-enabled is off by default (it's a prod-closed backdoor) and
// application-test.yml deliberately doesn't flip it globally, so it's scoped to this class only —
// the same pattern IyzicoWebhookSignatureTest uses for payments.iyzico.enabled.
@SpringBootTest(properties = "payments.stub.success-enabled=true")
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
    @Autowired LegalAcceptanceRepository legalAcceptanceRepository;
    @Autowired PasswordEncoder passwordEncoder;

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

    private String register(String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"TestPassword123!","fullName":"%s","role":"%s","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email, email.split("@")[0], role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TestUsers.verifyEmail(userRepository, email);
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
        String coachToken = registerCoach(email);
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

        String body = checkoutBody(coachId, packageId);
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

        long subscriptionId = ((Number) JsonPath.read(response, "$.subscriptionId")).longValue();
        long paymentId = ((Number) JsonPath.read(response, "$.paymentId")).longValue();

        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(subscriptionRepository.findLiveSubscription(subscription.getStudent().getId(), Long.valueOf(coachId))).isEmpty();
        assertThat(coachProfileRepository.findById((long) coachId).orElseThrow().getActiveStudentCount()).isZero();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        var acceptances = legalAcceptanceRepository.findBySubscriptionIdOrderByIdAsc(subscriptionId);
        assertThat(acceptances).hasSize(3);
        assertThat(acceptances).allSatisfy(acceptance -> {
            assertThat(acceptance.getPayment().getId()).isEqualTo(paymentId);
            assertThat(acceptance.getDocumentVersion()).isEqualTo("1.0");
            assertThat(acceptance.getDocumentContentHash()).hasSize(64);
            assertThat(acceptance.getSource()).isEqualTo("SUBSCRIPTION_CHECKOUT");
        });
    }

    @Test
    void checkout_duplicatePendingIsRejected() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-checkout-dupe@example.com", admin);
        String student = register("student-checkout-dupe@example.com", "STUDENT");
        long packageId = firstPackageId(student);
        String body = checkoutBody(coachId, packageId);

        String firstResponse = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long subscriptionId = ((Number) JsonPath.read(firstResponse, "$.subscriptionId")).longValue();

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_SUBSCRIBED"));
        assertThat(legalAcceptanceRepository.findBySubscriptionIdOrderByIdAsc(subscriptionId)).hasSize(3);
    }

    @Test
    void checkout_nonStudent_forbidden() throws Exception {
        String coach = registerCoach("coach-checkout-authz@example.com");

        // Full body (checkoutBody helper): a partial body no longer reaches @PreAuthorize at all —
        // Jackson 3 (Spring Boot 4.0.6) rejects a record with missing properties during argument
        // binding, which runs before the security-proxied method call, so a minimal body would 400
        // regardless of role. This test's job is authorization, not payload completeness.
        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(1, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void succeedPayment_e2e_flow_success_andIdempotency() throws Exception {
        String admin = adminToken();
        int coachId = approvedCoachProfileId("coach-succeed@example.com", admin);
        String student = register("student-succeed@example.com", "STUDENT");
        long packageId = firstPackageId(student);

        String body = checkoutBody(coachId, packageId);
        String checkoutRes = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long subscriptionId = ((Number) JsonPath.read(checkoutRes, "$.subscriptionId")).longValue();
        long paymentId = ((Number) JsonPath.read(checkoutRes, "$.paymentId")).longValue();

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

        String body = checkoutBody(coachId, packageId);
        String checkoutRes = mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .header("Authorization", "Bearer " + studentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long paymentId = ((Number) JsonPath.read(checkoutRes, "$.paymentId")).longValue();

        // Try to succeed using studentB's token
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/stub/succeed")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_PAYMENT_OWNER"));
    }

    private String checkoutBody(long coachId, long packageId) {
        return ("{\"coachId\":%d,\"packageId\":%d,\"preInformationDocumentId\":6,"
                + "\"distanceSalesDocumentId\":7,\"refundCancellationPolicyDocumentId\":8,"
                + "\"legalDocumentsAccepted\":true}").formatted(coachId, packageId);
    }

    // webhook_invalidStatus_returnsBadRequest and webhook_unknownPaymentId_returnsNotFound were
    // removed: they posted to the webhook with no signature, which now correctly gets rejected
    // with 401 before reaching this validation (see SubscriptionService.verifyWebhookSignature).
    // Equivalent signed coverage already exists in IyzicoWebhookSignatureTest
    // (processWebhook_unsupportedStatus_badRequest / processWebhook_wrongPaymentId_notFound).
    //
    // webhook_succeeds_andIsIdempotent and webhook_fails_andIsIdempotent were also removed for the
    // same reason (unsigned webhook calls, now 401). They were NOT moved here as-is: this class
    // can't set payments.iyzico.enabled=true, because that property switches the IyzicoClient bean
    // from StubIyzicoClient to RealIyzicoClient (see IyzicoClient impls' @ConditionalOnProperty),
    // and every checkout test in this same Spring context calls the real
    // /api/v1/subscriptions/checkout endpoint, which synchronously calls
    // IyzicoClient.initializeCheckout — RealIyzicoClient would then fail context startup
    // (IllegalStateException: no baseUrl configured) and take down every test in this class.
    // The SUCCESS+idempotency half was already covered by
    // IyzicoWebhookSignatureTest#processWebhook_validSignature_success /
    // #processWebhook_duplicateWebhook_idempotent. The FAILURE+idempotency half was genuinely
    // missing coverage and was added there instead, using its existing signed-request fixture.
}
