package com.ykskocluk.demo.dto;

import java.time.Instant;

public record MarketingChannelPreferenceResponse(
        boolean granted,
        Instant grantedAt,
        Instant withdrawnAt
) { }
