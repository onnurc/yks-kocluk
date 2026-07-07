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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
