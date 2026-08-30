package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachAccountProvisioningServiceTest {
    @Mock UserRepository users;
    @Mock CoachProfileRepository profiles;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordSecurityService passwordSecurityService;

    @Test
    void manualProvisioningNormalizesAndCreatesSecurePendingCoach() {
        when(users.findByEmailIgnoreCase("coach@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-placeholder");
        var service = new CoachAccountProvisioningService(users, profiles, passwordEncoder, passwordSecurityService);

        service.provision("  Yeni Koç  ", "  Coach@Example.COM ", AccountOrigin.ADMIN_MANUAL);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("coach@example.com");
        assertThat(user.getFullName()).isEqualTo("Yeni Koç");
        assertThat(user.getRole()).isEqualTo(Role.COACH);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isLegalOnboardingCompleted()).isFalse();
        assertThat(user.getAccountOrigin()).isEqualTo(AccountOrigin.ADMIN_MANUAL);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-placeholder");

        ArgumentCaptor<CoachProfile> profileCaptor = ArgumentCaptor.forClass(CoachProfile.class);
        verify(profiles).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getStatus()).isEqualTo(CoachProfileStatus.PENDING);
        assertThat(profileCaptor.getValue().getUser()).isSameAs(user);
        verify(passwordSecurityService).forgotPassword(argThat(request -> request.email().equals("coach@example.com")));
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnyAccountOrMailIsCreated() {
        User existing = new User();
        existing.setRole(Role.STUDENT);
        when(users.findByEmailIgnoreCase("used@example.com")).thenReturn(Optional.of(existing));
        var service = new CoachAccountProvisioningService(users, profiles, passwordEncoder, passwordSecurityService);

        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.provision("Coach", "used@example.com", AccountOrigin.ADMIN_MANUAL));

        assertThat(error.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
        verify(users, never()).save(any());
        verify(profiles, never()).save(any());
        verify(passwordSecurityService, never()).forgotPassword(any());
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
