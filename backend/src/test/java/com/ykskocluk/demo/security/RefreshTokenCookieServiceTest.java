package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.CorsProperties;
import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RefreshTokenCookieServiceTest {

    private static final JwtProperties JWT = new JwtProperties(
            "not-used-in-this-test-not-used-in-this-test", Duration.ofMinutes(15), Duration.ofDays(30));
    private static final CorsProperties CORS = new CorsProperties(List.of("https://app.example.com"));

    @Test
    void productionCookieIsHttpOnlySecureScopedAndBounded() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(JWT, new MockEnvironment(), CORS);

        String cookie = service.setCookieHeader("opaque-token");

        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth",
                "Max-Age=2592000");
    }

    @Test
    void localCookieRemainsHttpOnlyButDoesNotRequireHttps() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RefreshTokenCookieService service = new RefreshTokenCookieService(JWT, environment, CORS);

        String cookie = service.setCookieHeader("opaque-token");

        assertThat(cookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
    }

    @Test
    void clearCookieExpiresTheSameScopedCookie() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(JWT, new MockEnvironment(), CORS);

        assertThat(service.clearCookieHeader())
                .contains("yks_refresh_token=", "Path=/api/v1/auth", "Max-Age=0", "HttpOnly");
    }

    @Test
    void browserOriginMustMatchConfiguredCorsOriginExactly() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(JWT, new MockEnvironment(), CORS);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://evil.example.com");

        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.requireTrustedBrowserOrigin(request));

        assertThat(error.getErrorCode()).isEqualTo("UNTRUSTED_ORIGIN");
    }
}
