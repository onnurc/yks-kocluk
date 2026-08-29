package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.TrialConsultationResponse;

public record AdminTrialConfirmationResult(TrialConsultationResponse trial, String studentEmail,
                                           boolean newlyConfirmed) { }
