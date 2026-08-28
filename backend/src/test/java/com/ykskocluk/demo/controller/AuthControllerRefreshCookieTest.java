package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.LoginRequest;
import com.ykskocluk.demo.dto.RefreshRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.security.RefreshTokenCookieService;
import com.ykskocluk.demo.security.ratelimit.AuthRateLimitService;
import com.ykskocluk.demo.service.AuthService;
import com.ykskocluk.demo.service.EmailVerificationService;
import com.ykskocluk.demo.service.LegalAcceptanceService;
import com.ykskocluk.demo.service.PasswordSecurityService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshCookieTest {

    @Mock AuthService authService;
    @Mock AuthRateLimitService rateLimitService;
    @Mock LegalAcceptanceService legalAcceptanceService;
    @Mock PasswordSecurityService passwordSecurityService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock RefreshTokenCookieService cookieService;

    private MockHttpServletRequest request;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        controller = new AuthController(authService, rateLimitService, request, legalAcceptanceService,
                passwordSecurityService, emailVerificationService, cookieService);
    }

    @Test
    void loginSetsRefreshCookieAndKeepsRawTokenOutOfHeaderIndependentBodySerialization() {
        LoginRequest login = new LoginRequest("user@example.com", "SecurePassphrase42!");
        when(authService.login(login)).thenReturn(response("old-refresh"));
        when(cookieService.setCookieHeader("old-refresh")).thenReturn("secure-cookie");

        var result = controller.login(login);

        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo("secure-cookie");
        assertThat(result.getBody().accessToken()).isEqualTo("access-token");
    }

    @Test
    void refreshReadsCookieTokenAndRotatesCookie() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        when(cookieService.requireToken(request)).thenReturn("old-refresh");
        when(authService.refresh(new RefreshRequest("old-refresh"))).thenReturn(response("new-refresh"));
        when(cookieService.setCookieHeader("new-refresh")).thenReturn("rotated-cookie");

        var result = controller.refresh(servletResponse);

        verify(rateLimitService).checkRefresh("old-refresh", request);
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo("rotated-cookie");
    }

    @Test
    void logoutRevokesCurrentCookieTokenAndClearsCookie() {
        request.setCookies(new Cookie(RefreshTokenCookieService.COOKIE_NAME, "current-refresh"));
        when(cookieService.readToken(request)).thenReturn("current-refresh");
        when(cookieService.clearCookieHeader()).thenReturn("cleared-cookie");

        var result = controller.logout();

        verify(authService).logout(new com.ykskocluk.demo.dto.LogoutRequest("current-refresh"));
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo("cleared-cookie");
    }

    private AuthResponse response(String refreshToken) {
        return new AuthResponse("access-token", refreshToken, "Bearer", 900,
                new UserResponse(1L, "user@example.com", "User", Role.STUDENT, UserStatus.ACTIVE));
    }
}
