package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.ReportCreateRequest;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.entity.Report;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ReportRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for submitting safety reports and retrieving reports (admin).
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReportResponse createReport(Long reporterUserId, ReportCreateRequest request) {
        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setDetails(request.details());
        report.setStatus(ReportStatus.OPEN);

        reportRepository.saveAndFlush(report);

        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listReports(ReportStatus status, Pageable pageable) {
        Page<Report> page;
        if (status != null) {
            page = reportRepository.findByStatus(status, pageable);
        } else {
            page = reportRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getReporter().getId(),
                r.getTargetType(),
                r.getTargetId(),
                r.getReason(),
                r.getDetails(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getReviewedAt(),
                r.getReviewedBy() != null ? r.getReviewedBy().getId() : null
        );
    }
}
