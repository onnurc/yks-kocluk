import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ForgotPasswordPage } from "../pages/ForgotPasswordPage";
import { ResetPasswordPage } from "../pages/ResetPasswordPage";
import { SecuritySettingsPage } from "../pages/SecuritySettingsPage";
import { LoginPage } from "../pages/LoginPage";

const mocks = vi.hoisted(() => ({
  forgotPassword: vi.fn(), resetPassword: vi.fn(), changePassword: vi.fn(), clearSession: vi.fn(),
  auth: vi.fn(),
}));
vi.mock("../auth/authApi", () => ({ authApi: { forgotPassword: mocks.forgotPassword, resetPassword: mocks.resetPassword, changePassword: mocks.changePassword } }));
vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth() }));

const baseAuth = { login: vi.fn(), isAuthenticated: false, user: null, isSuspended: false, clearSession: mocks.clearSession };
afterEach(cleanup);

describe("password recovery and security pages", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.auth.mockReturnValue(baseAuth); });

  it("login page links to forgot password", () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByRole("link", { name: "Şifremi unuttum" })).toHaveAttribute("href", "/forgot-password");
  });

  it("forgot password displays the generic confirmation", async () => {
    mocks.forgotPassword.mockResolvedValue({ message: "Bu e-posta adresiyle eşleşen bir hesap varsa şifre sıfırlama bağlantısı gönderildi.", reloginRequired: false });
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("E-posta"), { target: { value: "user@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Sıfırlama bağlantısı gönder" }));
    expect(await screen.findByRole("status")).toHaveTextContent("bir hesap varsa");
  });

  it("reset password validates confirmation before API call", () => {
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "password-two" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi yenile" }));
    expect(screen.getByText("Şifreler eşleşmiyor.")).toBeInTheDocument(); expect(mocks.resetPassword).not.toHaveBeenCalled();
  });

  it("successful reset offers a login link", async () => {
    mocks.resetPassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "password-one" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi yenile" }));
    expect(await screen.findByRole("link", { name: "Giriş yap" })).toHaveAttribute("href", "/login");
  });

  it("Google-only account does not see change-password form", () => {
    mocks.auth.mockReturnValue({ ...baseAuth, user: { id: 1, email: "g@example.com", fullName: "G", role: "STUDENT", status: "ACTIVE", legalOnboardingCompleted: true, hasLocalPassword: false } });
    render(<MemoryRouter><SecuritySettingsPage /></MemoryRouter>);
    expect(screen.getByText(/yerel bir şifresi bulunmuyor/)).toBeInTheDocument(); expect(screen.queryByRole("button", { name: "Şifreyi değiştir" })).not.toBeInTheDocument();
  });

  it("password user change clears local auth and redirects", async () => {
    mocks.changePassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    mocks.auth.mockReturnValue({ ...baseAuth, user: { id: 1, email: "u@example.com", fullName: "U", role: "STUDENT", status: "ACTIVE", legalOnboardingCompleted: true, hasLocalPassword: true } });
    render(<MemoryRouter><SecuritySettingsPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Mevcut şifre"), { target: { value: "old-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "new-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "new-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi değiştir" }));
    await waitFor(() => expect(mocks.clearSession).toHaveBeenCalled());
  });
});
