package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.DashboardPayment;
import com.ykskocluk.demo.dto.DashboardSubscription;
import com.ykskocluk.demo.dto.StudentDashboardResponse;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.UserMapper;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDashboardService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final UserMapper userMapper;

    public StudentDashboardService(UserRepository userRepository,
                                   SubscriptionRepository subscriptionRepository,
                                   PaymentRepository paymentRepository,
                                   UserMapper userMapper) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse getDashboardData(Long studentUserId) {
        User user = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        UserResponse userResponse = userMapper.toResponse(user);

        Subscription latestSub = subscriptionRepository.findCurrentCoachRelationship(studentUserId)
                .orElseGet(() -> subscriptionRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentUserId)
                        .orElse(null));
        if (latestSub == null) {
            return new StudentDashboardResponse(userResponse, null, null);
        }
        DashboardSubscription dashboardSubscription = new DashboardSubscription(
                latestSub.getId(),
                latestSub.getStatus(),
                latestSub.getCoachProfile().getId(),
                latestSub.getCoachProfile().getUser().getFullName(),
                latestSub.getPkg().getId(),
                latestSub.getPkg().getName(),
                latestSub.getStartAt(),
                latestSub.getEndAt(),
                latestSub.isAutoRenew(),
                latestSub.getCancelledAt(),
                latestSub.getTerminationReason()
        );

        Payment latestPayment = paymentRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(latestSub.getId())
                .orElse(null);
        DashboardPayment dashboardPayment = null;
        if (latestPayment != null) {
            dashboardPayment = new DashboardPayment(
                    latestPayment.getId(),
                    latestPayment.getStatus(),
                    latestPayment.getAmount(),
                    latestPayment.getCreatedAt()
            );
        }

        return new StudentDashboardResponse(userResponse, dashboardSubscription, dashboardPayment);
    }
}
