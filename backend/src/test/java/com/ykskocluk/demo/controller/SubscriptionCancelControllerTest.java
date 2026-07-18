package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.CancelResult;
import com.ykskocluk.demo.service.SubscriptionBillingService;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice for the cancel endpoint (Phase 8d). {@code @PreAuthorize("hasRole('STUDENT')")} is genuinely
 * enabled, so the COACH 403 is enforced by method security. Proves: the confirmation email fires only
 * when {@code newlyCancelled}, and a mail failure still returns 200 (best-effort, cancel already committed).
 */
@WebMvcTest(SubscriptionController.class)
@Import(SubscriptionCancelControllerTest.MethodSecurityTestConfig.class)
class SubscriptionCancelControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    private static final long SUB_ID = 5L;
    private static final String STUDENT_EMAIL = "stu@example.com";
    private static final Instant END_AT = Instant.parse("2026-07-20T00:00:00Z");

    @Autowired MockMvc mockMvc;

    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean SubscriptionBillingService billingService;
    @MockitoBean MailClient mailClient;
    @MockitoBean JwtService jwtService; // JwtAuthenticationFilter dep; auth comes from @WithMockUser
    @MockitoBean UserRepository userRepository; // JwtAuthenticationFilter dep (construction-time)

    private SubscriptionResponse cancelledResponse() {
        return new SubscriptionResponse(SUB_ID, 1L, "Coach Name", 2L, "Aylık 1x", 1,
                SubscriptionStatus.ACTIVE, false, Instant.parse("2026-06-20T00:00:00Z"), END_AT);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cancel_returns200_andSendsConfirmationWhenNewlyCancelled() throws Exception {
        when(billingService.cancel(eq(SUB_ID), any()))
                .thenReturn(new CancelResult(cancelledResponse(), true, STUDENT_EMAIL));

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUB_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoRenew").value(false));

        verify(mailClient).sendCancellationConfirmed(eq(STUDENT_EMAIL), eq("Coach Name"), eq(END_AT));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void reCancel_returns200_butSendsNoEmail() throws Exception {
        when(billingService.cancel(eq(SUB_ID), any()))
                .thenReturn(new CancelResult(cancelledResponse(), false, STUDENT_EMAIL)); // already cancelled

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUB_ID).with(csrf()))
                .andExpect(status().isOk());

        verify(mailClient, never()).sendCancellationConfirmed(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_forbidden_andServiceNeverCalled() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUB_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(billingService);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void mailThrows_endpointStillReturns200() throws Exception {
        when(billingService.cancel(eq(SUB_ID), any()))
                .thenReturn(new CancelResult(cancelledResponse(), true, STUDENT_EMAIL));
        doThrow(new RuntimeException("resend down"))
                .when(mailClient).sendCancellationConfirmed(any(), any(), any());

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUB_ID).with(csrf()))
                .andExpect(status().isOk()); // best-effort: cancel committed, mail failure swallowed
    }
}
