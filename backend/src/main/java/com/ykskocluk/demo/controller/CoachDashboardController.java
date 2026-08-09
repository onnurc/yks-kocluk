package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.service.CoachDashboardService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coach")
@PreAuthorize("hasRole('COACH')")
public class CoachDashboardController {
    private final CoachDashboardService service;
    public CoachDashboardController(CoachDashboardService service) { this.service = service; }

    @GetMapping("/dashboard/summary")
    public CoachDashboardSummaryResponse summary(@AuthenticationPrincipal Long userId) { return service.summary(userId); }

    @GetMapping("/students")
    public PageResponse<CoachStudentResponse> students(@AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "ACTIVE") CoachStudentFilter status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.students(userId, status, pageable);
    }

    @GetMapping("/calendar/sessions")
    public PageResponse<SessionResponse> sessions(@AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) SessionStatus status, @RequestParam(required = false) Long studentId,
            @PageableDefault(size = 30, sort = {"startTime", "id"}) Pageable pageable) {
        return service.sessions(userId, from, to, status, studentId, pageable);
    }

    @GetMapping("/conversations/summary")
    public List<CoachConversationSummaryResponse> conversations(@AuthenticationPrincipal Long userId) {
        return service.conversationSummaries(userId);
    }

    @GetMapping("/dashboard/packages")
    public List<CoachPackageSummaryResponse> packages(@AuthenticationPrincipal Long userId) {
        return service.packages(userId);
    }
}
