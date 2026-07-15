const ACCESS_TOKEN_KEY = "yks_coaching_access_token";
const REFRESH_TOKEN_KEY = "yks_coaching_refresh_token";

/**
 * Retrieves the stored access token from localStorage.
 * 
 * TODO: Evaluate transitioning to a secure HttpOnly cookie strategy in production
 * to mitigate XSS exposure risks.
 */
export const getAccessToken = (): string | null => {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
};

export const setAccessToken = (token: string): void => {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
};

export const clearAccessToken = (): void => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
};

/**
 * Retrieves the stored refresh token from localStorage.
 */
export const getRefreshToken = (): string | null => {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
};

export const setRefreshToken = (token: string): void => {
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
};

export const clearRefreshToken = (): void => {
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};

export const clearAllTokens = (): void => {
  clearAccessToken();
  clearRefreshToken();
};
