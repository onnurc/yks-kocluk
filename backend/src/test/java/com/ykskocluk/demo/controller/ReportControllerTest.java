package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.ReportCreateRequest;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.enums.ReportTargetType;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.ReportService;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link ReportController}.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(username = "1")
    void createReport_authenticated_success() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.USER, 2L, "Harassment", "Details");
        ReportResponse response = new ReportResponse(10L, 1L, ReportTargetType.USER, 2L, "Harassment", "Details", ReportStatus.OPEN, Instant.now(), null, null);

        when(reportService.createReport(any(), eq(request))).thenReturn(new com.ykskocluk.demo.dto.ReportCreationResult(response, "reporter@example.com"));

        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.targetType").value("USER"))
                .andExpect(jsonPath("$.targetId").value(2))
                .andExpect(jsonPath("$.reason").value("Harassment"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        org.mockito.Mockito.verify(mailClient).sendReportReceived("reporter@example.com");
    }

    @Test
    @WithMockUser(username = "1")
    void createReport_mailThrows_stillSucceeds() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.USER, 2L, "Harassment", "Details");
        ReportResponse response = new ReportResponse(10L, 1L, ReportTargetType.USER, 2L, "Harassment", "Details", ReportStatus.OPEN, Instant.now(), null, null);

        when(reportService.createReport(any(), eq(request))).thenReturn(new com.ykskocluk.demo.dto.ReportCreationResult(response, "reporter@example.com"));
        org.mockito.Mockito.doThrow(new RuntimeException("Mail server offline"))
                .when(mailClient).sendReportReceived(any());

        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "1")
    void createReport_failedCreation_doesNotSendMail() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(ReportTargetType.USER, 2L, "Harassment", "Details");

        when(reportService.createReport(any(), eq(request))).thenThrow(new com.ykskocluk.demo.exception.ApiException(HttpStatus.CONFLICT, "DUPLICATE_OPEN_REPORT", "Duplicate"));

        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        org.mockito.Mockito.verify(mailClient, org.mockito.Mockito.never()).sendReportReceived(any());
    }
}
