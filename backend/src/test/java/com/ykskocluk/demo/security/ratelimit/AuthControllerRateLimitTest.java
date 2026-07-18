package com.ykskocluk.demo.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.controller.AuthController;
import com.ykskocluk.demo.dto.LoginRequest;
import com.ykskocluk.demo.dto.RefreshRequest;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    @WithMockUser
    void login_rateLimitExceeded_returns429AndRetryAfterHeader() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123");

        doThrow(new RateLimitExceededException(45L))
                .when(rateLimitService).checkLogin(any(), any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Too many requests. Please try again later."));
    }

    @Test
    @WithMockUser
    void register_rateLimitExceeded_returns429AndRetryAfterHeader() throws Exception {
        RegisterRequest request = new RegisterRequest("student@example.com", "password123", "FullName", Role.STUDENT, LocalDate.of(2008, 1, 1));

        doThrow(new RateLimitExceededException(30L))
                .when(rateLimitService).checkRegister(any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Too many requests. Please try again later."));
    }

    @Test
    @WithMockUser
    void refresh_rateLimitExceeded_returns429AndRetryAfterHeader() throws Exception {
        RefreshRequest request = new RefreshRequest("some-refresh-token");

        doThrow(new RateLimitExceededException(15L))
                .when(rateLimitService).checkRefresh(any(), any());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "15"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Too many requests. Please try again later."));
    }
}
