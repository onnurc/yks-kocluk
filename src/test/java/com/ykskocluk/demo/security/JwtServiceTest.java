package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "unit-test-jwt-secret-0123456789-0123456789", Duration.ofMinutes(15), Duration.ofDays(30)));

    private User user() {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", 42L);
        u.setEmail("user@example.com");
        u.setRole(Role.STUDENT);
        return u;
    }

    @Test
    void generatesTokenWithSubjectEmailAndRole() {
        String token = jwtService.generateAccessToken(user());

        Claims claims = jwtService.parse(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsTamperedToken() {
        String tampered = jwtService.generateAccessToken(user()) + "tamper";
        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService(new JwtProperties(
                "a-totally-different-secret-key-0123456789", Duration.ofMinutes(15), Duration.ofDays(30)));
        String foreign = other.generateAccessToken(user());
        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }
}
