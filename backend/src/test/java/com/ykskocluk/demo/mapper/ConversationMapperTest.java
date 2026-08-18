package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationMapperTest {
    @Test void participantResponseAlwaysExposesVisibleReadOnlyAdminObserver() {
        User student = new User(); student.setFullName("Student");
        User coachUser = new User(); coachUser.setFullName("Coach");
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 2L); coach.setUser(coachUser);
        Conversation conversation = new Conversation(); ReflectionTestUtils.setField(conversation, "id", 3L);
        conversation.setStudent(student); conversation.setCoachProfile(coach); conversation.setLastMessageAt(Instant.now());
        var response = new ConversationMapperImpl().toResponse(conversation, 0,
                "Son mesaj", Instant.parse("2026-08-17T10:00:00Z"), 2L, true);
        assertThat(response.lastMessage()).isEqualTo("Son mesaj");
        assertThat(response.counterpartUserId()).isEqualTo(2L);
        assertThat(response.counterpartOnline()).isTrue();
        assertThat(response.observer().type()).isEqualTo("ADMIN");
        assertThat(response.observer().displayName()).isEqualTo("Uniform Akademi Admin");
        assertThat(response.observer().readOnly()).isTrue();
    }
}
