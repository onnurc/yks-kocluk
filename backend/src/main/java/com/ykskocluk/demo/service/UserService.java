package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for administrative and standard user profile actions.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        user.setStatus(UserStatus.SUSPENDED);
        user.setSuspensionReason(reason);
        userRepository.saveAndFlush(user);

        return new SuspendResponse(
                user.getId(),
                user.getStatus().name(),
                user.getSuspensionReason(),
                user.getEmail()
        );
    }
}
