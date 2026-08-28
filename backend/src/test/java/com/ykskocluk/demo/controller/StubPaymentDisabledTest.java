package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.dto.IyzicoWebhookResponse;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StubPaymentController.class, PaymentController.class})
@Import(StubPaymentDisabledTest.SecurityTestConfig.class)
class StubPaymentDisabledTest {

    @EnableMethodSecurity
    static class SecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    SubscriptionService subscriptionService;

    @MockitoBean
    JwtService jwtService;

    // JwtAuthenticationFilter (pulled into the web slice) depends on this at construction time.
    @MockitoBean
    UserRepository userRepository;

    @Test
    @WithMockUser(roles = "STUDENT")
    void whenDisabled_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/payments/100/stub/succeed").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void webhookController_isAlwaysAvailable() throws Exception {
        IyzicoWebhookRequest webhookRequest = new IyzicoWebhookRequest(
                1L, "SUCCESS", "token-ref"
        );
        when(subscriptionService.processWebhook(any())).thenReturn(new IyzicoWebhookResponse("SUCCESS", "processed"));

        mockMvc.perform(post("/api/v1/payments/iyzico/webhook")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk());
    }
}
