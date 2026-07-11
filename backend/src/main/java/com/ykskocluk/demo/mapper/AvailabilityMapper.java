package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.AvailabilityResponse;
import com.ykskocluk.demo.entity.CoachAvailability;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AvailabilityMapper {

    @Mapping(target = "coachProfileId", source = "coachProfile.id")
    AvailabilityResponse toResponse(CoachAvailability availability);

    List<AvailabilityResponse> toResponseList(List<CoachAvailability> availabilities);
}
