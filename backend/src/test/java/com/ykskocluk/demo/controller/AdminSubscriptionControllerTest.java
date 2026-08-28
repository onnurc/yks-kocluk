package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateRequest;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateResponse;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link AdminSubscriptionController} enforcing ADMIN authorization.
 */
@WebMvcTest(AdminSubscriptionController.class)
@Import(AdminSubscriptionControllerTest.MethodSecurityTestConfig.class)
class AdminSubscriptionControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SubscriptionService subscriptionService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canTerminateSubscription() throws Exception {
        AdminSubscriptionTerminateRequest request = new AdminSubscriptionTerminateRequest("Violation");
        AdminSubscriptionTerminateResponse response = new AdminSubscriptionTerminateResponse(
                100L, "TERMINATED", Instant.now(), "Abonelik başarıyla sonlandırıldı");

        when(subscriptionService.terminateSubscription(eq(100L), eq("Violation"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/subscriptions/100/terminate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value(100))
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.message").value("Abonelik başarıyla sonlandırıldı"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotTerminateSubscription_forbidden() throws Exception {
        AdminSubscriptionTerminateRequest request = new AdminSubscriptionTerminateRequest("Violation");

        mockMvc.perform(post("/api/v1/admin/subscriptions/100/terminate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotTerminateSubscription_forbidden() throws Exception {
        AdminSubscriptionTerminateRequest request = new AdminSubscriptionTerminateRequest("Violation");

        mockMvc.perform(post("/api/v1/admin/subscriptions/100/terminate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListSubscriptions() throws Exception {
        com.ykskocluk.demo.dto.PageResponse<com.ykskocluk.demo.dto.AdminSubscriptionResponse> response =
                new com.ykskocluk.demo.dto.PageResponse<>(java.util.List.of(), 0, 20, 0L, 0, true);

        when(subscriptionService.listSubscriptions(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/subscriptions")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_terminateSubscription_rejectsOversizedReasonBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/subscriptions/100/terminate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminSubscriptionTerminateRequest("x".repeat(2001)))))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(subscriptionService);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotListSubscriptions_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscriptions")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotListSubscriptions_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscriptions")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
