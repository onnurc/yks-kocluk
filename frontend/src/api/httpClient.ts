import { getAccessToken, setAccessToken, clearAllTokens } from "../auth/tokenStorage";
import { authApi } from "../auth/authApi";
import { ApiError } from "./ApiError";
import type { FieldError } from "./ApiError";
import { resolveApiBaseUrl } from "./apiBaseUrl";

export const getApiBaseUrl = (): string => {
  return resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, import.meta.env.DEV);
};

// Central 401 handling: a single in-flight refresh is shared by every caller (REST requests
// here and the WebSocket reconnect loop in useConversationSocket) so concurrent 401s don't
// each fire their own /auth/refresh call. On refresh failure the session is torn down here,
// once, and "session-expired" notifies the rest of the app (AuthProvider clears its state,
// ProtectedRoute redirects to /login) rather than each caller handling it separately.
let refreshPromise: Promise<string | null> | null = null;

const performTokenRefresh = async (): Promise<string | null> => {
  try {
    const response = await authApi.refresh();
    setAccessToken(response.accessToken);
    return response.accessToken;
  } catch {
    clearAllTokens();
    window.dispatchEvent(new Event("session-expired"));
    return null;
  }
};

export const ensureFreshToken = (): Promise<string | null> => {
  if (!refreshPromise) {
    refreshPromise = performTokenRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
};

export const request = async <T>(
  path: string,
  options: RequestInit = {},
  isRetry = false
): Promise<T> => {
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  const url = `${getApiBaseUrl()}${cleanPath}`;
  const headers = new Headers(options.headers);

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAccessToken();
  const hadAuthHeader = !!token || headers.has("Authorization");
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Only retry requests that were actually authenticated — a 401 on login/register means
  // wrong credentials, not an expired session, and must not trigger a refresh loop.
  if (response.status === 401 && hadAuthHeader && !isRetry && cleanPath !== "/api/v1/auth/refresh") {
    const newToken = await ensureFreshToken();
    if (newToken) {
      const retryHeaders = new Headers(options.headers);
      if (options.body && !retryHeaders.has("Content-Type")) {
        retryHeaders.set("Content-Type", "application/json");
      }
      retryHeaders.set("Authorization", `Bearer ${newToken}`);
      return request<T>(path, { ...options, headers: retryHeaders }, true);
    }
    // ensureFreshToken already cleared the session and dispatched "session-expired" — fall
    // through so this call still surfaces its original 401 as an ApiError to the caller.
  }

  if (!response.ok) {
    let errorData: Record<string, unknown> = {};
    try {
      errorData = await response.json();
    } catch {
      // Ignore parsing errors for non-JSON content
    }

    const status = response.status;
    const title = typeof errorData.title === "string" ? errorData.title : response.statusText || "Error";
    const detail = typeof errorData.detail === "string" ? errorData.detail : typeof errorData.message === "string" ? errorData.message : "An unexpected error occurred.";
    const codeValue = errorData.errorCode || errorData.code;
    const code = typeof codeValue === "string" ? codeValue : undefined;
    const fieldErrorsValue = errorData.fieldErrors || errorData.errors;
    const fieldErrors = Array.isArray(fieldErrorsValue) ? fieldErrorsValue as FieldError[] : undefined;

    if (code === "LEGAL_ONBOARDING_REQUIRED") {
      window.dispatchEvent(new Event("legal-onboarding-required"));
    }
    if (code === "EMAIL_VERIFICATION_REQUIRED") {
      window.dispatchEvent(new Event("email-verification-required"));
    }

    const nextAllowedAt = typeof errorData.nextAllowedAt === "string" ? errorData.nextAllowedAt : undefined;
    throw new ApiError(status, title, detail, code, fieldErrors, nextAllowedAt);
  }

  if (response.status === 204) {
    return {} as T;
  }

  try {
    return await response.json();
  } catch {
    return {} as T;
  }
};

export const httpClient = {
  get: <T>(path: string, headers?: Record<string, string>) =>
    request<T>(path, { method: "GET", headers }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
      headers,
    }),
  postWithCredentials: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
      headers,
      credentials: "include",
    }),
  put: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
      headers,
    }),
  patch: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
      headers,
    }),
  delete: <T>(path: string, headers?: Record<string, string>) =>
    request<T>(path, { method: "DELETE", headers }),
};
