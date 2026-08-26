package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.StudentProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileProvisioningServiceTest {

    @Mock StudentProfileRepository studentProfileRepository;

    StudentProfileProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new StudentProfileProvisioningService(studentProfileRepository);
    }

    @Test
    void ensureForStudent_usesAtomicIdempotentProvisioning() {
        User student = user(Role.STUDENT, 42L);

        service.ensureForStudent(student);
        service.ensureForStudent(student);

        verify(studentProfileRepository, times(2)).createBaselineIfMissing(42L);
        verify(studentProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureForStudent_doesNotProvisionUnrelatedRoles() {
        service.ensureForStudent(user(Role.COACH, 18L));

        verify(studentProfileRepository, never()).createBaselineIfMissing(18L);
    }

    @Test
    void getOrCreateForStudent_returnsTheRealPersistedProfile() {
        StudentProfile profile = new StudentProfile();
        when(studentProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));

        assertThat(service.getOrCreateForStudent(42L)).isSameAs(profile);
        verify(studentProfileRepository).createBaselineIfMissing(42L);
    }

    private User user(Role role, Long id) {
        User user = new User();
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
