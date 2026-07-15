package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.RefundRequest;
import com.ykskocluk.demo.dto.RefundResponse;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.SubscriptionService;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link AdminPaymentController} enforcing ADMIN authorization.
 */
@WebMvcTest(AdminPaymentController.class)
@Import(AdminPaymentControllerTest.MethodSecurityTestConfig.class)
class AdminPaymentControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    SubscriptionService subscriptionService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canRequestRefund() throws Exception {
        RefundRequest request = new RefundRequest(new BigDecimal("150.00"), "Reason");
        RefundResponse response = new RefundResponse(1L, 2L, "SUCCESS", new BigDecimal("150.00"), BigDecimal.ZERO, "İade işlemi başarıyla gerçekleştirildi");

        when(subscriptionService.refund(eq(1L), eq(new BigDecimal("150.00")), eq("Reason")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/payments/1/refund")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalPaymentId").value(1))
                .andExpect(jsonPath("$.refundPaymentId").value(2))
                .andExpect(jsonPath("$.refundStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.remainingRefundableAmount").value(0))
                .andExpect(jsonPath("$.message").value("İade işlemi başarıyla gerçekleştirildi"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotRequestRefund_returnsForbidden() throws Exception {
        RefundRequest request = new RefundRequest(new BigDecimal("150.00"), "Reason");

        mockMvc.perform(post("/api/v1/admin/payments/1/refund")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotRequestRefund_returnsForbidden() throws Exception {
        RefundRequest request = new RefundRequest(new BigDecimal("150.00"), "Reason");

        mockMvc.perform(post("/api/v1/admin/payments/1/refund")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListPayments() throws Exception {
        com.ykskocluk.demo.dto.PageResponse<com.ykskocluk.demo.dto.AdminPaymentResponse> response =
                new com.ykskocluk.demo.dto.PageResponse<>(java.util.List.of(), 0, 20, 0L, 0, true);

        when(subscriptionService.listPayments(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/payments")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotListPayments_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotListPayments_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
