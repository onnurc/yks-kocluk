package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.service.MediaAssetUrlResolver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring", uses = MediaAssetUrlResolver.class)
public interface CoachProfileMapper {

    @Mapping(target = "userId", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "universityId", source = "profile.university.id")
    @Mapping(target = "universityName", source = "profile.university.name")
    @Mapping(target = "tracks", source = "tracks")
    @Mapping(target = "profileImageUrl", source = "profile.profileImageAsset", qualifiedByName = "publicMediaUrl")
    @Mapping(target = "introVideoUrl", source = "profile.introVideoAsset", qualifiedByName = "publicMediaUrl")
    CoachProfileResponse toResponse(CoachProfile profile, Set<Track> tracks);
}
