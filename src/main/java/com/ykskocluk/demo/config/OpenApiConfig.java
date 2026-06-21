package com.ykskocluk.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("YKS Coaching Platform API")
                .description("Backend REST API for the YKS coaching platform.")
                .version("v1"));
    }
}
