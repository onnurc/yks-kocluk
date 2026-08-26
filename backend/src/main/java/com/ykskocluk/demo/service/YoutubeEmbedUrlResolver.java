package com.ykskocluk.demo.service;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class YoutubeEmbedUrlResolver {
    private final YoutubeVideoService youtubeVideos;

    public YoutubeEmbedUrlResolver(YoutubeVideoService youtubeVideos) {
        this.youtubeVideos = youtubeVideos;
    }

    @Named("youtubeEmbedUrl")
    public String embedUrl(String videoId) {
        return youtubeVideos.embedUrl(videoId);
    }
}
