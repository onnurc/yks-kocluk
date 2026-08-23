package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.StudentProfileCreateRequest;
import com.ykskocluk.demo.dto.StudentProfileResponse;
import com.ykskocluk.demo.dto.StudentProfileUpdateRequest;
import com.ykskocluk.demo.entity.StudentProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.StudentProfileMapper;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StudentProfileMapper studentProfileMapper;

    public StudentProfileService(StudentProfileRepository studentProfileRepository,
                                 UserRepository userRepository,
                                 StudentProfileMapper studentProfileMapper) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.studentProfileMapper = studentProfileMapper;
    }

    @Transactional
    public StudentProfileResponse createOwn(Long userId, StudentProfileCreateRequest request) {
        if (studentProfileRepository.existsByUserId(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_ALREADY_EXISTS",
                    "Öğrenci profiliniz zaten mevcut");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setGradeLevel(request.gradeLevel());
        profile.setCity(request.city());
        studentProfileRepository.save(profile);
        return studentProfileMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getOwn(Long userId) {
        return studentProfileMapper.toResponse(requireOwnProfile(userId));
    }

    @Transactional
    public StudentProfileResponse updateOwn(Long userId, StudentProfileUpdateRequest request) {
        StudentProfile profile = requireOwnProfile(userId);
        profile.setGradeLevel(request.gradeLevel());
        profile.setCity(request.city());
        profile.setExamYear(request.examYear());
        profile.setYksScoreType(request.yksScoreType());
        profile.setExamSession(request.examSession());
        profile.setTargetUniversity(request.targetUniversity());
        profile.setTargetDepartment(request.targetDepartment());
        return studentProfileMapper.toResponse(profile);
    }

    private StudentProfile requireOwnProfile(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Öğrenci profili bulunamadı"));
    }
}
