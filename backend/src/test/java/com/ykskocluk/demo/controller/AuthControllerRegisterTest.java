package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.security.RefreshTokenCookieService;
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

import java.time.LocalDate;
import java.util.List;
import com.ykskocluk.demo.dto.EmailVerificationResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerRegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.ykskocluk.demo.service.LegalAcceptanceService legalAcceptanceService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private com.ykskocluk.demo.service.EmailVerificationService emailVerificationService;

    @MockitoBean
    private com.ykskocluk.demo.service.PasswordSecurityService passwordSecurityService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    @WithMockUser
    void malformedRegistrationEmailIsRejectedBeforeUserCreation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"emre@.com\",\"password\":\"SecurePassphrase42!\",\"fullName\":\"Emre\",\"dateOfBirth\":\"2005-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(authService);
    }

    @Test
    void verifyEmail_authenticatedUserUsesPrincipalAndReturnsState() throws Exception {
        when(emailVerificationService.verify(7L, "123456"))
                .thenReturn(new EmailVerificationResponse(true, null));
        var auth = new UsernamePasswordAuthenticationToken(7L, null, List.of());

        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf()).with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void verifyEmail_rejectsAnythingOtherThanSixDigits() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf()).with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12a45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    void register_studentNullDob_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("student@example.com", "SecurePassphrase42!", "Student", null);

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "DATE_OF_BIRTH_REQUIRED", "Öğrenci kaydı için doğum tarihi zorunludur"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DATE_OF_BIRTH_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Öğrenci kaydı için doğum tarihi zorunludur"));
    }

    @Test
    @WithMockUser
    void register_studentFutureDob_returnsBadRequest() throws Exception {
        LocalDate future = LocalDate.now().plusDays(5);
        RegisterRequest request = new RegisterRequest("student@example.com", "SecurePassphrase42!", "Student", future);

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH", "Doğum tarihi gelecekte olamaz"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_OF_BIRTH"))
                .andExpect(jsonPath("$.detail").value("Doğum tarihi gelecekte olamaz"));
    }

    @Test
    @WithMockUser
    void register_studentValidDob_returnsCreated() throws Exception {
        LocalDate dob = LocalDate.of(2005, 5, 5);
        RegisterRequest request = new RegisterRequest("student@example.com", "SecurePassphrase42!", "Student", dob);
        UserResponse userResponse = new UserResponse(1L, "student@example.com", "Student", Role.STUDENT, UserStatus.ACTIVE);
        AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", "Bearer", 900L, userResponse);

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);
        when(refreshTokenCookieService.setCookieHeader("refresh-token"))
                .thenReturn("yks_refresh_token=refresh-token; Path=/api/v1/auth; HttpOnly; SameSite=Lax");

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.email").value("student@example.com"));
    }

    @Test
    @WithMockUser
    void legalOnboarding_authenticatedUser_completesOwnOnboarding() throws Exception {
        mockMvc.perform(post("/api/v1/auth/legal-onboarding")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsDocumentId\":3,\"explicitConsentDocumentId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalOnboardingCompleted").value(true));
    }
}
