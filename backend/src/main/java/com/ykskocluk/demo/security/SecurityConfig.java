package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.config.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API requests authenticate with bearer JWTs and do not use the HTTP session as an API
 * authentication store.
 *
 * <p>{@link SessionCreationPolicy#IF_REQUIRED} is intentional: Spring Security's Google OAuth2
 * authorization-code handshake temporarily stores its authorization request/state in an HTTP
 * session. Normal bearer-token API traffic remains session-independent.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, com.ykskocluk.demo.config.PasswordSecurityProperties.class,
        com.ykskocluk.demo.config.EmailVerificationProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                            ProblemDetailAccessDeniedHandler accessDeniedHandler,
                                            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                                            CorsProperties corsProperties) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                // API authorization is bearer-header based, not cookie based. The temporary
                // OAuth2 handshake session does not make application API endpoints cookie-authenticated.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                                        + "script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: https:; media-src 'self' https:; "
                                        + "frame-src https://www.youtube.com https://www.youtube-nocookie.com; "
                                        + "connect-src 'self' https: wss:; "
                                        + "form-action 'self' https://*.iyzico.com https://*.iyzipay.com"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/packages",
                                "/api/v1/public/coaches", "/api/v1/public/coaches/*",
                                // Redirects to a signed URL for PUBLIC assets only; browsers cannot
                                // attach a bearer token when loading <img>/<video> sources.
                                "/api/v1/public/media/*").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/oauth2/exchange",
                                "/api/v1/legal-documents/**",
                                "/api/v1/payments/iyzico/webhook",
                                "/api/v1/public/coach-applications")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // STOMP handshake is open; the JWT is validated in the CONNECT frame
                        // by StompAuthChannelInterceptor (no token rides on the handshake).
                        .requestMatchers("/ws/**").permitAll()
                        // Coarse route protection complements controller method security. Public
                        // coach discovery uses /api/v1/coaches/** and is intentionally unaffected.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/coach/**").hasRole("COACH")
                        .requestMatchers("/api/v1/student/**", "/api/v1/students/**",
                                "/api/v1/trial-consultations/**", "/api/v1/refund-requests/**")
                        .hasRole("STUDENT")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth.successHandler(oauth2LoginSuccessHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Only the HttpOnly refresh cookie uses credentials and its Path is restricted to /auth.
        // Origins remain an exact allowlist; CorsProperties rejects wildcard configuration.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
