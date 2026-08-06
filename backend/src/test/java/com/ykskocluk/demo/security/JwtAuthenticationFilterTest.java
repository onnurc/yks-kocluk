package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.impl.DefaultJws;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter} ensuring suspended user requests are blocked centrally.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @Test
    void doFilterInternal_suspendedUser_returns403AndShortCircuits() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

        Claims claims = new DefaultClaims(Map.of("sub", "1", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("valid-token")).thenReturn(jws);

        User suspendedUser = new User();
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspendedUser));

        StringWriter out = new StringWriter();
        PrintWriter printWriter = new PrintWriter(out);
        when(response.getWriter()).thenReturn(printWriter);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(response).setContentType("application/problem+json");
        assertThat(out.toString()).contains("USER_SUSPENDED");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_deletedUser_returns403AndShortCircuits() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

        Claims claims = new DefaultClaims(Map.of("sub", "1", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("valid-token")).thenReturn(jws);

        User deletedUser = new User();
        deletedUser.setStatus(UserStatus.DELETED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(deletedUser));

        StringWriter out = new StringWriter();
        PrintWriter printWriter = new PrintWriter(out);
        when(response.getWriter()).thenReturn(printWriter);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(response).setContentType("application/problem+json");
        assertThat(out.toString()).contains("USER_DELETED");
        assertThat(out.toString()).contains("Hesap artık kullanılamaz");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_activeUser_authenticatesAndContinues() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

        Claims claims = new DefaultClaims(Map.of("sub", "1", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("valid-token")).thenReturn(jws);

        User activeUser = new User();
        activeUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    void doFilterInternal_tokenIssuedBeforePasswordChange_remainsUnauthenticated() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class); HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class); when(request.getHeader("Authorization")).thenReturn("Bearer old-token");
        Claims claims = new DefaultClaims(Map.of("sub", "1", "role", "STUDENT", "iat", Date.from(Instant.parse("2026-01-01T00:00:00Z"))));
        Jws jws = mock(Jws.class); when(jws.getPayload()).thenReturn(claims); when(jwtService.parse("old-token")).thenReturn(jws);
        User user = new User(); user.setStatus(UserStatus.ACTIVE); user.setPasswordChangedAt(Instant.parse("2026-01-02T00:00:00Z"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        jwtAuthenticationFilter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_passwordVersionMismatch_remainsUnauthenticated() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class); HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class); when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        Claims claims = new DefaultClaims(Map.of("sub", "1", "role", "STUDENT", "passwordVersion", 0));
        Jws jws = mock(Jws.class); when(jws.getPayload()).thenReturn(claims); when(jwtService.parse("stale-token")).thenReturn(jws);
        User user = new User(); user.setStatus(UserStatus.ACTIVE); user.setPasswordVersion(1); when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        jwtAuthenticationFilter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
