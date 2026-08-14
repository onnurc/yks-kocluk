package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "payments.iyzico.enabled=true",
        "payments.iyzico.api-key=test-api-key",
        "payments.iyzico.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class IyzicoWebhookSignatureTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PackageRepository packageRepository;

    private User student;
    private CoachProfile coach;
    private Subscription subscription;
    private Payment payment;

    private static final String SECRET_KEY = "test-secret-key";

    @BeforeEach
    void setUp() {
        // Create student
        student = new User();
        student.setEmail("student-webhook-" + UUID.randomUUID() + "@example.com");
        student.setFullName("Student Webhook");
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        userRepository.save(student);

        // Create university
        University uni = new University();
        uni.setName("Webhook Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        // Create coach
        coach = new CoachProfile();
        User coachUser = new User();
        coachUser.setEmail("coach-webhook-" + UUID.randomUUID() + "@example.com");
        coachUser.setFullName("Coach Webhook");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        // Get package
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);

        // Create pending subscription
        subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(pkg);
        subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        subscription.setStartAt(Instant.now());
        subscription.setEndAt(Instant.now().plusSeconds(3600));
        subscriptionRepository.save(subscription);

        // Create pending payment
        payment = new Payment();
        payment.setSubscription(subscription);
        payment.setType(PaymentType.CHARGE);
        payment.setAmount(pkg.getPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("idem-webhook-" + UUID.randomUUID());
        payment.setCommissionRate(BigDecimal.valueOf(0.2));
        payment.setCommissionAmount(BigDecimal.ZERO);
        payment.setCoachPayoutAmount(BigDecimal.ZERO);
        paymentRepository.save(payment);
    }

    private String calculateSignature(String iyziEventType, Long paymentId, String paymentConversationId, String status) {
        try {
            String data = SECRET_KEY + iyziEventType + paymentId + paymentConversationId + status;
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKeySpec);
            byte[] hashBytes = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void processWebhook_missingSignature_unauthorized() throws Exception {
        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), "SUCCESS", "ref-1", "PAYMENT_API", "conv-1");

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));

        // Verify state unchanged
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
    }

    @Test
    void processWebhook_invalidSignature_unauthorized() throws Exception {
        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), "SUCCESS", "ref-1", "PAYMENT_API", "conv-1");

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", "wrongsignaturevalue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));

        // Verify state unchanged
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
    }

    @Test
    void processWebhook_validSignature_success() throws Exception {
        String eventType = "PAYMENT_API";
        String convId = "conv-1";
        String status = "SUCCESS";
        String signature = calculateSignature(eventType, payment.getId(), convId, status);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), status, "ref-real", eventType, convId);

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify state updated
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount()).isEqualTo(1);
    }

    @Test
    void processWebhook_unsupportedStatus_badRequest() throws Exception {
        String eventType = "PAYMENT_API";
        String convId = "conv-1";
        String status = "PENDING"; // Only SUCCESS and FAILURE are supported
        String signature = calculateSignature(eventType, payment.getId(), convId, status);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), status, "ref-real", eventType, convId);

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_WEBHOOK_STATUS"));
    }

    @Test
    void processWebhook_wrongPaymentId_notFound() throws Exception {
        Long wrongPaymentId = 999999L;
        String eventType = "PAYMENT_API";
        String convId = "conv-1";
        String status = "SUCCESS";
        String signature = calculateSignature(eventType, wrongPaymentId, convId, status);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(wrongPaymentId, status, "ref-real", eventType, convId);

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void processWebhook_validSignatureFailureStatus_processedAndIdempotent() throws Exception {
        String eventType = "PAYMENT_API";
        String convId = "conv-1";
        String status = "FAILURE";
        String signature = calculateSignature(eventType, payment.getId(), convId, status);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), status, null, eventType, convId);

        // First execution
        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify state updated: payment failed, subscription stays pending, capacity untouched
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount()).isZero();

        // Duplicate execution
        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEMPOTENT"));

        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount()).isZero();
    }

    @Test
    void processWebhook_duplicateWebhook_idempotent() throws Exception {
        String eventType = "PAYMENT_API";
        String convId = "conv-1";
        String status = "SUCCESS";
        String signature = calculateSignature(eventType, payment.getId(), convId, status);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(payment.getId(), status, "ref-real", eventType, convId);

        // First execution
        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Second execution
        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEMPOTENT"));

        // Verify activeStudentCount did not increment twice
        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount()).isEqualTo(1);
    }
}
