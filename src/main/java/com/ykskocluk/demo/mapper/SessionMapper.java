package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.SessionResponse;
import com.ykskocluk.demo.entity.Session;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "coachProfileId", source = "coachProfile.id")
    @Mapping(target = "coachName", source = "coachProfile.user.fullName")
    @Mapping(target = "studentName", source = "student.fullName")
    @Mapping(target = "availabilityId", source = "availability.id")
    SessionResponse toResponse(Session session);

    List<SessionResponse> toResponseList(List<Session> sessions);
}
