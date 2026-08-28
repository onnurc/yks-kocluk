package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.SuspendRequest;
import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.UserService;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link AdminUserController} enforcing ADMIN authorization.
 */
@WebMvcTest(AdminUserController.class)
@Import(AdminUserControllerTest.MethodSecurityTestConfig.class)
class AdminUserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    com.ykskocluk.demo.integration.MailClient mailClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canSuspendUser() throws Exception {
        SuspendRequest request = new SuspendRequest("Toxicity");
        SuspendResponse response = new SuspendResponse(5L, "SUSPENDED", "Toxicity", "user@example.com");

        when(userService.suspendUser(any(), eq(5L), eq("Toxicity"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.reason").value("Toxicity"));

        org.mockito.Mockito.verify(mailClient).sendUserSuspended("user@example.com");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_suspendUser_mailThrows_stillSucceeds() throws Exception {
        SuspendRequest request = new SuspendRequest("Toxicity");
        SuspendResponse response = new SuspendResponse(5L, "SUSPENDED", "Toxicity", "user@example.com");

        when(userService.suspendUser(any(), eq(5L), eq("Toxicity"))).thenReturn(response);
        org.mockito.Mockito.doThrow(new RuntimeException("Mail server down"))
                .when(mailClient).sendUserSuspended(any());

        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_suspendUser_failedSuspend_doesNotSendMail() throws Exception {
        SuspendRequest request = new SuspendRequest("Toxicity");

        when(userService.suspendUser(any(), eq(5L), eq("Toxicity")))
                .thenThrow(new com.ykskocluk.demo.exception.ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_SELF", "Self"));

        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(mailClient, org.mockito.Mockito.never()).sendUserSuspended(any());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotSuspendUser_forbidden() throws Exception {
        SuspendRequest request = new SuspendRequest("Toxicity");

        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotSuspendUser_forbidden() throws Exception {
        SuspendRequest request = new SuspendRequest("Toxicity");

        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canUnsuspendUser() throws Exception {
        when(userService.unsuspendUser(5L))
                .thenReturn(new SuspendResponse(5L, "ACTIVE", null, "user@example.com"));

        mockMvc.perform(post("/api/v1/admin/users/5/unsuspend").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_suspendUser_rejectsOversizedReasonBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/suspend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SuspendRequest("x".repeat(2001)))))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotUnsuspendUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/unsuspend").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
