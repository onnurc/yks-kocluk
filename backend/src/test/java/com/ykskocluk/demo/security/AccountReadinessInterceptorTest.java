package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.AccountReadinessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountReadinessInterceptorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccountReadinessService readiness = mock(AccountReadinessService.class);
    private final AccountReadinessInterceptor interceptor = new AccountReadinessInterceptor(users, readiness);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyNewAuthenticatedProductMutationIsReadinessCheckedByDefault() throws Exception {
        User user = new User();
        user.setRole(Role.STUDENT);
        authenticate(7L, "STUDENT");
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThat(interceptor.preHandle(request("POST", "/api/v1/new-product/action"),
                new MockHttpServletResponse(), new Object())).isTrue();

        verify(readiness).requireReady(user);
    }

    @Test
    void unreadyUserFailurePropagatesForProtectedMutation() {
        User user = new User();
        authenticate(7L, "STUDENT");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "EMAIL_VERIFICATION_REQUIRED", "Doğrulama gerekli"))
                .when(readiness).requireReady(user);

        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/reports"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Doğrulama");
    }

    @Test
    void verificationOnboardingLogoutAndRequiredReadsRemainAvailable() throws Exception {
        authenticate(7L, "STUDENT");
        for (MockHttpServletRequest request : List.of(
                request("POST", "/api/v1/auth/verify-email"),
                request("POST", "/api/v1/auth/legal-onboarding"),
                request("POST", "/api/v1/auth/logout"),
                request("POST", "/api/v1/privacy/account-deletion"),
                request("GET", "/api/v1/student-profiles/me"))) {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }
        verifyNoInteractions(users, readiness);
    }

    private void authenticate(Long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
