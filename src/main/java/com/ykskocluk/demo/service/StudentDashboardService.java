package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.DashboardPayment;
import com.ykskocluk.demo.dto.DashboardSubscription;
import com.ykskocluk.demo.dto.StudentDashboardResponse;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentDashboardService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public StudentDashboardService(UserRepository userRepository,
                                   SubscriptionRepository subscriptionRepository,
                                   PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse getDashboardData(Long studentUserId) {
        User user = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );

        List<Subscription> subscriptions = subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentUserId);
        if (subscriptions.isEmpty()) {
            return new StudentDashboardResponse(userResponse, null, null);
        }

        Subscription latestSub = subscriptions.get(0);
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

        List<Payment> payments = paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(latestSub.getId());
        DashboardPayment dashboardPayment = null;
        if (!payments.isEmpty()) {
            Payment latestPayment = payments.get(0);
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
