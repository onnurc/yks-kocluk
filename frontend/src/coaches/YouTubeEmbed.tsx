import { useState } from "react";
import { safeYoutubeEmbedUrl } from "./youtubeEmbedUrl";

export function YouTubeEmbed({ url, title }: { url: string | null | undefined; title: string }) {
  const safeUrl = safeYoutubeEmbedUrl(url);
  const [playing, setPlaying] = useState(false);
  const [thumbnailUnavailable, setThumbnailUnavailable] = useState(false);
  if (!safeUrl) return null;

  const videoId = safeUrl.match(/^https:\/\/www\.youtube-nocookie\.com\/embed\/([A-Za-z0-9_-]{11})$/)?.[1];
  if (!videoId) return null;

  if (!playing) {
    return (
      <button className="youtube-embed-preview" type="button" aria-label={`${title} oynat`} onClick={() => setPlaying(true)}>
        {thumbnailUnavailable ? (
          <span className="youtube-embed-preview__fallback" aria-hidden="true">YouTube videosu</span>
        ) : (
          <img
            src={`https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`}
            alt=""
            onError={() => setThumbnailUnavailable(true)}
          />
        )}
        <span className="youtube-embed-preview__play" aria-hidden="true">▶</span>
      </button>
    );
  }

  return (
    <iframe
      src={`${safeUrl}?autoplay=1`}
      title={title}
      loading="lazy"
      referrerPolicy="strict-origin-when-cross-origin"
      sandbox="allow-scripts allow-same-origin allow-presentation"
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      allowFullScreen
    />
  );
}
