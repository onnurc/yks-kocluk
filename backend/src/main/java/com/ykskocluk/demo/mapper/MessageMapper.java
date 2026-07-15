package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", source = "sender.fullName")
    @Mapping(target = "sentAt", source = "createdAt")
    MessageResponse toResponse(Message message);
}
