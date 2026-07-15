package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.PackageResponse;
import com.ykskocluk.demo.entity.Package;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PackageMapper {

    PackageResponse toResponse(Package pkg);
}
