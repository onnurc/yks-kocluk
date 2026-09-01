package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.WebSocketSessionsInvalidatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for administrative and standard user profile actions.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SuspendResponse suspendUser(Long adminUserId, Long userId, String reason) {
        if (adminUserId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_SELF", "Kendinizi askıya alamazsınız");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        if (user.getRole() == com.ykskocluk.demo.enums.Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_ADMIN", "Yöneticileri askıya alamazsınız");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.CONFLICT, "DELETED_USER_CANNOT_BE_SUSPENDED",
                    "Silinmiş kullanıcı durumu değiştirilemez");
        }

        user.setStatus(UserStatus.SUSPENDED);
        user.setSuspensionReason(reason);
        userRepository.saveAndFlush(user);
        eventPublisher.publishEvent(new WebSocketSessionsInvalidatedEvent(userId));

        return new SuspendResponse(
                user.getId(),
                user.getStatus().name(),
                user.getSuspensionReason(),
                user.getEmail()
        );
    }

    @Transactional
    public SuspendResponse unsuspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.CONFLICT, "DELETED_USER_CANNOT_BE_REACTIVATED",
                    "Silinmiş kullanıcı yeniden etkinleştirilemez");
        }
        if (user.getRole() == com.ykskocluk.demo.enums.Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_UNSUSPEND_ADMIN",
                    "Yönetici durumu bu endpoint üzerinden değiştirilemez");
        }
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_NOT_SUSPENDED", "Kullanıcı askıya alınmış değil");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setSuspensionReason(null);
        userRepository.saveAndFlush(user);
        return new SuspendResponse(user.getId(), user.getStatus().name(), null, user.getEmail());
    }
}
