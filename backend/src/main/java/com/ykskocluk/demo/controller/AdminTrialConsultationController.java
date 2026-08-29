package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminTrialConfirmRequest;
import com.ykskocluk.demo.dto.TrialConsultationResponse;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.service.AdminTrialConfirmationResult;
import com.ykskocluk.demo.service.TrialConsultationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/trial-consultations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrialConsultationController {
    private static final Logger log = LoggerFactory.getLogger(AdminTrialConsultationController.class);

    private final TrialConsultationService service;
    private final MailClient mailClient;

    public AdminTrialConsultationController(TrialConsultationService service, MailClient mailClient) {
        this.service = service;
        this.mailClient = mailClient;
    }

    @PostMapping("/{id}/confirm")
    public TrialConsultationResponse confirm(@AuthenticationPrincipal Long adminUserId,
                                             @PathVariable Long id,
                                             @Valid @RequestBody AdminTrialConfirmRequest request) {
        AdminTrialConfirmationResult result = service.confirmByAdmin(adminUserId, id, request.meetingUrl());
        if (result.newlyConfirmed()) {
            try {
                mailClient.sendTrialConsultationConfirmed(result.studentEmail(), result.trial().coachName(),
                        result.trial().startsAt(), result.trial().meetingUrl());
            } catch (Exception ex) {
                log.error("Trial confirmation email failed for trial {}; exceptionType={}", id,
                        ex.getClass().getName());
            }
        }
        return result.trial();
    }
}
