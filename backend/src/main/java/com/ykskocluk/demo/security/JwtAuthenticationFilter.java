package com.ykskocluk.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.Optional;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a {@code Authorization: Bearer <accessToken>} header.
 * On a valid token the {@code SecurityContext} is populated with the user id as principal
 * and a {@code ROLE_<role>} authority. Invalid/absent tokens are ignored here — the
 * security entry point handles the resulting 401 for protected endpoints.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token).getPayload();
                Long userId = Long.valueOf(claims.getSubject());

                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    UserStatus status = userOpt.get().getStatus();
                    if (status == UserStatus.SUSPENDED || status == UserStatus.DELETED) {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        String detail = status == UserStatus.SUSPENDED ? "Hesabınız askıya alınmıştır" : "Hesap artık kullanılamaz";
                        String errorCode = status == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_DELETED";
                        response.getWriter().write("""
                                {"type":"about:blank","title":"Forbidden","status":403,\
                                "detail":"%s","errorCode":"%s","timestamp":"%s"}\
                                """.formatted(detail, errorCode, Instant.now()));
                        return;
                    }
                }

                String role = claims.get("role", String.class);
                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid token → leave the context unauthenticated; entry point returns 401.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
