export type UserRole = "STUDENT" | "COACH" | "ADMIN";

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";

export interface CurrentUser {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  dateOfBirth?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}
