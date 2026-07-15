import { httpClient } from "../api/httpClient";
import type { AuthResponse, CurrentUser } from "./authTypes";
import { getRefreshToken } from "./tokenStorage";

export const authApi = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    return httpClient.post<AuthResponse>("/api/v1/auth/login", { email, password });
  },

  register: async (email: string, password: string, fullName: string, role: string, dateOfBirth?: string): Promise<AuthResponse> => {
    return httpClient.post<AuthResponse>("/api/v1/auth/register", {
      email,
      password,
      fullName,
      role,
      dateOfBirth,
    });
  },

  getCurrentUser: async (): Promise<CurrentUser> => {
    return httpClient.get<CurrentUser>("/api/v1/auth/me");
  },

  logout: async (): Promise<void> => {
    const refreshToken = getRefreshToken() || "";
    if (!refreshToken) return;
    try {
      await httpClient.post<void>("/api/v1/auth/logout", { refreshToken });
    } catch (err) {
      // Suppress server logout failure logs in client UI
      console.warn("Server-side logout could not be processed:", err);
    }
  },
};
