package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.StudentProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileProvisioningService {

    private final StudentProfileRepository studentProfileRepository;

    public StudentProfileProvisioningService(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    @Transactional
    public void ensureForStudent(User user) {
        if (user.getRole() == Role.STUDENT) {
            studentProfileRepository.createBaselineIfMissing(user.getId());
        }
    }

    @Transactional
    public StudentProfile getOrCreateForStudent(Long userId) {
        studentProfileRepository.createBaselineIfMissing(userId);
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Öğrenci profili bulunamadı"));
    }
}
