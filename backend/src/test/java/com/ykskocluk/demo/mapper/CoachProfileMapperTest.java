package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import com.ykskocluk.demo.service.MediaAssetUrlResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoachProfileMapperTest {

    @Test
    void exposesOnlyTheActiveProfileImageAssetId() {
        CoachProfileMapperImpl mapper = new CoachProfileMapperImpl();
        ReflectionTestUtils.setField(mapper, "mediaAssetUrlResolver",
                new MediaAssetUrlResolver("https://api.example.com"));

        MediaAsset image = new MediaAsset();
        ReflectionTestUtils.setField(image, "id", 73L);
        image.setStatus(MediaStatus.ACTIVE);
        image.setVisibility(MediaVisibility.PUBLIC);
        CoachProfile profile = new CoachProfile();
        profile.setProfileImageAsset(image);

        var activeResponse = mapper.toResponse(profile, Set.of());
        assertThat(activeResponse.profileImageAssetId()).isEqualTo(73L);
        assertThat(activeResponse.profileImageUrl()).isEqualTo("https://api.example.com/api/v1/public/media/73");

        image.setStatus(MediaStatus.DELETED);
        var deletedResponse = mapper.toResponse(profile, Set.of());
        assertThat(deletedResponse.profileImageAssetId()).isNull();
        assertThat(deletedResponse.profileImageUrl()).isNull();
    }
}
