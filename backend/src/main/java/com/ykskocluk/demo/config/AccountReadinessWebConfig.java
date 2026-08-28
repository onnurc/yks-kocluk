package com.ykskocluk.demo.config;

import com.ykskocluk.demo.security.AccountReadinessInterceptor;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.AccountReadinessService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnBean(AccountReadinessService.class)
public class AccountReadinessWebConfig implements WebMvcConfigurer {

    private final AccountReadinessInterceptor readinessInterceptor;

    public AccountReadinessWebConfig(UserRepository userRepository,
                                     AccountReadinessService readinessService) {
        this.readinessInterceptor = new AccountReadinessInterceptor(userRepository, readinessService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(readinessInterceptor).addPathPatterns("/api/v1/**");
    }
}
