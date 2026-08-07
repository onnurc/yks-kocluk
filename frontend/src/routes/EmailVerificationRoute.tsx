import React from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

export const EmailVerificationRoute: React.FC = () => {
  const { user } = useAuth();
  const location = useLocation();

  if (user && !user.emailVerified) {
    return <Navigate to="/verify-email" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
};
