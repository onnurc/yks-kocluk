const LOCAL_API_DEFAULT = "http://localhost:8080";

export function resolveApiBaseUrl(rawValue: string | undefined, development: boolean): string {
  const configured = rawValue?.trim();
  if (!configured && !development) {
    throw new Error("VITE_API_BASE_URL is required for production builds");
  }

  const value = configured || LOCAL_API_DEFAULT;
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("VITE_API_BASE_URL must be a valid absolute URL");
  }

  if (url.username || url.password || url.search || url.hash) {
    throw new Error("VITE_API_BASE_URL must not contain credentials, a query, or a fragment");
  }
  if (development) {
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      throw new Error("VITE_API_BASE_URL must use HTTP or HTTPS in development");
    }
  } else if (url.protocol !== "https:" || isLocalOrPrivateHost(url.hostname)) {
    throw new Error("VITE_API_BASE_URL must use a public HTTPS origin in production");
  }

  return value.replace(/\/+$/, "");
}

function isLocalOrPrivateHost(rawHost: string): boolean {
  const host = rawHost.toLowerCase().replace(/^\[|\]$/g, "");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local")
      || host.endsWith(".internal") || host === "::1" || host === "0:0:0:0:0:0:0:1") {
    return true;
  }

  const parts = host.split(".");
  if (parts.length !== 4 || parts.some((part) => !/^\d+$/.test(part))) return false;
  const octets = parts.map(Number);
  if (octets.some((octet) => octet < 0 || octet > 255)) return true;
  const [a, b] = octets;
  return a === 0 || a === 10 || a === 127 || a >= 224
    || (a === 100 && b >= 64 && b <= 127)
    || (a === 169 && b === 254)
    || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168);
}
