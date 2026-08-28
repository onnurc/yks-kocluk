import { httpClient } from "../api/httpClient";
import type { AuthResponse, CurrentUser, LegalOnboardingRequest, LegalOnboardingResponse, RegisterRequest, PasswordActionResponse, EmailVerificationResponse } from "./authTypes";

export const authApi = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    return httpClient.postWithCredentials<AuthResponse>("/api/v1/auth/login", { email, password });
  },

  register: (request: RegisterRequest): Promise<AuthResponse> =>
    httpClient.postWithCredentials<AuthResponse>("/api/v1/auth/register", request),

  exchangeOAuthCode: (code: string): Promise<AuthResponse> =>
    httpClient.postWithCredentials<AuthResponse>("/api/v1/auth/oauth2/exchange", { code }),

  completeLegalOnboarding: (request: LegalOnboardingRequest): Promise<LegalOnboardingResponse> =>
    httpClient.post<LegalOnboardingResponse>("/api/v1/auth/legal-onboarding", request),

  getCurrentUser: async (): Promise<CurrentUser> => {
    return httpClient.get<CurrentUser>("/api/v1/auth/me");
  },

  refresh: (): Promise<AuthResponse> =>
    httpClient.postWithCredentials<AuthResponse>("/api/v1/auth/refresh"),

  logout: async (): Promise<void> => {
    try {
      await httpClient.postWithCredentials<void>("/api/v1/auth/logout");
    } catch (err) {
      // Suppress server logout failure logs in client UI
      console.warn("Server-side logout could not be processed:", err);
    }
  },

  forgotPassword: (email: string): Promise<PasswordActionResponse> =>
    httpClient.post<PasswordActionResponse>("/api/v1/auth/forgot-password", { email }),

  resetPassword: (token: string, newPassword: string): Promise<PasswordActionResponse> =>
    httpClient.post<PasswordActionResponse>("/api/v1/auth/reset-password", { token, newPassword }),

  changePassword: (currentPassword: string, newPassword: string): Promise<PasswordActionResponse> =>
    httpClient.post<PasswordActionResponse>("/api/v1/auth/change-password", { currentPassword, newPassword }),

  verifyEmail: (code: string): Promise<EmailVerificationResponse> =>
    httpClient.post<EmailVerificationResponse>("/api/v1/auth/verify-email", { code }),

  resendVerification: (): Promise<EmailVerificationResponse> =>
    httpClient.post<EmailVerificationResponse>("/api/v1/auth/resend-verification"),
};
