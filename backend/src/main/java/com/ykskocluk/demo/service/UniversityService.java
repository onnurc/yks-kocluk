package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.UniversityCreateRequest;
import com.ykskocluk.demo.dto.UniversityResponse;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.UniversityMapper;
import com.ykskocluk.demo.repository.UniversityRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UniversityService {

    private final UniversityRepository universityRepository;
    private final UniversityMapper universityMapper;

    public UniversityService(UniversityRepository universityRepository, UniversityMapper universityMapper) {
        this.universityRepository = universityRepository;
        this.universityMapper = universityMapper;
    }

    @Transactional(readOnly = true)
    public List<UniversityResponse> list() {
        return universityRepository.findAll(Sort.by("name")).stream()
                .map(universityMapper::toResponse)
                .toList();
    }

    @Transactional
    public UniversityResponse create(UniversityCreateRequest request) {
        if (universityRepository.existsByName(request.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIVERSITY_ALREADY_EXISTS",
                    "Bu üniversite zaten kayıtlı");
        }
        University university = new University();
        university.setName(request.name());
        university.setCity(request.city());
        universityRepository.save(university);
        return universityMapper.toResponse(university);
    }
}
