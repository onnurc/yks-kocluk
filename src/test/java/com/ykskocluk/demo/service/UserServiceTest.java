package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

    @InjectMocks
    UserService userService;

    @Test
    void suspendUser_success_updatesStatusAndReason() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        SuspendResponse response = userService.suspendUser(1L, "Suspicious activity");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(response.reason()).isEqualTo("Suspicious activity");

        verify(userRepository).saveAndFlush(argThat(u ->
                u.getStatus() == UserStatus.SUSPENDED &&
                u.getSuspensionReason().equals("Suspicious activity")
        ));
    }

    @Test
    void suspendUser_notFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> userService.suspendUser(1L, "Suspicious activity"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");

        verify(userRepository, never()).saveAndFlush(any());
    }
}
