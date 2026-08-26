package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachYoutubeIntroServiceTest {
    @Mock CoachProfileRepository coaches;
    @Mock UserRepository users;
    CoachYoutubeIntroService service;

    @BeforeEach
    void setUp() {
        service = new CoachYoutubeIntroService(coaches, users, new YoutubeVideoService());
    }

    @Test
    void adminStoresOnlyNormalizedVideoIdAndReturnsGeneratedEmbed() {
        User admin = user(1L, Role.ADMIN);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", 9L);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(coaches.findById(9L)).thenReturn(Optional.of(coach));

        var response = service.set(1L, 9L, "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(coach.getIntroYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.embedUrl()).isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
    }

    @Test
    void coachCannotManageTheOfficialChannelReference() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, Role.COACH)));

        assertThatThrownBy(() -> service.set(7L, 9L, "dQw4w9WgXcQ"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode()).isEqualTo("ADMIN_REQUIRED");
        verify(coaches, never()).findById(9L);
    }

    private User user(Long id, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }
}
