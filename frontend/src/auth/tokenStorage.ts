/**
 * The short-lived access token is deliberately process-memory only. Page reload continuity comes
 * from the server-managed HttpOnly refresh cookie; no authentication token is persisted in Web
 * Storage or exposed to frontend code beyond the access token needed for Bearer/STOMP auth.
 */
let accessToken: string | null = null;

const LEGACY_ACCESS_TOKEN_KEY = "yks_coaching_access_token";
const LEGACY_REFRESH_TOKEN_KEY = "yks_coaching_refresh_token";

export const getAccessToken = (): string | null => accessToken;

export const setAccessToken = (token: string): void => {
  accessToken = token;
};

export const clearAccessToken = (): void => {
  accessToken = null;
};

/**
 * Removes only tokens persisted by frontend builds that predate cookie-based refresh sessions.
 * Legacy refresh tokens are intentionally never read or reused.
 */
export const clearLegacyAuthTokens = (): void => {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(LEGACY_ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY);
};

export const clearAllTokens = (): void => {
  clearAccessToken();
  clearLegacyAuthTokens();
};
