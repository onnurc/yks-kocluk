import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
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
    expect(screen.getByRole("link", { name: "Parolamı Unuttum" })).toHaveAttribute("href", "/forgot-password");
  });

  it("forgot password displays the generic confirmation", async () => {
    mocks.forgotPassword.mockResolvedValue({ message: "Bu e-posta adresiyle eşleşen bir hesap varsa şifre sıfırlama bağlantısı gönderildi.", reloginRequired: false });
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("E-posta Adresi"), { target: { value: "user@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Sıfırlama Bağlantısı Gönder" }));
    await waitFor(() => expect(mocks.forgotPassword).toHaveBeenCalledWith("user@example.com"));
    expect(await screen.findByRole("status")).toHaveTextContent("bir hesap varsa");
  });

  it("renders forgot password in the auth shell and returns to login", () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/forgot-password"]}>
        <Routes>
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/login" element={<div>Giriş hedefi</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(container.querySelector(".auth-shell")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Parolamı Unuttum" })).toBeInTheDocument();
    expect(screen.getByLabelText("E-posta Adresi")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("E-posta Adresi").closest("label")).toHaveClass("auth-field");
    fireEvent.click(screen.getByRole("link", { name: "Giriş sayfasına dön" }));
    expect(screen.getByText("Giriş hedefi")).toBeInTheDocument();
  });

  it("keeps the styled forgot-password form available after a safe request error", async () => {
    mocks.forgotPassword.mockRejectedValue(new Error("İstek tamamlanamadı. Lütfen tekrar deneyin."));
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("E-posta Adresi"), { target: { value: "user@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Sıfırlama Bağlantısı Gönder" }));

    expect(await screen.findByText("İstek tamamlanamadı. Lütfen tekrar deneyin.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sıfırlama Bağlantısı Gönder" })).toBeEnabled();
  });

  it("renders a valid reset link inside the Uniform auth shell", () => {
    const { container } = render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);

    expect(container.querySelector(".auth-shell")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Yeni Parola Belirle" })).toBeInTheDocument();
    expect(screen.getByLabelText("Yeni Parola")).toHaveAttribute("type", "password");
    expect(screen.getByLabelText("Yeni Parola Tekrar")).toHaveAttribute("type", "password");
  });

  it("toggles both reset password fields independently while keeping them masked by default", () => {
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    const password = screen.getByLabelText("Yeni Parola");
    const confirmation = screen.getByLabelText("Yeni Parola Tekrar");

    expect(password).toHaveAttribute("type", "password");
    expect(confirmation).toHaveAttribute("type", "password");

    fireEvent.click(screen.getByRole("button", { name: "Yeni parolayı göster" }));
    expect(password).toHaveAttribute("type", "text");
    expect(confirmation).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "Yeni parolayı gizle" })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Parola tekrarını göster" }));
    expect(password).toHaveAttribute("type", "text");
    expect(confirmation).toHaveAttribute("type", "text");

    fireEvent.click(screen.getByRole("button", { name: "Yeni parolayı gizle" }));
    expect(password).toHaveAttribute("type", "password");
    expect(confirmation).toHaveAttribute("type", "text");
  });

  it("shows in-app validation for a too-short password and does not submit", () => {
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni Parola"), { target: { value: "short" } });
    fireEvent.change(screen.getByLabelText("Yeni Parola Tekrar"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı Yenile" }));

    expect(screen.getByText("Parola 12–72 karakter arasında olmalıdır.", { selector: ".auth-field-error" })).toBeInTheDocument();
    expect(screen.getByLabelText("Yeni Parola")).toHaveAttribute("aria-invalid", "true");
    expect(mocks.resetPassword).not.toHaveBeenCalled();
  });

  it("shows in-app mismatch validation before the API call", () => {
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni Parola"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni Parola Tekrar"), { target: { value: "password-two" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı Yenile" }));

    expect(screen.getByText("Parola tekrarı yeni parolayla eşleşmiyor.")).toBeInTheDocument();
    expect(screen.getByLabelText("Yeni Parola Tekrar")).toHaveAttribute("aria-describedby", "confirm-password-error");
    expect(mocks.resetPassword).not.toHaveBeenCalled();
  });

  it("submits the existing reset API contract for a valid form", async () => {
    mocks.resetPassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni Parola"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni Parola Tekrar"), { target: { value: "password-one" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı Yenile" }));

    await waitFor(() => expect(mocks.resetPassword).toHaveBeenCalledWith("abc", "password-one"));
  });

  it("shows the stable same-current-password error and leaves the form retryable", async () => {
    const { ApiError } = await import("../api/ApiError");
    mocks.resetPassword.mockRejectedValue(new ApiError(400, "Bad Request", "safe", "PASSWORD_REUSE_NOT_ALLOWED"));
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni Parola"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni Parola Tekrar"), { target: { value: "password-one" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı Yenile" }));

    expect(await screen.findByText("Yeni şifreniz mevcut şifrenizle aynı olamaz.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Parolayı Yenile" })).toBeEnabled();
  });

  it("shows a styled success state with a login action", async () => {
    mocks.resetPassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    render(<MemoryRouter initialEntries={["/reset-password?token=abc"]}><ResetPasswordPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Yeni Parola"), { target: { value: "password-one" } });
    fireEvent.change(screen.getByLabelText("Yeni Parola Tekrar"), { target: { value: "password-one" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı Yenile" }));

    expect(await screen.findByRole("heading", { name: "Parolanız Yenilendi" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Parolanız başarıyla yenilendi");
    expect(screen.getByRole("link", { name: "Giriş Yap" })).toHaveAttribute("href", "/login");
  });

  it("returns from reset password to the login route", () => {
    render(
      <MemoryRouter initialEntries={["/reset-password?token=abc"]}>
        <Routes>
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/login" element={<div>Giriş hedefi</div>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("link", { name: "Giriş sayfasına dön" }));
    expect(screen.getByText("Giriş hedefi")).toBeInTheDocument();
  });

  it("Google-only account does not see change-password form", () => {
    mocks.auth.mockReturnValue({ ...baseAuth, user: { id: 1, email: "g@example.com", fullName: "G", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, hasLocalPassword: false } });
    render(<MemoryRouter><SecuritySettingsPage /></MemoryRouter>);
    expect(screen.getByText(/yerel bir şifresi bulunmuyor/)).toBeInTheDocument(); expect(screen.queryByRole("button", { name: /Şifreyi Değiştir/i })).not.toBeInTheDocument();
  });

  it("password user change clears local auth and redirects", async () => {
    mocks.changePassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    mocks.auth.mockReturnValue({ ...baseAuth, user: { id: 1, email: "u@example.com", fullName: "U", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, hasLocalPassword: true } });
    render(<MemoryRouter><SecuritySettingsPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Mevcut şifre"), { target: { value: "old-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "new-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "new-password" } });
    fireEvent.click(screen.getByRole("button", { name: /Şifreyi Değiştir/i }));
    await waitFor(() => expect(mocks.clearSession).toHaveBeenCalled());
  });
});
