package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.CoachDetailResponse;
import com.ykskocluk.demo.dto.CoachSummaryResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.service.MediaAssetUrlResolver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring", uses = MediaAssetUrlResolver.class)
public interface CoachSearchMapper {

    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "universityName", source = "profile.university.name")
    @Mapping(target = "tracks", source = "tracks")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "totalSessions", source = "totalSessions")
    @Mapping(target = "acceptingNewStudents",
            expression = "java(profile.getActiveStudentCount() < profile.getMaxStudentCapacity())")
    @Mapping(target = "profileImageUrl", source = "profile.profileImageAsset", qualifiedByName = "publicMediaUrl")
    @Mapping(target = "introVideoUrl", source = "profile.introVideoAsset", qualifiedByName = "publicMediaUrl")
    CoachSummaryResponse toSummary(CoachProfile profile, Set<Track> tracks, Double rating, int totalSessions);

    @Mapping(target = "userId", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "universityName", source = "profile.university.name")
    @Mapping(target = "tracks", source = "tracks")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "totalSessions", source = "totalSessions")
    @Mapping(target = "acceptingNewStudents",
            expression = "java(profile.getActiveStudentCount() < profile.getMaxStudentCapacity())")
    @Mapping(target = "profileImageUrl", source = "profile.profileImageAsset", qualifiedByName = "publicMediaUrl")
    @Mapping(target = "introVideoUrl", source = "profile.introVideoAsset", qualifiedByName = "publicMediaUrl")
    CoachDetailResponse toDetail(CoachProfile profile, Set<Track> tracks, Double rating, int totalSessions);
}
