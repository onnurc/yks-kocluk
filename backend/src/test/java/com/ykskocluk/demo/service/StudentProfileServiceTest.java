package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.StudentProfileUpdateRequest;
import com.ykskocluk.demo.entity.StudentProfile;
import com.ykskocluk.demo.enums.ExamSession;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.mapper.StudentProfileMapper;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTest {

    @Mock StudentProfileRepository studentProfileRepository;
    @Mock UserRepository userRepository;
    @Mock StudentProfileMapper studentProfileMapper;

    StudentProfileService service;

    @BeforeEach
    void setUp() {
        service = new StudentProfileService(studentProfileRepository, userRepository, studentProfileMapper);
    }

    @Test
    void updateOwn_persistsCompleteEducationGoalsAndPreservesSubmittedCity() {
        StudentProfile profile = new StudentProfile();
        profile.setCity("İzmir");
        when(studentProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));

        service.updateOwn(7L, new StudentProfileUpdateRequest(
                "11. Sınıf", "İzmir", 2031, Track.EQUAL_WEIGHT, ExamSession.TYT,
                "Boğaziçi Üniversitesi", "Psikoloji"));

        assertThat(profile.getGradeLevel()).isEqualTo("11. Sınıf");
        assertThat(profile.getCity()).isEqualTo("İzmir");
        assertThat(profile.getExamYear()).isEqualTo(2031);
        assertThat(profile.getYksScoreType()).isEqualTo(Track.EQUAL_WEIGHT);
        assertThat(profile.getExamSession()).isEqualTo(ExamSession.TYT);
        assertThat(profile.getTargetUniversity()).isEqualTo("Boğaziçi Üniversitesi");
        assertThat(profile.getTargetDepartment()).isEqualTo("Psikoloji");
    }
}
