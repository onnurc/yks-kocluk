package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "hasLocalPassword", expression = "java(user.getPasswordHash() != null)")
    UserResponse toResponse(User user);
}
