import { createContext, useContext } from "react";
import type { CurrentUser } from "./authTypes";
import type { RegisterRequest } from "./authTypes";

export interface AuthContextType {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSuspended: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  completeOAuthLogin: (code: string) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<CurrentUser | null>;
  clearSession: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
