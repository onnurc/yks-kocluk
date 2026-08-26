package com.ykskocluk.demo.service;

import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class YoutubeVideoService {
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Set<String> STANDARD_HOSTS = Set.of("youtube.com", "www.youtube.com");

    public String normalizeVideoId(String value) {
        String candidate = value == null ? "" : value.trim();
        if (VIDEO_ID.matcher(candidate).matches()) return candidate;

        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException ex) {
            throw invalidYoutubeReference();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
            throw invalidYoutubeReference();
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String id = null;
        if (host.equals("youtu.be")) {
            id = firstPathSegment(uri.getPath());
        } else if (STANDARD_HOSTS.contains(host)) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.equals("/watch")) id = queryParameter(uri.getRawQuery(), "v");
            else if (path.startsWith("/shorts/") || path.startsWith("/embed/")) id = secondPathSegment(path);
        }
        if (id == null || !VIDEO_ID.matcher(id).matches()) throw invalidYoutubeReference();
        return id;
    }

    public String embedUrl(String videoId) {
        if (videoId == null) return null;
        if (!VIDEO_ID.matcher(videoId).matches()) throw invalidYoutubeReference();
        return "https://www.youtube-nocookie.com/embed/" + videoId;
    }

    private String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) return null;
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2 && URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst().orElse(null);
    }

    private String firstPathSegment(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        return segments.length > 1 ? segments[1] : null;
    }

    private String secondPathSegment(String path) {
        String[] segments = path.split("/");
        return segments.length > 2 ? segments[2] : null;
    }

    private ApiException invalidYoutubeReference() {
        return new ApiException(HttpStatus.BAD_REQUEST, "COACH_YOUTUBE_URL_INVALID",
                "Only a valid YouTube video ID or HTTPS youtube.com/youtu.be URL is allowed");
    }
}
