package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.StudentProfileResponse;
import com.ykskocluk.demo.entity.StudentProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.ykskocluk.demo.service.MediaAssetUrlResolver;

@Mapper(componentModel = "spring", uses = MediaAssetUrlResolver.class)
public interface StudentProfileMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "profileImageUrl", source = "profileImageAsset", qualifiedByName = "publicMediaUrl")
    StudentProfileResponse toResponse(StudentProfile profile);
}
