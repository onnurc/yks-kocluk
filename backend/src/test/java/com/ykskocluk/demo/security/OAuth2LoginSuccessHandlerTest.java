package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.service.AuthService;
import com.ykskocluk.demo.service.OAuth2LoginCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private AuthService authService;

    @Mock
    private OAuth2LoginCodeService oauth2LoginCodeService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oauth2User;

    private OAuth2LoginSuccessHandler handler;
    private User testUser;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(
                authService,
                oauth2LoginCodeService,
                "http://localhost:5173/oauth/callback"
        );
        testUser = new User();
        testUser.setEmail("google-user@example.com");
    }

    @Test
    void onAuthenticationSuccess_generatesCodeAndRedirectsWithCode() throws IOException {
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("google-user@example.com");
        when(oauth2User.getAttribute("sub")).thenReturn("google-sub-id");
        when(oauth2User.getAttribute("name")).thenReturn("Google User");
        when(oauth2User.getAttribute("email_verified")).thenReturn(true);

        when(authService.upsertGoogleUser("google-user@example.com", "google-sub-id", "Google User", true))
                .thenReturn(testUser);
        when(oauth2LoginCodeService.generateCodeForUser(testUser)).thenReturn("raw-exchange-code-123");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("http://localhost:5173/oauth/callback?code=raw-exchange-code-123&provider=google");
        verify(response, never()).sendRedirect(argThat(url -> url.contains("accessToken") || url.contains("refreshToken")));
    }

    @Test
    void onAuthenticationSuccess_withExistingQueryParameters_appendsCorrectly() throws IOException {
        OAuth2LoginSuccessHandler handlerWithQueryParams = new OAuth2LoginSuccessHandler(
                authService,
                oauth2LoginCodeService,
                "http://localhost:5173/oauth/callback?foo=bar"
        );

        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("google-user@example.com");
        when(oauth2User.getAttribute("sub")).thenReturn("google-sub-id");
        when(oauth2User.getAttribute("name")).thenReturn("Google User");
        when(oauth2User.getAttribute("email_verified")).thenReturn(true);

        when(authService.upsertGoogleUser("google-user@example.com", "google-sub-id", "Google User", true))
                .thenReturn(testUser);
        when(oauth2LoginCodeService.generateCodeForUser(testUser)).thenReturn("raw-exchange-code-123");

        handlerWithQueryParams.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("http://localhost:5173/oauth/callback?foo=bar&code=raw-exchange-code-123&provider=google");
    }

    @Test
    void onAuthenticationSuccessInvalidatesTemporaryOauthSession() throws IOException {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("google-user@example.com");
        when(oauth2User.getAttribute("sub")).thenReturn("google-sub-id");
        when(oauth2User.getAttribute("name")).thenReturn("Google User");
        when(oauth2User.getAttribute("email_verified")).thenReturn(true);
        when(authService.upsertGoogleUser("google-user@example.com", "google-sub-id", "Google User", true))
                .thenReturn(testUser);
        when(oauth2LoginCodeService.generateCodeForUser(testUser)).thenReturn("one-time-code");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(session).invalidate();
    }
}
