package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.ReportService;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link AdminReportController} enforcing ADMIN authorization.
 */
@WebMvcTest(AdminReportController.class)
@Import(AdminReportControllerTest.MethodSecurityTestConfig.class)
class AdminReportControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReportService reportService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    com.ykskocluk.demo.integration.MailClient mailClient;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListReports() throws Exception {
        when(reportService.listReports(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_cannotListReports_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_cannotListReports_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "5", roles = "ADMIN")
    void admin_canUpdateReportStatus() throws Exception {
        ReportResponse reportResponse = new ReportResponse(100L, 1L, com.ykskocluk.demo.enums.ReportTargetType.USER, 2L, "Reason", null, com.ykskocluk.demo.enums.ReportStatus.REVIEWED, java.time.Instant.now(), java.time.Instant.now(), 5L);
        when(reportService.updateReportStatus(any(), eq(100L), eq(com.ykskocluk.demo.enums.ReportStatus.REVIEWED)))
                .thenReturn(new com.ykskocluk.demo.dto.ReportStatusUpdateResult(reportResponse, "reporter@example.com", true, com.ykskocluk.demo.enums.ReportStatus.REVIEWED));

        mockMvc.perform(patch("/api/v1/admin/reports/100/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(mailClient).sendReportStatusUpdated("reporter@example.com", com.ykskocluk.demo.enums.ReportStatus.REVIEWED);
    }

    @Test
    @WithMockUser(username = "5", roles = "ADMIN")
    void admin_updateReportStatus_noTransition_doesNotSendEmail() throws Exception {
        ReportResponse reportResponse = new ReportResponse(100L, 1L, com.ykskocluk.demo.enums.ReportTargetType.USER, 2L, "Reason", null, com.ykskocluk.demo.enums.ReportStatus.REVIEWED, java.time.Instant.now(), java.time.Instant.now(), 5L);
        when(reportService.updateReportStatus(any(), eq(100L), eq(com.ykskocluk.demo.enums.ReportStatus.REVIEWED)))
                .thenReturn(new com.ykskocluk.demo.dto.ReportStatusUpdateResult(reportResponse, "reporter@example.com", false, com.ykskocluk.demo.enums.ReportStatus.REVIEWED));

        mockMvc.perform(patch("/api/v1/admin/reports/100/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(mailClient, org.mockito.Mockito.never()).sendReportStatusUpdated(any(), any());
    }

    @Test
    @WithMockUser(username = "5", roles = "ADMIN")
    void admin_updateReportStatus_mailThrows_stillSucceeds() throws Exception {
        ReportResponse reportResponse = new ReportResponse(100L, 1L, com.ykskocluk.demo.enums.ReportTargetType.USER, 2L, "Reason", null, com.ykskocluk.demo.enums.ReportStatus.REVIEWED, java.time.Instant.now(), java.time.Instant.now(), 5L);
        when(reportService.updateReportStatus(any(), eq(100L), eq(com.ykskocluk.demo.enums.ReportStatus.REVIEWED)))
                .thenReturn(new com.ykskocluk.demo.dto.ReportStatusUpdateResult(reportResponse, "reporter@example.com", true, com.ykskocluk.demo.enums.ReportStatus.REVIEWED));

        org.mockito.Mockito.doThrow(new RuntimeException("Mail service down"))
                .when(mailClient).sendReportStatusUpdated(any(), any());

        mockMvc.perform(patch("/api/v1/admin/reports/100/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "5", roles = "STUDENT")
    void student_cannotUpdateReportStatus_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/reports/100/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "5", roles = "COACH")
    void coach_cannotUpdateReportStatus_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/reports/100/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isForbidden());
    }
}
