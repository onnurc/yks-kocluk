package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.UniversityResponse;
import com.ykskocluk.demo.entity.University;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UniversityMapper {

    UniversityResponse toResponse(University university);
}
