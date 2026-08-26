package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.StudentProfileUpdateRequest;
import com.ykskocluk.demo.dto.StudentProfileResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTest {

    @Mock StudentProfileRepository studentProfileRepository;
    @Mock UserRepository userRepository;
    @Mock StudentProfileMapper studentProfileMapper;
    @Mock StudentProfileProvisioningService studentProfileProvisioningService;

    StudentProfileService service;

    @BeforeEach
    void setUp() {
        service = new StudentProfileService(studentProfileRepository, userRepository, studentProfileMapper,
                studentProfileProvisioningService);
    }

    @Test
    void updateOwn_persistsCompleteEducationGoalsAndPreservesSubmittedCity() {
        StudentProfile profile = new StudentProfile();
        profile.setCity("İzmir");
        when(studentProfileProvisioningService.getOrCreateForStudent(7L)).thenReturn(profile);

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

    @Test
    void getOwn_repairsMissingHistoricalStudentThroughProvisioning() {
        StudentProfile profile = new StudentProfile();
        StudentProfileResponse response = new StudentProfileResponse(
                12L, 7L, "Google Student", "google@example.com",
                null, null, null, null, null, null, null, null, null);
        when(studentProfileProvisioningService.getOrCreateForStudent(7L)).thenReturn(profile);
        when(studentProfileMapper.toResponse(profile)).thenReturn(response);

        assertThat(service.getOwn(7L)).isSameAs(response);
        verify(studentProfileProvisioningService).getOrCreateForStudent(7L);
    }
}
