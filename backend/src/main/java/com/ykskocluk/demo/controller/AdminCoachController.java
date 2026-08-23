package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.RejectRequest;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.service.CoachProfileService;
import com.ykskocluk.demo.service.AdminDashboardService;
import com.ykskocluk.demo.service.CoachDashboardService;
import com.ykskocluk.demo.dto.CoachStudentResponse;
import com.ykskocluk.demo.dto.AdminCoachDirectoryResponse;
import com.ykskocluk.demo.enums.AdminCoachFilter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coaches")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoachController {

    private final CoachProfileService coachProfileService;
    private final AdminDashboardService adminDashboardService;
    private final CoachDashboardService coachDashboardService;

    public AdminCoachController(CoachProfileService coachProfileService,
                                AdminDashboardService adminDashboardService,
                                CoachDashboardService coachDashboardService) {
        this.coachProfileService = coachProfileService;
        this.adminDashboardService = adminDashboardService;
        this.coachDashboardService = coachDashboardService;
    }

    @GetMapping
    public PageResponse<AdminCoachDirectoryResponse> list(
            @RequestParam(defaultValue = "PENDING") AdminCoachFilter status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminDashboardService.coaches(status, search, pageable);
    }

    @GetMapping("/{id}")
    public AdminCoachDirectoryResponse detail(@PathVariable Long id) {
        return adminDashboardService.coach(id);
    }

    @GetMapping("/{id}/students")
    public PageResponse<CoachStudentResponse> activeStudents(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AdminCoachDirectoryResponse coach = adminDashboardService.coach(id);
        return coachDashboardService.students(coach.userId(), com.ykskocluk.demo.enums.CoachStudentFilter.ACTIVE, pageable);
    }

    @PostMapping("/{id}/approve")
    public CoachProfileResponse approve(@PathVariable Long id) {
        return coachProfileService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public CoachProfileResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return coachProfileService.reject(id, request.reason());
    }
}
