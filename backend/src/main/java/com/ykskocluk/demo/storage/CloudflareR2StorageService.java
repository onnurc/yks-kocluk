package com.ykskocluk.demo.storage;

import com.ykskocluk.demo.config.R2Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.r2.enabled", havingValue = "true")
public class CloudflareR2StorageService implements StorageService {
    private final R2Properties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public CloudflareR2StorageService(R2Properties properties, S3Client client, S3Presigner presigner) {
        this.properties = properties;
        this.client = client;
        this.presigner = presigner;
    }

    @Override
    public UploadTarget createPresignedUpload(String key, String contentType, long sizeBytes) {
        Duration duration = Duration.ofMinutes(properties.uploadUrlExpirationMinutes());
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                .contentType(contentType).contentLength(sizeBytes).build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(duration).putObjectRequest(objectRequest).build());
        return new UploadTarget(signed.url().toString(), Instant.now().plus(duration),
                Map.of("Content-Type", contentType, "Content-Length", Long.toString(sizeBytes)));
    }

    @Override
    public StoredObjectMetadata headObject(String key) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return new StoredObjectMetadata(true, response.contentType(), response.contentLength());
        } catch (NoSuchKeyException e) {
            return new StoredObjectMetadata(false, null, 0);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return new StoredObjectMetadata(false, null, 0);
            throw e;
        }
    }

    @Override
    public byte[] readObjectPrefix(String key, int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        return client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(properties.bucket()).key(key).range("bytes=0-" + (maxBytes - 1)).build())
                .asByteArray();
    }

    @Override
    public DownloadTarget createPresignedDownload(String key) {
        Duration duration = Duration.ofMinutes(properties.downloadUrlExpirationMinutes());
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(duration)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build()).build());
        return new DownloadTarget(signed.url().toString(), Instant.now().plus(duration));
    }

    @Override public void deleteObject(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

}
