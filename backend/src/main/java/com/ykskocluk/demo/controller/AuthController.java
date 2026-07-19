package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.LoginRequest;
import com.ykskocluk.demo.dto.LogoutRequest;
import com.ykskocluk.demo.dto.OAuth2ExchangeRequest;
import com.ykskocluk.demo.dto.RefreshRequest;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.service.AuthService;
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

    public AuthController(AuthService authService,
                          AuthRateLimitService rateLimitService,
                          HttpServletRequest httpServletRequest) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.httpServletRequest = httpServletRequest;
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
}
