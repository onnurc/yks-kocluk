package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.LoginRequest;
import com.ykskocluk.demo.dto.LogoutRequest;
import com.ykskocluk.demo.dto.OAuth2ExchangeRequest;
import com.ykskocluk.demo.dto.RefreshRequest;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.dto.LegalOnboardingRequest;
import com.ykskocluk.demo.dto.LegalOnboardingResponse;
import com.ykskocluk.demo.service.AuthService;
import com.ykskocluk.demo.service.PasswordSecurityService;
import com.ykskocluk.demo.dto.ChangePasswordRequest;
import com.ykskocluk.demo.dto.ForgotPasswordRequest;
import com.ykskocluk.demo.dto.ResetPasswordRequest;
import com.ykskocluk.demo.dto.PasswordActionResponse;
import com.ykskocluk.demo.service.LegalAcceptanceService;
import com.ykskocluk.demo.service.EmailVerificationService;
import com.ykskocluk.demo.dto.EmailVerificationResponse;
import com.ykskocluk.demo.dto.VerifyEmailRequest;
import com.ykskocluk.demo.security.ratelimit.AuthRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimitService rateLimitService;
    private final HttpServletRequest httpServletRequest;
    private final LegalAcceptanceService legalAcceptanceService;
    private final PasswordSecurityService passwordSecurityService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService,
                          AuthRateLimitService rateLimitService,
                          HttpServletRequest httpServletRequest,
                          LegalAcceptanceService legalAcceptanceService,
                          PasswordSecurityService passwordSecurityService,
                          EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.httpServletRequest = httpServletRequest;
        this.legalAcceptanceService = legalAcceptanceService;
        this.passwordSecurityService = passwordSecurityService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/verify-email")
    @PreAuthorize("isAuthenticated()")
    public EmailVerificationResponse verifyEmail(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody VerifyEmailRequest request) {
        rateLimitService.checkEmailVerification(userId, httpServletRequest);
        return emailVerificationService.verify(userId, request.code());
    }

    @PostMapping("/resend-verification")
    @PreAuthorize("isAuthenticated()")
    public EmailVerificationResponse resendVerification(@AuthenticationPrincipal Long userId) {
        rateLimitService.checkEmailVerificationResend(userId, httpServletRequest);
        return emailVerificationService.resend(userId);
    }

    @PostMapping("/forgot-password")
    public PasswordActionResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        rateLimitService.checkForgotPassword(request.email(), httpServletRequest);
        return passwordSecurityService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public PasswordActionResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        rateLimitService.checkResetPassword(request.token(), httpServletRequest);
        return passwordSecurityService.resetPassword(request);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public PasswordActionResponse changePassword(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody ChangePasswordRequest request) {
        rateLimitService.checkChangePassword(userId, httpServletRequest);
        return passwordSecurityService.changePassword(userId, request);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        rateLimitService.checkRegister(request.email(), httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        rateLimitService.checkLogin(request.email(), httpServletRequest);
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        rateLimitService.checkRefresh(request.refreshToken(), httpServletRequest);
        return authService.refresh(request);
    }

    @PostMapping("/oauth2/exchange")
    public AuthResponse exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        rateLimitService.checkOAuth2Exchange(httpServletRequest);
        return authService.exchangeOAuth2Code(request.code());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return authService.getCurrentUser(userId);
    }

    @PostMapping("/legal-onboarding")
    @PreAuthorize("isAuthenticated()")
    public LegalOnboardingResponse completeLegalOnboarding(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LegalOnboardingRequest request) {
        legalAcceptanceService.completeOnboarding(userId, request);
        return new LegalOnboardingResponse(true);
    }
}
