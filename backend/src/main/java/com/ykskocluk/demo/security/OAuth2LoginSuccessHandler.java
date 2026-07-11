package com.ykskocluk.demo.security;

import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Runs after a successful Google OAuth2 login. Resolves/links the user via
 * {@link AuthService#upsertGoogleUser}, issues our own JWT pair, and redirects to the
 * configured frontend URL carrying the tokens. (MVP: tokens in the redirect query;
 * revisit once the Next.js app exists.)
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(AuthService authService,
                                     @Value("${app.oauth2.frontend-redirect-uri}") String frontendRedirectUri) {
        this.authService = authService;
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

        AuthResponse tokens = authService.upsertGoogleUser(
                email, googleSub, fullName, Boolean.TRUE.equals(emailVerified));

        String target = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("accessToken", tokens.accessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .build().toUriString();
        response.sendRedirect(target);
    }
}
