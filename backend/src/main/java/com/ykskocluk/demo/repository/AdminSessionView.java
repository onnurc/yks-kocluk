package com.ykskocluk.demo.repository;

import java.time.Instant;

public interface AdminSessionView {
    Long getId();
    String getType();
    Long getCoachProfileId();
    Long getCoachUserId();
    String getCoachName();
    Long getStudentId();
    String getStudentName();
    Instant getStartsAt();
    Instant getEndsAt();
    String getStatus();
    Long getSubscriptionId();
}
