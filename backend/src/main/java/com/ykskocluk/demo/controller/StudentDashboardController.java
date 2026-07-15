package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.StudentDashboardResponse;
import com.ykskocluk.demo.service.StudentDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    public StudentDashboardController(StudentDashboardService studentDashboardService) {
        this.studentDashboardService = studentDashboardService;
    }

    @GetMapping("/me/dashboard")
    public StudentDashboardResponse getDashboard(@AuthenticationPrincipal Long studentUserId) {
        return studentDashboardService.getDashboardData(studentUserId);
    }
}
