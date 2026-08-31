package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachApplicationRequest;
import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachApplicationRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachApplicationServiceTest {

    @Mock CoachApplicationRepository coachApplicationRepository;
    @Mock UserRepository userRepository;
    @Mock CoachAccountProvisioningService provisioningService;

    private CoachApplicationService service() {
        return new CoachApplicationService(coachApplicationRepository, userRepository, provisioningService);
    }

    private CoachApplicationRequest request(String email) {
        return new CoachApplicationRequest("Aday Koç", email, null, "Deneyim");
    }

    // --- submit: duplicate-application guard ---

    @Test
    void submit_invalidEmailDoesNotCreateApplication() {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service().submit(request("emre@gmail..com")));
        assertThat(error.getErrorCode()).isEqualTo("INVALID_EMAIL");
        verify(coachApplicationRepository, never()).save(any());
    }

    @Test
    void submit_pendingApplicationExists_returnsWithoutExposingState() {
        CoachApplicationService service = service();
        String email = "coach@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING))
                .thenReturn(true);

        service.submit(request(email));
        verify(coachApplicationRepository, never()).save(any());
    }

    @Test
    void submit_approvedApplicationExists_returnsWithoutExposingState() {
        CoachApplicationService service = service();
        String email = "coach@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING))
                .thenReturn(false);
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.APPROVED))
                .thenReturn(true);

        service.submit(request(email));
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
    void submit_emailAlreadyBelongsToUser_returnsWithoutExposingState() {
        CoachApplicationService service = service();
        String email = "student@example.com";
        when(coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(eq(email), any()))
                .thenReturn(false);
        User existing = new User();
        existing.setRole(Role.STUDENT);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

        service.submit(request(email));
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
    void adminListRejectsUnsupportedSortPropertyBeforeRepositoryQuery() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service().list(
                CoachApplicationStatus.PENDING, PageRequest.of(0, 20, Sort.by("linkedUser.passwordHash"))));

        assertThat(error.getErrorCode()).isEqualTo("INVALID_SORT_PROPERTY");
        verify(coachApplicationRepository, never()).findByStatus(any(), any());
    }

    @Test
    void adminListPreservesSupportedSortDirection() {
        when(coachApplicationRepository.findByStatus(eq(CoachApplicationStatus.PENDING), any()))
                .thenReturn(Page.empty());

        service().list(CoachApplicationStatus.PENDING,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt")));

        verify(coachApplicationRepository).findByStatus(eq(CoachApplicationStatus.PENDING),
                argThat(pageable -> pageable.getSort().getOrderFor("createdAt").isAscending()));
    }

    @Test
    void approve_createsPendingCoachProfileShellForAuthenticatedProductPages() {
        CoachApplicationService service = service();
        CoachApplication application = new CoachApplication();
        application.setEmail("approved@example.com");
        application.setFullName("Approved Coach");
        application.setStatus(CoachApplicationStatus.PENDING);
        when(coachApplicationRepository.findById(14L)).thenReturn(Optional.of(application));
        User user = new User();
        user.setEmail("approved@example.com");
        user.setFullName("Approved Coach");
        user.setRole(Role.COACH);
        CoachProfile profile = new CoachProfile();
        profile.setUser(user);
        profile.setStatus(CoachProfileStatus.PENDING);
        when(provisioningService.provision("Approved Coach", "approved@example.com", AccountOrigin.COACH_APPLICATION))
                .thenReturn(new CoachAccountProvisioningService.ProvisionedCoach(user, profile));

        service.approve(14L);

        verify(provisioningService).provision(
                "Approved Coach", "approved@example.com", AccountOrigin.COACH_APPLICATION);
        assertThat(application.getLinkedUser()).isSameAs(user);
        assertThat(application.getStatus()).isEqualTo(CoachApplicationStatus.APPROVED);
    }
}
