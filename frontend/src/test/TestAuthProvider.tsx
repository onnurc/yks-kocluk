import type { ReactNode } from "react";
import { AuthContext, type AuthContextType } from "../auth/AuthContext";

const defaultAuthValue: AuthContextType = {
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isLoading: false,
  isSuspended: false,
  login: async () => undefined,
  register: async () => undefined,
  completeOAuthLogin: async () => {
    throw new Error("OAuth girişi bu test sağlayıcısında yapılandırılmadı.");
  },
  logout: async () => undefined,
  refreshCurrentUser: async () => null,
  clearSession: () => undefined,
};

type TestAuthProviderProps = {
  children: ReactNode;
  value?: Partial<AuthContextType>;
};

export function TestAuthProvider({ children, value }: TestAuthProviderProps) {
  return (
    <AuthContext.Provider value={{ ...defaultAuthValue, ...value }}>
      {children}
    </AuthContext.Provider>
  );
}
