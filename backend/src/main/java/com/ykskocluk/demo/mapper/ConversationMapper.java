package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.dto.ConversationResponse;
import com.ykskocluk.demo.dto.ConversationObserverResponse;
import com.ykskocluk.demo.entity.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(target = "id", source = "conversation.id")
    @Mapping(target = "coachProfileId", source = "conversation.coachProfile.id")
    @Mapping(target = "coachName", source = "conversation.coachProfile.user.fullName")
    @Mapping(target = "studentName", source = "conversation.student.fullName")
    @Mapping(target = "lastMessage", source = "lastMessage")
    @Mapping(target = "lastMessageAt", source = "lastMessageAt")
    @Mapping(target = "unreadCount", source = "unreadCount")
    @Mapping(target = "counterpartUserId", source = "counterpartUserId")
    @Mapping(target = "counterpartOnline", source = "counterpartOnline")
    @Mapping(target = "observer", expression = "java(platformAdminObserver())")
    ConversationResponse toResponse(Conversation conversation, long unreadCount,
                                    String lastMessage, java.time.Instant lastMessageAt,
                                    Long counterpartUserId, boolean counterpartOnline);

    default ConversationObserverResponse platformAdminObserver() {
        return ConversationObserverResponse.platformAdmin();
    }
}
