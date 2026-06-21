package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.StudentProfileResponse;
import com.ykskocluk.demo.entity.StudentProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudentProfileMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    StudentProfileResponse toResponse(StudentProfile profile);
}
