package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.service.AdminDashboardService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService service;
    public AdminDashboardController(AdminDashboardService service) { this.service = service; }

    @GetMapping("/dashboard/summary")
    public AdminDashboardSummaryResponse summary() { return service.summary(); }

    @GetMapping("/finance/summary")
    public AdminFinanceSummaryResponse finance(@RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to) {
        return service.finance(from, to);
    }

    @GetMapping("/users")
    public PageResponse<AdminUserDirectoryResponse> users(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.users(role, status, search, pageable);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailResponse user(@PathVariable Long id) { return service.user(id); }

    @GetMapping("/sessions")
    public PageResponse<AdminOperationalSessionResponse> sessions(
            @RequestParam(defaultValue = "ALL") AdminSessionType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.operations(type, status, coachId, studentId, from, to, pageable);
    }
}
