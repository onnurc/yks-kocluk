package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties({R2Properties.class, MediaPolicyProperties.class,
        MediaPublicUrlProperties.class, MediaCleanupProperties.class})
public class StorageConfig {

    @Bean
    MediaConfigurationValidator mediaConfigurationValidator(
            R2Properties r2,
            MediaPublicUrlProperties media,
            Environment environment
    ) {
        return new MediaConfigurationValidator(r2, media, environment);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.r2.enabled", havingValue = "true")
    S3Client r2S3Client(R2Properties p) {
        return S3Client.builder().endpointOverride(URI.create(p.endpoint())).region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKeyId(), p.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.r2.enabled", havingValue = "true")
    S3Presigner r2S3Presigner(R2Properties p) {
        return S3Presigner.builder().endpointOverride(URI.create(p.endpoint())).region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKeyId(), p.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }
}
