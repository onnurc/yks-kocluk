import React, { useState, useEffect, useCallback } from "react";
import type { ReactNode } from "react";
import type { CurrentUser, RegisterRequest } from "./authTypes";
import { authApi } from "./authApi";
import { getAccessToken, setAccessToken, setRefreshToken, clearAllTokens } from "./tokenStorage";
import { ApiError } from "../api/ApiError";
import { AuthContext } from "./AuthContext";

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(getAccessToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSuspended, setIsSuspended] = useState<boolean>(false);

  const clearSession = useCallback(() => {
    clearAllTokens();
    setUser(null);
    setAccessTokenState(null);
    setIsSuspended(false);
  }, []);

  const handleSuspendedUser = useCallback(() => {
    setIsSuspended(true);
    setUser(null);
    clearAllTokens();
    setAccessTokenState(null);
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    try {
      const currentUser = await authApi.getCurrentUser();
      if (currentUser.status === "SUSPENDED") {
        handleSuspendedUser();
        return null;
      }
      setUser(currentUser);
      setIsSuspended(false);
      return currentUser;
    } catch (error) {
      if (error instanceof ApiError && error.code === "USER_SUSPENDED") {
        handleSuspendedUser();
      } else {
        clearSession();
      }
      return null;
    }
  }, [clearSession, handleSuspendedUser]);

  const login = async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(email, password);
      setAccessToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setAccessTokenState(response.accessToken);
      await refreshCurrentUser();
    } catch (error) {
      if (error instanceof ApiError && error.code === "USER_SUSPENDED") {
        handleSuspendedUser();
      }
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (request: RegisterRequest) => {
    setIsLoading(true);
    try {
      const response = await authApi.register(request);
      setAccessToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setAccessTokenState(response.accessToken);
      await refreshCurrentUser();
    } catch (error) {
      if (error instanceof ApiError && error.code === "USER_SUSPENDED") {
        handleSuspendedUser();
      }
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const completeOAuthLogin = async (code: string): Promise<CurrentUser> => {
    setIsLoading(true);
    try {
      const response = await authApi.exchangeOAuthCode(code);
      setAccessToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setAccessTokenState(response.accessToken);
      const currentUser = await authApi.getCurrentUser();
      setUser(currentUser);
      setIsSuspended(false);
      return currentUser;
    } catch (error) {
      clearSession();
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
      clearSession();
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
  }, [refreshCurrentUser]);

  useEffect(() => {
    const handleLegalOnboardingRequired = () => { void refreshCurrentUser(); };
    const handleEmailVerificationRequired = () => { void refreshCurrentUser(); };
    // Dispatched by httpClient's central 401/refresh handling (and the WebSocket reconnect
    // loop, which shares that same refresh mechanism) once the refresh token itself is dead.
    const handleSessionExpired = () => { clearSession(); };
    window.addEventListener("legal-onboarding-required", handleLegalOnboardingRequired);
    window.addEventListener("email-verification-required", handleEmailVerificationRequired);
    window.addEventListener("session-expired", handleSessionExpired);
    return () => {
      window.removeEventListener("legal-onboarding-required", handleLegalOnboardingRequired);
      window.removeEventListener("email-verification-required", handleEmailVerificationRequired);
      window.removeEventListener("session-expired", handleSessionExpired);
    };
  }, [refreshCurrentUser, clearSession]);

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
        completeOAuthLogin,
        logout,
        refreshCurrentUser,
        clearSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
