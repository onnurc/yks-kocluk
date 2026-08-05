import { httpClient } from "../api/httpClient";
import type { AuthResponse, CurrentUser, LegalOnboardingRequest, LegalOnboardingResponse, RegisterRequest } from "./authTypes";
import { getRefreshToken } from "./tokenStorage";

export const authApi = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    return httpClient.post<AuthResponse>("/api/v1/auth/login", { email, password });
  },

  register: (request: RegisterRequest): Promise<AuthResponse> =>
    httpClient.post<AuthResponse>("/api/v1/auth/register", request),

  exchangeOAuthCode: (code: string): Promise<AuthResponse> =>
    httpClient.post<AuthResponse>("/api/v1/auth/oauth2/exchange", { code }),

  completeLegalOnboarding: (request: LegalOnboardingRequest): Promise<LegalOnboardingResponse> =>
    httpClient.post<LegalOnboardingResponse>("/api/v1/auth/legal-onboarding", request),

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
