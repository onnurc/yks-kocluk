package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.ReportCreateRequest;
import com.ykskocluk.demo.dto.ReportCreationResult;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.service.ReportService;
import com.ykskocluk.demo.security.ratelimit.AuthenticatedActionRateLimitService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for users to report safety or rule violations.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final MailClient mailClient;
    private final AuthenticatedActionRateLimitService actionRateLimit;

    public ReportController(ReportService reportService, MailClient mailClient,
                            AuthenticatedActionRateLimitService actionRateLimit) {
        this.reportService = reportService;
        this.mailClient = mailClient;
        this.actionRateLimit = actionRateLimit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReportCreateRequest request) {
        actionRateLimit.checkReportCreate(userId);
        ReportCreationResult result = reportService.createReport(userId, request);
        try {
            mailClient.sendReportReceived(result.reporterEmail());
        } catch (Exception e) {
            // Best-effort: report is already committed; never let a mail failure surface.
            log.error("Report-received email failed for report {}: {}", result.response().id(), e.getMessage(), e);
        }
        return result.response();
    }
}
