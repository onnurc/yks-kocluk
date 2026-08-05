package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.OAuth2ExchangeRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.security.ratelimit.AuthRateLimitService;
import com.ykskocluk.demo.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ykskocluk.demo.security.ratelimit.RateLimitExceededException;

@WebMvcTest(AuthController.class)
class AuthControllerOAuth2ExchangeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.ykskocluk.demo.service.LegalAcceptanceService legalAcceptanceService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser
    void exchangeCode_validCode_returnsAuthResponse() throws Exception {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("valid-code-123");
        UserResponse userResponse = new UserResponse(1L, "student@example.com", "Test Student", Role.STUDENT, UserStatus.ACTIVE);
        AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", "Bearer", 900L, userResponse);

        when(authService.exchangeOAuth2Code("valid-code-123")).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("student@example.com"));
    }

    @Test
    @WithMockUser
    void exchangeCode_invalidCode_returns401() throws Exception {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("invalid-code");

        when(authService.exchangeOAuth2Code("invalid-code"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OAUTH_CODE", "Geçersiz veya süresi dolmuş kod"));

        mockMvc.perform(post("/api/v1/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OAUTH_CODE"));
    }

    @Test
    @WithMockUser
    void exchangeCode_blankCode_returns400ValidationError() throws Exception {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("");

        mockMvc.perform(post("/api/v1/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void exchangeCode_rateLimitExceeded_returns429AndRetryAfter() throws Exception {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("valid-code-123");

        doThrow(new RateLimitExceededException(45L))
                .when(rateLimitService).checkOAuth2Exchange(any());

        mockMvc.perform(post("/api/v1/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }
}
