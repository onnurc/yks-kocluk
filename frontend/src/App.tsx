import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
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
import { AdminCoachApplicationsPage } from "./pages/admin/AdminCoachApplicationsPage";
import { AdminUsersPage } from "./pages/admin/AdminUsersPage";
import { AdminCoachesPage } from "./pages/admin/AdminCoachesPage";
import { AdminSessionsPage } from "./pages/admin/AdminSessionsPage";
import { BookingsPage } from "./pages/BookingsPage";
import { ChatPage } from "./pages/ChatPage";
import { OAuthCallbackPage } from "./pages/OAuthCallbackPage";
import { LegalOnboardingPage } from "./pages/LegalOnboardingPage";
import { PrivacySettingsPage } from "./pages/PrivacySettingsPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { SecuritySettingsPage } from "./pages/SecuritySettingsPage";
import { AccountPage } from "./pages/AccountPage";
import { LegalOnboardingRoute } from "./routes/LegalOnboardingRoute";
import { EmailVerificationRoute } from "./routes/EmailVerificationRoute";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import { HomePage } from "./public/HomePage";
import { AboutPage } from "./public/AboutPage";
import { CoachingPage } from "./public/CoachingPage";
import { CoachesPage } from "./public/CoachesPage";
import { CoachProfilePage } from "./public/CoachProfilePage";
import { CoachApplicationPage } from "./public/CoachApplicationPage";
import { PublicLayout } from "./public/PublicLayout";
import "./App.css";

const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Marketing Routes */}
          <Route element={<PublicLayout />}>
            <Route index element={<HomePage />} />
            <Route path="biz-kimiz" element={<AboutPage />} />
            <Route path="kocluk" element={<CoachingPage />} />
            <Route path="coaches" element={<CoachesPage />} />
            <Route path="coaches/:id" element={<CoachProfilePage />} />
            <Route path="koc-basvuru" element={<CoachApplicationPage />} />
          </Route>

          {/* Authentication Routes */}
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

                <Route element={<RoleRoute allowedRoles={["STUDENT", "COACH"]} />}>
                  <Route path="/account" element={<AccountPage />} />
                </Route>
              
                {/* Student-only Routes */}
                <Route element={<RoleRoute allowedRoles={["STUDENT"]} />}>
                  <Route path="/bookings" element={<BookingsPage />} />
                </Route>

                {/* Messaging: students and coaches both participate in conversations */}
                <Route element={<RoleRoute allowedRoles={["STUDENT", "COACH"]} />}>
                  <Route path="/messages" element={<ChatPage />} />
                  <Route path="/messages/:conversationId" element={<ChatPage />} />
                </Route>
              </Route>

              {/* Admin-only Routes */}
              <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                <Route path="/admin" element={<AdminDashboardPage />} />
                <Route path="/admin/users" element={<AdminUsersPage />} />
                <Route path="/admin/coaches" element={<AdminCoachesPage />} />
                <Route path="/admin/safety" element={<AdminSafetyPage />} />
                <Route path="/admin/reports" element={<AdminSafetyPage />} />
                <Route path="/admin/finance" element={<AdminFinancePage />} />
                <Route path="/admin/sessions" element={<AdminSessionsPage />} />
                <Route path="/admin/coach-applications" element={<AdminCoachApplicationsPage />} />
                <Route path="/admin/messages" element={<ChatPage />} />
                <Route path="/admin/messages/:conversationId" element={<ChatPage />} />
              </Route>
              </Route>
            </Route>
          </Route>

          {/* Wildcard 404 Route */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
