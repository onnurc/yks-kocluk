package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StubPaymentController.class, PaymentController.class})
@ActiveProfiles("test")
@Import(StubPaymentEnabledTest.SecurityTestConfig.class)
class StubPaymentEnabledTest {

    @EnableMethodSecurity
    static class SecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SubscriptionService subscriptionService;

    @MockitoBean
    JwtService jwtService;

    // JwtAuthenticationFilter (pulled into the web slice) depends on this at construction time.
    @MockitoBean
    UserRepository userRepository;

    @Test
    @WithMockUser(roles = "STUDENT")
    void whenEnabledAndStudent_succeeds() throws Exception {
        SubscriptionResponse dummyResponse = new SubscriptionResponse(
                1L, 2L, "Coach Name", 3L, "Pkg Name",
                2, SubscriptionStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        when(subscriptionService.succeedPayment(eq(100L), any())).thenReturn(dummyResponse);

        mockMvc.perform(post("/api/v1/payments/100/stub/succeed").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void whenEnabledAndCoach_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/payments/100/stub/succeed").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenEnabledAndAnonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/v1/payments/100/stub/succeed").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
