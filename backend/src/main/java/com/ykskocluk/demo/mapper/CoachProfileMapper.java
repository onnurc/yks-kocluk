package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.Track;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface CoachProfileMapper {

    @Mapping(target = "userId", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "universityId", source = "profile.university.id")
    @Mapping(target = "universityName", source = "profile.university.name")
    @Mapping(target = "tracks", source = "tracks")
    CoachProfileResponse toResponse(CoachProfile profile, Set<Track> tracks);
}
