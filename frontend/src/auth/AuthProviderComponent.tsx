import React, { useState, useEffect, useCallback } from "react";
import type { ReactNode } from "react";
import type { CurrentUser } from "./authTypes";
import { authApi } from "./authApi";
import { getAccessToken, setAccessToken, setRefreshToken, clearAllTokens } from "./tokenStorage";
import { ApiError } from "../api/ApiError";
import { safetyApi } from "../safety/safetyApi";
import { AuthContext } from "./AuthContext";

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(getAccessToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSuspended, setIsSuspended] = useState<boolean>(false);
  const [hasConsented, setHasConsented] = useState<boolean>(true);
  const [consentVersion, setConsentVersion] = useState<string>("v1.0");
  const [consentStatus, setConsentStatus] = useState<string>("PENDING");

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
        return;
      }
      setUser(currentUser);
      setIsSuspended(false);

      const isMinor = (dateOfBirth?: string) => {
        if (!dateOfBirth) return false;
        const birthDate = new Date(dateOfBirth);
        const today = new Date();
        let age = today.getFullYear() - birthDate.getFullYear();
        const m = today.getMonth() - birthDate.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
          age--;
        }
        return age < 18;
      };

      if (currentUser.role === "STUDENT" && isMinor(currentUser.dateOfBirth)) {
        try {
          const status = await safetyApi.checkConsentStatus("KVKK");
          setHasConsented(status.hasConsented);
          setConsentVersion(status.currentVersion);
          setConsentStatus(status.status);
        } catch {
          setHasConsented(false);
          setConsentStatus("PENDING");
        }
      } else {
        setHasConsented(true);
        setConsentStatus("ACCEPTED");
      }
    } catch (error) {
      if (error instanceof ApiError && (error.code === "USER_SUSPENDED" || error.status === 403)) {
        handleSuspendedUser();
      } else {
        clearAllTokens();
        setUser(null);
        setAccessTokenState(null);
      }
    }
  }, [handleSuspendedUser]);

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

  const register = async (email: string, password: string, fullName: string, role: string, dateOfBirth?: string) => {
    setIsLoading(true);
    try {
      const response = await authApi.register(email, password, fullName, role, dateOfBirth);
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
  }, [refreshCurrentUser]);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!user,
        isLoading,
        isSuspended,
        hasConsented,
        consentVersion,
        consentStatus,
        login,
        register,
        logout,
        refreshCurrentUser,
        setHasConsented,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
