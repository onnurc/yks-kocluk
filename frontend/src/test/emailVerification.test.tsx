import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/ApiError";
import { VerifyEmailPage } from "../pages/VerifyEmailPage";
import { EmailVerificationRoute } from "../routes/EmailVerificationRoute";

const mocks = vi.hoisted(() => ({
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  auth: vi.fn(),
}));

vi.mock("../auth/authApi", () => ({
  authApi: { verifyEmail: mocks.verifyEmail, resendVerification: mocks.resendVerification },
}));
vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth() }));

const unverified = {
  id: 1, email: "student@example.com", fullName: "Student", role: "STUDENT" as const,
  status: "ACTIVE" as const, emailVerified: false, legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

const Location = () => <span data-testid="location">{useLocation().pathname}</span>;

afterEach(cleanup);

describe("email verification flow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.auth.mockReturnValue({ user: unverified, refreshCurrentUser: vi.fn() });
  });

  it("accepts only six numeric digits", () => {
    render(<MemoryRouter><VerifyEmailPage /></MemoryRouter>);
    const input = screen.getByLabelText("6 haneli doğrulama kodu:");
    const verifyButton = screen.getByRole("button", { name: "E-postayı Doğrula" });
    expect(verifyButton).toBeDisabled();
    fireEvent.change(input, { target: { value: "12a34-567" } });
    expect(input).toHaveValue("123456");
    expect(verifyButton).toBeEnabled();
  });

  it("shows the invalid-code error", async () => {
    mocks.verifyEmail.mockRejectedValue(new ApiError(400, "Bad Request", "invalid", "EMAIL_VERIFICATION_CODE_INVALID"));
    render(<MemoryRouter><VerifyEmailPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("6 haneli doğrulama kodu:"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "E-postayı Doğrula" }));
    expect(await screen.findByText("Girdiğiniz doğrulama kodu geçersiz.")).toBeInTheDocument();
  });

  it("refreshes auth and sends legally incomplete user to onboarding", async () => {
    const refreshed = { ...unverified, emailVerified: true, legalOnboardingCompleted: false };
    const refreshCurrentUser = vi.fn().mockResolvedValue(refreshed);
    mocks.auth.mockReturnValue({ user: unverified, refreshCurrentUser });
    mocks.verifyEmail.mockResolvedValue({ emailVerified: true });
    render(
      <MemoryRouter initialEntries={["/verify-email"]}>
        <Routes>
          <Route path="/verify-email" element={<><VerifyEmailPage /><Location /></>} />
          <Route path="/legal-onboarding" element={<Location />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText("6 haneli doğrulama kodu:"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "E-postayı Doğrula" }));
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/legal-onboarding"));
    expect(refreshCurrentUser).toHaveBeenCalled();
  });

  it("resend starts the server-provided cooldown", async () => {
    mocks.resendVerification.mockResolvedValue({ emailVerified: false, nextResendAt: new Date(Date.now() + 60_000).toISOString() });
    render(<MemoryRouter><VerifyEmailPage /></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "Yeni Kod Gönder" }));
    expect(await screen.findByRole("button", { name: /Yeni kod için/ })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("Yeni doğrulama kodu e-posta adresinize gönderildi.");
  });

  it("protected product routes redirect unverified users", () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route element={<EmailVerificationRoute />}><Route path="/dashboard" element={<span>dashboard</span>} /></Route>
          <Route path="/verify-email" element={<Location />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByTestId("location")).toHaveTextContent("/verify-email");
  });
});
