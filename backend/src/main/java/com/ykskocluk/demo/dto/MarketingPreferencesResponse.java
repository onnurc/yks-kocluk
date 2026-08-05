package com.ykskocluk.demo.dto;

public record MarketingPreferencesResponse(
        MarketingChannelPreferenceResponse email,
        MarketingChannelPreferenceResponse sms
) { }
