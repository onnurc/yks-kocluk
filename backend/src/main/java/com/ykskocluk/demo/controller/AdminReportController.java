package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminReportStatusUpdateRequest;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-restricted controller to list and search safety reports.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public PageResponse<ReportResponse> list(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reportService.listReports(status, pageable);
    }

    @PatchMapping("/{reportId}/status")
    public ReportResponse updateStatus(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long reportId,
            @Valid @RequestBody AdminReportStatusUpdateRequest request) {
        return reportService.updateReportStatus(adminUserId, reportId, request.status());
    }
}
