package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminDashboardSummaryResponse;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ykskocluk.demo.dto.AdminUserDirectoryResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import static org.mockito.ArgumentMatchers.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
@Import(AdminDashboardControllerTest.MethodSecurity.class)
class AdminDashboardControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockitoBean AdminDashboardService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanReadSummary() throws Exception {
        when(service.summary()).thenReturn(new AdminDashboardSummaryResponse(4, 3, 2, 1, 5,
                BigDecimal.TEN, BigDecimal.ONE, 6, 7, 8));
        mvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudentCount").value(4))
                .andExpect(jsonPath("$.grossRevenueThisMonth").value(10));
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotReadSummary() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "COACH")
    void coachCannotReadSummary() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void userDirectoryNeverSerializesSecurityInternals() throws Exception {
        when(service.users(isNull(), isNull(), isNull(), any())).thenReturn(new PageResponse<>(List.of(
                new AdminUserDirectoryResponse(1L, "Student", "s@example.com", Role.STUDENT,
                        UserStatus.ACTIVE, true, true, false, Instant.parse("2026-01-01T00:00:00Z"))),
                0, 20, 1, 1, true));

        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("s@example.com"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordVersion").doesNotExist())
                .andExpect(jsonPath("$.content[0].googleSub").doesNotExist());
    }
}
