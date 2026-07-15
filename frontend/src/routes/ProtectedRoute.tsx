import React from "react";
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

export const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, isLoading, isSuspended } = useAuth();

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "4rem" }}>
        <p>Loading application session...</p>
      </div>
    );
  }

  if (isSuspended) {
    return <Navigate to="/suspended" replace />;
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
};
