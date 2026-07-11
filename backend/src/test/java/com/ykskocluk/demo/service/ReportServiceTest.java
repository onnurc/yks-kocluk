package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.ReportCreateRequest;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.entity.Report;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.enums.ReportTargetType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ReportRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReportService}.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    ReportRepository reportRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    com.ykskocluk.demo.repository.ConversationRepository conversationRepository;

    @Mock
    com.ykskocluk.demo.repository.MessageRepository messageRepository;

    @InjectMocks
    ReportService reportService;

    @Test
    void createReport_success_savesReportAsOpen() {
        User reporter = new User();
        reporter.setEmail("reporter@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(reporter, "id", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Message msg = new Message();
        org.springframework.test.util.ReflectionTestUtils.setField(msg, "id", 42L);
        Conversation conv = new Conversation();
        org.springframework.test.util.ReflectionTestUtils.setField(conv, "id", 99L);
        conv.setStudent(reporter);
        User coachUser = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(coachUser, "id", 2L);
        com.ykskocluk.demo.entity.CoachProfile coach = new com.ykskocluk.demo.entity.CoachProfile();
        coach.setUser(coachUser);
        conv.setCoachProfile(coach);
        msg.setConversation(conv);

        when(messageRepository.findById(42L)).thenReturn(Optional.of(msg));

        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.MESSAGE, 42L, "Harassment", "Details here");

        ReportResponse response = reportService.createReport(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.targetType()).isEqualTo(ReportTargetType.MESSAGE);
        assertThat(response.targetId()).isEqualTo(42L);
        assertThat(response.reason()).isEqualTo("Harassment");
        assertThat(response.details()).isEqualTo("Details here");
        assertThat(response.status()).isEqualTo(ReportStatus.OPEN);

        verify(reportRepository).saveAndFlush(argThat(r ->
                r.getReporter() == reporter &&
                r.getTargetType() == ReportTargetType.MESSAGE &&
                r.getTargetId().equals(42L) &&
                r.getReason().equals("Harassment") &&
                r.getDetails().equals("Details here") &&
                r.getStatus() == ReportStatus.OPEN
        ));
    }

    @Test
    void createReport_reporterNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.MESSAGE, 42L, "Harassment", "Details here");

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> reportService.createReport(1L, request));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void listReports_withStatus_callsFindByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Report> page = new PageImpl<>(List.of());

        when(reportRepository.findByStatus(ReportStatus.OPEN, pageable)).thenReturn(page);

        PageResponse<ReportResponse> response = reportService.listReports(ReportStatus.OPEN, pageable);

        assertThat(response).isNotNull();
        verify(reportRepository).findByStatus(ReportStatus.OPEN, pageable);
        verify(reportRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listReports_withoutStatus_callsFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Report> page = new PageImpl<>(List.of());

        when(reportRepository.findAll(pageable)).thenReturn(page);

        PageResponse<ReportResponse> response = reportService.listReports(null, pageable);

        assertThat(response).isNotNull();
        verify(reportRepository).findAll(pageable);
        verify(reportRepository, never()).findByStatus(any(), any());
    }

    @Test
    void createReport_selfReport_throwsBadRequest() {
        User reporter = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(reporter, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.USER, 1L, "Self-report", null);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> reportService.createReport(1L, request));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("CANNOT_REPORT_SELF");
    }

    @Test
    void createReport_userTargetNotFound_throwsNotFound() {
        User reporter = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(reporter, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.existsById(2L)).thenReturn(false);

        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.USER, 2L, "Inappropriate", null);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> reportService.createReport(1L, request));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void createReport_conversationNonParticipant_throwsForbidden() {
        User reporter = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(reporter, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Conversation conv = new Conversation();
        org.springframework.test.util.ReflectionTestUtils.setField(conv, "id", 99L);
        User studentUser = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(studentUser, "id", 2L);
        conv.setStudent(studentUser);

        User coachUser = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(coachUser, "id", 3L);
        com.ykskocluk.demo.entity.CoachProfile coach = new com.ykskocluk.demo.entity.CoachProfile();
        coach.setUser(coachUser);
        conv.setCoachProfile(coach);

        when(conversationRepository.findById(99L)).thenReturn(Optional.of(conv));

        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.CONVERSATION, 99L, "Spam", null);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> reportService.createReport(1L, request));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo("NOT_CONVERSATION_PARTICIPANT");
    }
}
