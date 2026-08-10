package com.ykskocluk.demo.storage;

import com.ykskocluk.demo.config.R2Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubStorageServiceTest {
    private StubStorageService service(String publicUrl) {
        return new StubStorageService(new R2Properties(false, null, null, null, null, null, publicUrl, 10, 10));
    }

    @Test void worksWithoutSecretsOrNetworkAndSimulatesCompletion() {
        StubStorageService storage = service(null);
        var upload = storage.createPresignedUpload("public/profile-images/1/id.jpg", "image/jpeg", 123);
        assertThat(upload.url()).startsWith("https://stub-storage.invalid/upload/");
        assertThat(storage.headObject("public/profile-images/1/id.jpg")).isEqualTo(
                new StorageService.StoredObjectMetadata(true, "image/jpeg", 123));
        assertThat(storage.resolvePublicUrl("key")).isNull();
    }

    @Test void configuredPublicBaseUrlResolvesStableUrl() {
        assertThat(service("https://media.example.com").resolvePublicUrl("public/a.jpg"))
                .isEqualTo("https://media.example.com/public/a.jpg");
    }
}
