package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.WebSocketSessionsInvalidatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    UserService userService;

    @Test
    void suspendUser_success_updatesStatusAndReason() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        user.setEmail("suspended@example.com");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        SuspendResponse response = userService.suspendUser(1L, 2L, "Suspicious activity");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(response.reason()).isEqualTo("Suspicious activity");
        assertThat(response.email()).isEqualTo("suspended@example.com");

        verify(userRepository).saveAndFlush(argThat(u ->
                u.getStatus() == UserStatus.SUSPENDED &&
                u.getSuspensionReason().equals("Suspicious activity") &&
                "suspended@example.com".equals(u.getEmail())
        ));
        verify(eventPublisher).publishEvent(new WebSocketSessionsInvalidatedEvent(2L));
    }

    @Test
    void suspendUser_notFound_throwsNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> userService.suspendUser(1L, 2L, "Suspicious activity"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspendUser_selfSuspend_throwsBadRequest() {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> userService.suspendUser(1L, 1L, "Self suspend"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("CANNOT_SUSPEND_SELF");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspendUser_suspendAdmin_throwsBadRequest() {
        User user = new User();
        user.setRole(com.ykskocluk.demo.enums.Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> userService.suspendUser(1L, 2L, "Suspend admin"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("CANNOT_SUSPEND_ADMIN");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void unsuspendUser_restoresActiveAndClearsReason() {
        User user = new User();
        user.setRole(com.ykskocluk.demo.enums.Role.COACH);
        user.setStatus(UserStatus.SUSPENDED);
        user.setSuspensionReason("review");
        user.setEmail("coach@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        SuspendResponse response = userService.unsuspendUser(2L);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(user.getSuspensionReason()).isNull();
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void unsuspendUser_neverReactivatesDeletedIdentity() {
        User user = new User();
        user.setRole(com.ykskocluk.demo.enums.Role.COACH);
        user.setStatus(UserStatus.DELETED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> userService.unsuspendUser(2L));

        assertThat(ex.getErrorCode()).isEqualTo("DELETED_USER_CANNOT_BE_REACTIVATED");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspendUser_neverChangesDeletedIdentity() {
        User user = new User();
        user.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        user.setStatus(UserStatus.DELETED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> userService.suspendUser(1L, 2L, "reason"));

        assertThat(ex.getErrorCode()).isEqualTo("DELETED_USER_CANNOT_BE_SUSPENDED");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(userRepository, never()).saveAndFlush(any());
    }
}
