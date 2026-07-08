import React, { createContext, useContext, useState, useEffect } from "react";
import type { ReactNode } from "react";
import type { CurrentUser } from "./authTypes";
import { authApi } from "./authApi";
import { getAccessToken, setAccessToken, setRefreshToken, clearAllTokens } from "./tokenStorage";
import { ApiError } from "../api/ApiError";

interface AuthContextType {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSuspended: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string, role: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(getAccessToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSuspended, setIsSuspended] = useState<boolean>(false);

  const handleSuspendedUser = () => {
    setIsSuspended(true);
    setUser(null);
    clearAllTokens();
    setAccessTokenState(null);
  };

  const refreshCurrentUser = async () => {
    try {
      const currentUser = await authApi.getCurrentUser();
      if (currentUser.status === "SUSPENDED") {
        handleSuspendedUser();
        return;
      }
      setUser(currentUser);
      setIsSuspended(false);
    } catch (error) {
      if (error instanceof ApiError && (error.code === "USER_SUSPENDED" || error.status === 403)) {
        handleSuspendedUser();
      } else {
        clearAllTokens();
        setUser(null);
        setAccessTokenState(null);
      }
    }
  };

  const login = async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(email, password);
      setAccessToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setAccessTokenState(response.accessToken);
      await refreshCurrentUser();
    } catch (error) {
      if (error instanceof ApiError && (error.code === "USER_SUSPENDED" || error.status === 403)) {
        handleSuspendedUser();
      }
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (email: string, password: string, fullName: string, role: string) => {
    setIsLoading(true);
    try {
      const response = await authApi.register(email, password, fullName, role);
      setAccessToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setAccessTokenState(response.accessToken);
      await refreshCurrentUser();
    } catch (error) {
      if (error instanceof ApiError && (error.code === "USER_SUSPENDED" || error.status === 403)) {
        handleSuspendedUser();
      }
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await authApi.logout();
    } finally {
      clearAllTokens();
      setUser(null);
      setAccessTokenState(null);
      setIsSuspended(false);
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const initializeAuth = async () => {
      const token = getAccessToken();
      if (token) {
        await refreshCurrentUser();
      }
      setIsLoading(false);
    };
    initializeAuth();
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!user,
        isLoading,
        isSuspended,
        login,
        register,
        logout,
        refreshCurrentUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
