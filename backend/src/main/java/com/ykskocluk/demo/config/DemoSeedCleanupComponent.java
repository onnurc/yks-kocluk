package com.ykskocluk.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoSeedCleanupComponent implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedCleanupComponent.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean demoSeedEnabled;

    public DemoSeedCleanupComponent(JdbcTemplate jdbcTemplate, 
                                     @Value("${app.demo-seed-enabled:false}") boolean demoSeedEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    @Transactional
    public void afterSingletonsInstantiated() {
        if (!demoSeedEnabled) {
            log.info("[DemoSeedCleanupComponent] app.demo-seed-enabled is false. Cleaning up demo seed records from the database...");
            cleanupDemoSeeds();
        } else {
            log.info("[DemoSeedCleanupComponent] app.demo-seed-enabled is true. Keeping demo seed records.");
        }
    }

    private void cleanupDemoSeeds() {
        List<String> demoEmails = List.of(
            "admin.demo@example.com",
            "student.demo@example.com",
            "coach.demo@example.com",
            "suspended.demo@example.com",
            "student.pending.demo@example.com",
            "student.active.demo@example.com"
        );

        String emailList = "'" + String.join("','", demoEmails) + "'";

        try {
            // 1. Delete admin conversation access logs associated with demo admins or conversations
            jdbcTemplate.execute(
                "DELETE FROM admin_conversation_access_logs WHERE admin_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR conversation_id IN (" +
                "    SELECT id FROM conversations WHERE student_user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    ) OR coach_profile_id IN (" +
                "        SELECT id FROM coach_profiles WHERE user_id IN (" +
                "            SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "        )" +
                "    )" +
                ")"
            );

            // 2. Delete messages associated with demo users' conversations or sent by demo users
            jdbcTemplate.execute(
                "DELETE FROM messages WHERE conversation_id IN (" +
                "    SELECT id FROM conversations WHERE student_user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    ) OR coach_profile_id IN (" +
                "        SELECT id FROM coach_profiles WHERE user_id IN (" +
                "            SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "        )" +
                "    )" +
                ") OR sender_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ")"
            );

            // 3. Delete conversations associated with demo users
            jdbcTemplate.execute(
                "DELETE FROM conversations WHERE student_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR coach_profile_id IN (" +
                "    SELECT id FROM coach_profiles WHERE user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    )" +
                ")"
            );

            // 4. Delete sessions associated with demo students or demo coaches
            jdbcTemplate.execute(
                "DELETE FROM sessions WHERE student_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR coach_profile_id IN (" +
                "    SELECT id FROM coach_profiles WHERE user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    )" +
                ")"
            );

            // 5. Delete coach availabilities associated with demo coaches
            jdbcTemplate.execute(
                "DELETE FROM coach_availabilities WHERE coach_profile_id IN (" +
                "    SELECT id FROM coach_profiles WHERE user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    )" +
                ")"
            );

            // 6. Delete payments associated with demo users' subscriptions
            jdbcTemplate.execute(
                "DELETE FROM payments WHERE subscription_id IN (" +
                "    SELECT id FROM subscriptions WHERE student_user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    ) OR coach_profile_id IN (" +
                "        SELECT id FROM coach_profiles WHERE user_id IN (" +
                "            SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "        )" +
                "    )" +
                ")"
            );

            // 7. Delete subscriptions associated with demo users
            jdbcTemplate.execute(
                "DELETE FROM subscriptions WHERE student_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR coach_profile_id IN (" +
                "    SELECT id FROM coach_profiles WHERE user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    )" +
                ")"
            );

            // 8. Delete reports associated with demo users
            jdbcTemplate.execute(
                "DELETE FROM reports WHERE reporter_user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR reviewed_by_admin_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ") OR (target_type = 'USER' AND target_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ")) OR (target_type = 'COACH' AND target_id IN (" +
                "    SELECT id FROM coach_profiles WHERE user_id IN (" +
                "        SELECT id FROM users WHERE email IN (" + emailList + ")" +
                "    )" +
                "))"
            );

            // 9. Delete consent records associated with demo users
            jdbcTemplate.execute(
                "DELETE FROM consent_records WHERE user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ")"
            );

            // 10. Delete coach profiles associated with demo users
            jdbcTemplate.execute(
                "DELETE FROM coach_profiles WHERE user_id IN (" +
                "    SELECT id FROM users WHERE email IN (" + emailList + ")" +
                ")"
            );

            // 11. Delete demo users
            int deletedUsersCount = jdbcTemplate.update(
                "DELETE FROM users WHERE email IN (" + emailList + ")"
            );

            log.info("[DemoSeedCleanupComponent] Cleaned up {} demo users and their associated records.", deletedUsersCount);
        } catch (Exception e) {
            log.error("[DemoSeedCleanupComponent] Failed to clean up demo seed records from the database", e);
            throw e;
        }
    }
}
