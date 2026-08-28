package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.AccountReadinessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Default-deny readiness gate for authenticated product mutations. Read-only requests and the
 * explicit authentication/privacy/onboarding allowlist remain available so an unready account can
 * verify its email, accept legal documents, recover/change credentials, log out, or be deleted.
 * Existing service guards remain as defense in depth for core business operations and STOMP.
 */
public class AccountReadinessInterceptor implements HandlerInterceptor {

    private static final List<String> PRE_READINESS_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/privacy/",
            "/api/v1/consents/",
            "/api/v1/public/");

    private final UserRepository userRepository;
    private final AccountReadinessService readinessService;

    public AccountReadinessInterceptor(UserRepository userRepository,
                                       AccountReadinessService readinessService) {
        this.userRepository = userRepository;
        this.readinessService = readinessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isSafeMethod(request.getMethod()) || isExplicitlyAllowed(request.getRequestURI())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isAdmin(authentication)
                || !(authentication.getPrincipal() instanceof Long userId)) {
            return true;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Kullanıcı bulunamadı"));
        readinessService.requireReady(user);
        return true;
    }

    private boolean isSafeMethod(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method);
    }

    private boolean isExplicitlyAllowed(String path) {
        return PRE_READINESS_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
