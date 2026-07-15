import { createContext, useContext } from "react";
import type { CurrentUser } from "./authTypes";

export interface AuthContextType {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSuspended: boolean;
  hasConsented: boolean;
  consentVersion: string;
  consentStatus: string;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string, role: string, dateOfBirth?: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<void>;
  setHasConsented: (val: boolean) => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
