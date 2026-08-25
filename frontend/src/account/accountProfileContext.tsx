import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, ReactNode, SetStateAction } from "react";
import { useAuth } from "../auth/AuthProvider";
import { accountApi } from "./accountApi";
import type { CoachProfileResponse, StudentProfileResponse } from "./accountTypes";

type AccountProfileContextValue = {
  studentProfile: StudentProfileResponse | null;
  coachProfile: CoachProfileResponse | null;
  profileImageUrl: string | null;
  profileLoading: boolean;
  profileLoadFailed: boolean;
  refreshProfile: () => Promise<void>;
  setStudentProfile: Dispatch<SetStateAction<StudentProfileResponse | null>>;
  setCoachProfile: Dispatch<SetStateAction<CoachProfileResponse | null>>;
};

const AccountProfileContext = createContext<AccountProfileContextValue | null>(null);

export function AccountProfileProvider({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuth();
  const [studentProfileState, setStudentProfile] = useState<StudentProfileResponse | null>(null);
  const [coachProfileState, setCoachProfile] = useState<CoachProfileResponse | null>(null);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileLoadFailed, setProfileLoadFailed] = useState(false);
  const requestIdRef = useRef(0);
  const userId = user?.id;
  const userRole = user?.role;
  const profileOwner = userId && userRole ? `${userRole}:${userId}` : null;

  const refreshProfile = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    if (!isAuthenticated || !userId || !userRole || userRole === "ADMIN") {
      setProfileLoading(false);
      setProfileLoadFailed(false);
      return;
    }

    setProfileLoading(true);
    setProfileLoadFailed(false);
    try {
      if (userRole === "STUDENT") {
        const profile = await accountApi.getStudentProfile();
        if (requestIdRef.current === requestId) {
          setStudentProfile(profile);
          setCoachProfile(null);
          setLoadedFor(`STUDENT:${userId}`);
        }
      } else {
        const profile = await accountApi.getCoachProfile();
        if (requestIdRef.current === requestId) {
          setCoachProfile(profile);
          setStudentProfile(null);
          setLoadedFor(`COACH:${userId}`);
        }
      }
    } catch {
      if (requestIdRef.current === requestId) setProfileLoadFailed(true);
    } finally {
      if (requestIdRef.current === requestId) setProfileLoading(false);
    }
  }, [isAuthenticated, userId, userRole]);

  useEffect(() => {
    void refreshProfile();
    return () => { requestIdRef.current += 1; };
  }, [refreshProfile]);

  const studentProfile = userRole === "STUDENT" && loadedFor === profileOwner ? studentProfileState : null;
  const coachProfile = userRole === "COACH" && loadedFor === profileOwner ? coachProfileState : null;
  const profileImageUrl = studentProfile?.profileImageUrl ?? coachProfile?.profileImageUrl ?? null;

  const value = useMemo<AccountProfileContextValue>(() => ({
    studentProfile,
    coachProfile,
    profileImageUrl,
    profileLoading,
    profileLoadFailed,
    refreshProfile,
    setStudentProfile,
    setCoachProfile,
  }), [coachProfile, profileImageUrl, profileLoadFailed, profileLoading, refreshProfile, studentProfile]);

  return <AccountProfileContext.Provider value={value}>{children}</AccountProfileContext.Provider>;
}

export function useAccountProfile() {
  const context = useContext(AccountProfileContext);
  if (!context) throw new Error("useAccountProfile must be used within AccountProfileProvider");
  return context;
}
