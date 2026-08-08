export type UserRole = "STUDENT" | "COACH" | "ADMIN";

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";

export interface CurrentUser {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  dateOfBirth?: string;
  emailVerified: boolean;
  legalOnboardingCompleted: boolean;
  hasLocalPassword: boolean;
  passwordChangedAt?: string;
}

export interface PasswordActionResponse {
  message: string;
  reloginRequired: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  role: "STUDENT" | "COACH";
  dateOfBirth?: string;
  acceptedTermsDocumentId: number;
  acceptedExplicitConsentDocumentId: number;
  marketingEmailOptIn: boolean;
  marketingSmsOptIn: boolean;
}

export interface LegalOnboardingRequest {
  termsDocumentId: number;
  explicitConsentDocumentId: number;
  marketingEmailOptIn: boolean;
  marketingSmsOptIn: boolean;
}

export interface LegalOnboardingResponse {
  legalOnboardingCompleted: boolean;
}

export interface EmailVerificationResponse {
  emailVerified: boolean;
  nextResendAt?: string;
}
