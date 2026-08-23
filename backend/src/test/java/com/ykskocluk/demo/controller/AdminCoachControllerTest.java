package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminCoachDirectoryResponse;
import com.ykskocluk.demo.dto.CoachStudentResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.CoachStudentFilter;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AdminDashboardService;
import com.ykskocluk.demo.service.CoachDashboardService;
import com.ykskocluk.demo.service.CoachProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCoachController.class)
@Import(AdminCoachControllerTest.MethodSecurity.class)
class AdminCoachControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockitoBean CoachProfileService coachProfileService;
    @MockitoBean AdminDashboardService adminDashboardService;
    @MockitoBean CoachDashboardService coachDashboardService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanReadCoachActiveStudentsThroughExistingCoachReadModel() throws Exception {
        when(adminDashboardService.coach(7L)).thenReturn(new AdminCoachDirectoryResponse(
                7L, 70L, 7L, "Derya Koç", "derya@example.com", CoachProfileStatus.APPROVED,
                CoachProfileStatus.APPROVED, UserStatus.ACTIVE, null, "ODTÜ", "Fizik", true,
                Instant.parse("2026-01-01T00:00:00Z")));
        when(coachDashboardService.students(eq(70L), eq(CoachStudentFilter.ACTIVE), any())).thenReturn(
                new PageResponse<>(List.of(new CoachStudentResponse(22L, "Ece Öğrenci", 2L,
                        "Mentorluk Paketi", SubscriptionStatus.ACTIVE,
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                        1, 1, 3L, null)), 0, 20, 1, 1, true));

        mvc.perform(get("/api/v1/admin/coaches/7/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Ece Öğrenci"))
                .andExpect(jsonPath("$.content[0].packageName").value("Mentorluk Paketi"));
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotReadCoachActiveStudents() throws Exception {
        mvc.perform(get("/api/v1/admin/coaches/7/students")).andExpect(status().isForbidden());
    }
}
