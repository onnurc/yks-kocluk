export const safeYoutubeEmbedUrl = (value: string | null | undefined): string | null => {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.hostname !== "www.youtube-nocookie.com") return null;
    if (!/^\/embed\/[A-Za-z0-9_-]{11}$/.test(url.pathname)) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return url.toString();
  } catch {
    return null;
  }
};

export function YouTubeEmbed({ url, title }: { url: string | null | undefined; title: string }) {
  const safeUrl = safeYoutubeEmbedUrl(url);
  if (!safeUrl) return null;
  return (
    <iframe
      src={safeUrl}
      title={title}
      loading="lazy"
      referrerPolicy="strict-origin-when-cross-origin"
      sandbox="allow-scripts allow-same-origin allow-presentation"
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      allowFullScreen
    />
  );
}
