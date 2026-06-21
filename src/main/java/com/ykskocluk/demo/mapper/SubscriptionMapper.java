package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "coachProfileId", source = "coachProfile.id")
    @Mapping(target = "coachName", source = "coachProfile.user.fullName")
    @Mapping(target = "packageId", source = "pkg.id")
    @Mapping(target = "packageName", source = "pkg.name")
    @Mapping(target = "weeklySessions", source = "pkg.weeklySessions")
    SubscriptionResponse toResponse(Subscription subscription);
}
