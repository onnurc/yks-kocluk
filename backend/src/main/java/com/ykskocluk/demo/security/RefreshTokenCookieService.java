package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.CorsProperties;
import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.exception.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Creates and reads the server-only rotating refresh-token cookie. */
@Component
public class RefreshTokenCookieService {

    public static final String COOKIE_NAME = "yks_refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final Duration refreshTtl;
    private final boolean secure;
    private final CorsProperties corsProperties;

    public RefreshTokenCookieService(JwtProperties jwtProperties, Environment environment,
                                     CorsProperties corsProperties) {
        this.refreshTtl = jwtProperties.refreshTtl();
        this.secure = !environment.acceptsProfiles(Profiles.of("local", "test", "stub"));
        this.corsProperties = corsProperties;
    }

    public String setCookieHeader(String rawRefreshToken) {
        return cookie(rawRefreshToken, refreshTtl).toString();
    }

    public String clearCookieHeader() {
        return cookie("", Duration.ZERO).toString();
    }

    public String requireToken(HttpServletRequest request) {
        String token = readToken(request);
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "Geçersiz yenileme jetonu");
        }
        return token;
    }

    public String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * SameSite=Lax blocks ordinary cross-site POSTs. This origin check additionally rejects
     * credentialed browser calls from an untrusted same-site sibling origin. Non-browser clients
     * may omit Origin; they cannot obtain the HttpOnly cookie through cross-origin script.
     */
    public void requireTrustedBrowserOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank() && !corsProperties.allowedOrigins().contains(origin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "UNTRUSTED_ORIGIN", "İstek kaynağına izin verilmiyor");
        }
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
