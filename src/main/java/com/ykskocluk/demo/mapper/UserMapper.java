package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
