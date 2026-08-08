import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./auth/AuthProvider";
import { AppLayout } from "./components/AppLayout";
import { ProtectedRoute } from "./routes/ProtectedRoute";
import { RoleRoute } from "./routes/RoleRoute";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { DashboardPage } from "./pages/DashboardPage";
import { SuspendedPage } from "./pages/SuspendedPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage";
import { AdminSafetyPage } from "./pages/admin/AdminSafetyPage";
import { AdminFinancePage } from "./pages/admin/AdminFinancePage";
import { CoachListPage } from "./pages/CoachListPage";
import { CoachDetailPage } from "./pages/CoachDetailPage";
import { BookingsPage } from "./pages/BookingsPage";
import { MessagesPage } from "./pages/MessagesPage";
import { ConversationPage } from "./pages/ConversationPage";
import { OAuthCallbackPage } from "./pages/OAuthCallbackPage";
import { LegalOnboardingPage } from "./pages/LegalOnboardingPage";
import { PrivacySettingsPage } from "./pages/PrivacySettingsPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { SecuritySettingsPage } from "./pages/SecuritySettingsPage";
import { LegalOnboardingRoute } from "./routes/LegalOnboardingRoute";
import { EmailVerificationRoute } from "./routes/EmailVerificationRoute";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import "./App.css";

const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/suspended" element={<SuspendedPage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          {/* Protected Routes */}
          <Route element={<ProtectedRoute />}>
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/legal-onboarding" element={<LegalOnboardingPage />} />
            <Route element={<AppLayout />}>
              <Route path="/privacy" element={<PrivacySettingsPage />} />
              <Route path="/security" element={<SecuritySettingsPage />} />
              <Route element={<EmailVerificationRoute />}>
              <Route element={<LegalOnboardingRoute />}>
                <Route path="/dashboard" element={<DashboardPage />} />
              
              {/* Student-only Routes */}
              <Route element={<RoleRoute allowedRoles={["STUDENT"]} />}>
                <Route path="/coaches" element={<CoachListPage />} />
                <Route path="/coaches/:id" element={<CoachDetailPage />} />
                <Route path="/bookings" element={<BookingsPage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/messages/:conversationId" element={<ConversationPage />} />
              </Route>
              </Route>

              {/* Admin-only Routes */}
              <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                <Route path="/admin" element={<AdminDashboardPage />} />
                <Route path="/admin/safety" element={<AdminSafetyPage />} />
                <Route path="/admin/finance" element={<AdminFinancePage />} />
              </Route>
              </Route>
            </Route>
          </Route>

          {/* Root Redirect */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          {/* Wildcard 404 Route */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
