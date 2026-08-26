package com.ykskocluk.demo.service;

import com.ykskocluk.demo.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeVideoServiceTest {
    private final YoutubeVideoService service = new YoutubeVideoService();

    @Test
    void normalizesSupportedYoutubeIdsAndUrls() {
        assertThat(service.normalizeVideoId("dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ");
        assertThat(service.normalizeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .isEqualTo("dQw4w9WgXcQ");
        assertThat(service.normalizeVideoId("https://youtu.be/dQw4w9WgXcQ?t=10"))
                .isEqualTo("dQw4w9WgXcQ");
        assertThat(service.normalizeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
                .isEqualTo("dQw4w9WgXcQ");
        assertThat(service.embedUrl("dQw4w9WgXcQ"))
                .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
    }

    @Test
    void rejectsArbitraryDomainsSchemesHtmlAndMalformedIds() {
        for (String value : new String[]{
                "https://evil.example/watch?v=dQw4w9WgXcQ",
                "javascript:alert(1)",
                "<iframe src='https://youtube.com/embed/dQw4w9WgXcQ'></iframe>",
                "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
                "too-short"
        }) {
            assertThatThrownBy(() -> service.normalizeVideoId(value))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).getErrorCode())
                    .isEqualTo("COACH_YOUTUBE_URL_INVALID");
        }
    }
}
