package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachApplicationRequest;
import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachApplicationRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachApplicationServiceTest {

    @Mock CoachApplicationRepository coachApplicationRepository;
    @Mock UserRepository userRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordSecurityService passwordSecurityService;

    private CoachApplicationService service() {
        return new CoachApplicationService(coachApplicationRepository, userRepository, coachProfileRepository,
                passwordEncoder, passwordSecurityService);
    }

    private CoachApplicationRequest request(String email) {
        return new CoachApplicationRequest("Aday Koç", email, null, "Deneyim");
    }

    // --- submit: duplicate-application guard ---

    @Test
    void submit_pendingApplicationExists_rejected() {
        CoachApplicationService service = service();
        String email = "coach@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING))
                .thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.submit(request(email)));

        assertThat(ex.getErrorCode()).isEqualTo("COACH_APPLICATION_ALREADY_PENDING");
        verify(coachApplicationRepository, never()).save(any());
    }

    @Test
    void submit_approvedApplicationExists_rejected() {
        CoachApplicationService service = service();
        String email = "coach@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING))
                .thenReturn(false);
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.APPROVED))
                .thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.submit(request(email)));

        assertThat(ex.getErrorCode()).isEqualTo("COACH_APPLICATION_ALREADY_APPROVED");
        verify(coachApplicationRepository, never()).save(any());
    }

    @Test
    void submit_onlyRejectedApplicationExists_allowed() {
        CoachApplicationService service = service();
        String email = "coach@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING))
                .thenReturn(false);
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.APPROVED))
                .thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        service.submit(request(email));

        ArgumentCaptor<CoachApplication> captor = ArgumentCaptor.forClass(CoachApplication.class);
        verify(coachApplicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CoachApplicationStatus.PENDING);
        assertThat(captor.getValue().getEmail()).isEqualTo(email);
    }

    // --- submit: already-a-User guard ---

    @Test
    void submit_emailAlreadyBelongsToUser_rejected() {
        CoachApplicationService service = service();
        String email = "student@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(eq(email), any()))
                .thenReturn(false);
        User existing = new User();
        existing.setRole(Role.STUDENT);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.submit(request(email)));

        assertThat(ex.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
        verify(coachApplicationRepository, never()).save(any());
    }

    @Test
    void submit_noConflicts_savesPendingApplication() {
        CoachApplicationService service = service();
        String email = "new-applicant@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(eq(email), any()))
                .thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        service.submit(request(email));

        verify(coachApplicationRepository).save(any(CoachApplication.class));
    }

    // --- submit: email normalization ---

    @Test
    void submit_normalizesEmailCaseAndWhitespace() {
        CoachApplicationService service = service();
        CoachApplicationRequest req = new CoachApplicationRequest("Aday Koç", "  Coach@Example.com  ", null, null);
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(eq("coach@example.com"), any()))
                .thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("coach@example.com")).thenReturn(Optional.empty());

        service.submit(req);

        ArgumentCaptor<CoachApplication> captor = ArgumentCaptor.forClass(CoachApplication.class);
        verify(coachApplicationRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("coach@example.com");
    }

    @Test
    void approve_createsPendingCoachProfileShellForAuthenticatedProductPages() {
        CoachApplicationService service = service();
        CoachApplication application = new CoachApplication();
        application.setEmail("approved@example.com");
        application.setFullName("Approved Coach");
        application.setStatus(CoachApplicationStatus.PENDING);
        when(coachApplicationRepository.findById(14L)).thenReturn(Optional.of(application));
        when(userRepository.findByEmailIgnoreCase("approved@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-placeholder");

        service.approve(14L);

        ArgumentCaptor<CoachProfile> profileCaptor = ArgumentCaptor.forClass(CoachProfile.class);
        verify(coachProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser().getRole()).isEqualTo(Role.COACH);
        assertThat(profileCaptor.getValue().getStatus()).isEqualTo(CoachProfileStatus.PENDING);
        assertThat(profileCaptor.getValue().getHeadline()).isNull();
        assertThat(profileCaptor.getValue().getUniversity()).isNull();
        assertThat(profileCaptor.getValue().getActiveStudentCount()).isZero();
        verify(passwordSecurityService).forgotPassword(any());
    }
}
