package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.service.AuthService;
import com.ykskocluk.demo.service.OAuth2LoginCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Runs after a successful Google OAuth2 login. Resolves/links the user via
 * {@link AuthService#upsertGoogleUser}, issues a short-lived random one-time OAuth
 * login code, and redirects to the configured frontend callback URL carrying the code.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuth2LoginCodeService oauth2LoginCodeService;
    private final String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(AuthService authService,
                                     OAuth2LoginCodeService oauth2LoginCodeService,
                                     @Value("${app.oauth2.frontend-redirect-uri}") String frontendRedirectUri) {
        this.authService = authService;
        this.oauth2LoginCodeService = oauth2LoginCodeService;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String email = principal.getAttribute("email");
        String googleSub = principal.getAttribute("sub");
        String fullName = principal.getAttribute("name");
        Boolean emailVerified = principal.getAttribute("email_verified");

        User user = authService.upsertGoogleUser(
                email, googleSub, fullName, Boolean.TRUE.equals(emailVerified));

        String code = oauth2LoginCodeService.generateCodeForUser(user);

        // The HTTP session exists only to protect the OAuth2 authorization-code handshake.
        // Authentication continues through the one-time frontend exchange code, not a cookie
        // session, so discard the temporary session before redirecting.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        String target = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("code", code)
                .queryParam("provider", "google")
                .build().toUriString();
        response.sendRedirect(target);
    }
}
