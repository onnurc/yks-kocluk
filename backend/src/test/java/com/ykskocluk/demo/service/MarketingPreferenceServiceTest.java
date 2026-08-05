package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.MarketingPreferencesUpdateRequest;
import com.ykskocluk.demo.entity.MarketingPreference;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.MarketingChannel;
import com.ykskocluk.demo.enums.MarketingPreferenceStatus;
import com.ykskocluk.demo.repository.MarketingPreferenceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingPreferenceServiceTest {
    @Mock MarketingPreferenceRepository repository;
    @Mock UserRepository userRepository;
    MarketingPreferenceService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new MarketingPreferenceService(repository, userRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void existingUserWithoutRows_defaultsToNotGranted() {
        when(repository.findByUserId(7L)).thenReturn(List.of());
        var response = service.get(7L);
        assertThat(response.email().granted()).isFalse();
        assertThat(response.sms().granted()).isFalse();
    }

    @Test
    void channelsCanBeGrantedAndWithdrawnIndependently() {
        when(repository.findByUserIdAndChannel(7L, MarketingChannel.EMAIL)).thenReturn(Optional.empty());
        when(repository.findByUserIdAndChannel(7L, MarketingChannel.SMS)).thenReturn(Optional.empty());

        var response = service.update(7L, new MarketingPreferencesUpdateRequest(true, false));

        assertThat(response.email().granted()).isTrue();
        assertThat(response.sms().granted()).isFalse();
        assertThat(response.sms().withdrawnAt()).isNotNull();
        verify(repository, times(2)).save(any());
    }

    @Test
    void repeatedIdenticalUpdate_isIdempotent() {
        MarketingPreference granted = preference(MarketingChannel.EMAIL, MarketingPreferenceStatus.GRANTED);
        when(repository.findByUserIdAndChannel(7L, MarketingChannel.EMAIL)).thenReturn(Optional.of(granted));

        var response = service.update(7L, new MarketingPreferencesUpdateRequest(true, null));

        assertThat(response.email().granted()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void registrationPersistsOnlyExplicitOptInChannels() {
        when(repository.findByUserIdAndChannel(7L, MarketingChannel.EMAIL)).thenReturn(Optional.empty());

        service.recordRegistrationPreferences(user, true, false);

        verify(repository).save(argThat(preference -> preference.getChannel() == MarketingChannel.EMAIL
                && preference.getStatus() == MarketingPreferenceStatus.GRANTED
                && "REGISTRATION".equals(preference.getSource())));
        verify(repository, never()).findByUserIdAndChannel(7L, MarketingChannel.SMS);
    }

    private MarketingPreference preference(MarketingChannel channel, MarketingPreferenceStatus status) {
        MarketingPreference preference = new MarketingPreference();
        preference.setUser(user);
        preference.setChannel(channel);
        preference.setStatus(status);
        if (status == MarketingPreferenceStatus.GRANTED) preference.setGrantedAt(java.time.Instant.now());
        return preference;
    }
}
