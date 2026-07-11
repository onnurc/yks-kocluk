package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.ConversationResponse;
import com.ykskocluk.demo.entity.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(target = "id", source = "conversation.id")
    @Mapping(target = "coachProfileId", source = "conversation.coachProfile.id")
    @Mapping(target = "coachName", source = "conversation.coachProfile.user.fullName")
    @Mapping(target = "studentName", source = "conversation.student.fullName")
    @Mapping(target = "lastMessageAt", source = "conversation.lastMessageAt")
    @Mapping(target = "unreadCount", source = "unreadCount")
    ConversationResponse toResponse(Conversation conversation, long unreadCount);
}
