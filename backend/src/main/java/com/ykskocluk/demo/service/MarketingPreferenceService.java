package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.MarketingPreference;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.MarketingChannel;
import com.ykskocluk.demo.enums.MarketingPreferenceStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.MarketingPreferenceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;

@Service
public class MarketingPreferenceService {
    private final MarketingPreferenceRepository repository;
    private final UserRepository userRepository;

    public MarketingPreferenceService(MarketingPreferenceRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MarketingPreferencesResponse get(Long userId) {
        EnumMap<MarketingChannel, MarketingPreference> preferences = new EnumMap<>(MarketingChannel.class);
        repository.findByUserId(userId).forEach(p -> preferences.put(p.getChannel(), p));
        return response(preferences.get(MarketingChannel.EMAIL), preferences.get(MarketingChannel.SMS));
    }

    @Transactional
    public MarketingPreferencesResponse update(Long userId, MarketingPreferencesUpdateRequest request) {
        if (request == null || (request.email() == null && request.sms() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MARKETING_PREFERENCE_UPDATE_EMPTY",
                    "En az bir iletişim tercihi gönderilmelidir");
        }
        User user = requireUser(userId);
        MarketingPreference email = updateChannel(user, MarketingChannel.EMAIL, request.email(), "SELF_SERVICE");
        MarketingPreference sms = updateChannel(user, MarketingChannel.SMS, request.sms(), "SELF_SERVICE");
        return response(email, sms);
    }

    public void recordRegistrationPreferences(User user, boolean email, boolean sms) {
        if (email) updateChannel(user, MarketingChannel.EMAIL, true, "REGISTRATION");
        if (sms) updateChannel(user, MarketingChannel.SMS, true, "REGISTRATION");
    }

    public void withdrawAll(User user, Instant now) {
        for (MarketingPreference preference : repository.findByUserId(user.getId())) {
            if (preference.getStatus() != MarketingPreferenceStatus.WITHDRAWN) {
                preference.setStatus(MarketingPreferenceStatus.WITHDRAWN);
                preference.setWithdrawnAt(now);
                preference.setSource("ACCOUNT_DELETION");
            }
        }
    }

    private MarketingPreference updateChannel(User user, MarketingChannel channel, Boolean granted, String source) {
        MarketingPreference existing = repository.findByUserIdAndChannel(user.getId(), channel).orElse(null);
        if (granted == null) return existing;
        MarketingPreferenceStatus target = granted ? MarketingPreferenceStatus.GRANTED : MarketingPreferenceStatus.WITHDRAWN;
        if (existing != null && existing.getStatus() == target) return existing;
        Instant now = Instant.now();
        MarketingPreference preference = existing != null ? existing : new MarketingPreference();
        if (existing == null) {
            preference.setUser(user);
            preference.setChannel(channel);
        }
        preference.setStatus(target);
        preference.setSource(source);
        if (granted) {
            preference.setGrantedAt(now);
            preference.setWithdrawnAt(null);
        } else {
            preference.setWithdrawnAt(now);
        }
        return repository.save(preference);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
    }

    private MarketingPreferencesResponse response(MarketingPreference email, MarketingPreference sms) {
        return new MarketingPreferencesResponse(channelResponse(email), channelResponse(sms));
    }

    private MarketingChannelPreferenceResponse channelResponse(MarketingPreference preference) {
        if (preference == null) return new MarketingChannelPreferenceResponse(false, null, null);
        return new MarketingChannelPreferenceResponse(
                preference.getStatus() == MarketingPreferenceStatus.GRANTED,
                preference.getGrantedAt(), preference.getWithdrawnAt());
    }
}
