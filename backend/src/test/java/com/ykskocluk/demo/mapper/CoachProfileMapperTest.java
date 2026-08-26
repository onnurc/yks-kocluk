package com.ykskocluk.demo.mapper;

import com.ykskocluk.demo.config.MediaPublicUrlProperties;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import com.ykskocluk.demo.service.MediaAssetUrlResolver;
import com.ykskocluk.demo.service.YoutubeEmbedUrlResolver;
import com.ykskocluk.demo.service.YoutubeVideoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoachProfileMapperTest {

    @Test
    void exposesOnlyTheActiveProfileImageAssetId() {
        CoachProfileMapperImpl mapper = new CoachProfileMapperImpl();
        ReflectionTestUtils.setField(mapper, "mediaAssetUrlResolver",
                new MediaAssetUrlResolver(new MediaPublicUrlProperties("https://api.example.com")));
        ReflectionTestUtils.setField(mapper, "youtubeEmbedUrlResolver",
                new YoutubeEmbedUrlResolver(new YoutubeVideoService()));

        MediaAsset image = new MediaAsset();
        ReflectionTestUtils.setField(image, "id", 73L);
        image.setPublicToken("b".repeat(64));
        image.setStatus(MediaStatus.ACTIVE);
        image.setVisibility(MediaVisibility.PUBLIC);
        CoachProfile profile = new CoachProfile();
        profile.setProfileImageAsset(image);
        profile.setIntroYoutubeVideoId("dQw4w9WgXcQ");

        var activeResponse = mapper.toResponse(profile, Set.of());
        assertThat(activeResponse.profileImageAssetId()).isEqualTo(73L);
        assertThat(activeResponse.profileImageUrl()).isEqualTo("https://api.example.com/api/v1/public/media/"
                + "b".repeat(64));
        assertThat(activeResponse.introVideoEmbedUrl())
                .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");

        image.setStatus(MediaStatus.DELETED);
        var deletedResponse = mapper.toResponse(profile, Set.of());
        assertThat(deletedResponse.profileImageAssetId()).isNull();
        assertThat(deletedResponse.profileImageUrl()).isNull();
    }
}
