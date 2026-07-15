import { getAccessToken } from "../auth/tokenStorage";
import { ApiError } from "./ApiError";

const getBaseUrl = (): string => {
  return (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
};

export const request = async <T>(
  path: string,
  options: RequestInit = {}
): Promise<T> => {
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  const url = `${getBaseUrl()}${cleanPath}`;
  const headers = new Headers(options.headers);

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAccessToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorData: any = {};
    try {
      errorData = await response.json();
    } catch {
      // Ignore parsing errors for non-JSON content
    }

    const status = response.status;
    const title = errorData.title || response.statusText || "Error";
    const detail = errorData.detail || errorData.message || "An unexpected error occurred.";
    const code = errorData.errorCode || errorData.code;
    const fieldErrors = errorData.fieldErrors || errorData.errors;

    throw new ApiError(status, title, detail, code, fieldErrors);
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
  post: <T>(path: string, body?: any, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
      headers,
    }),
  put: <T>(path: string, body?: any, headers?: Record<string, string>) =>
    request<T>(path, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
      headers,
    }),
  delete: <T>(path: string, headers?: Record<string, string>) =>
    request<T>(path, { method: "DELETE", headers }),
};
