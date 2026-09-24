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
