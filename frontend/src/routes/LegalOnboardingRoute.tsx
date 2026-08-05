import React from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

export const LegalOnboardingRoute: React.FC = () => {
  const { user } = useAuth();
  const location = useLocation();

  if (user && !user.legalOnboardingCompleted) {
    return <Navigate to="/legal-onboarding" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
};
