import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "../pages/LoginPage";
import { RegisterPage } from "../pages/RegisterPage";
import { ApiError } from "../api/ApiError";

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  legalReload: vi.fn(),
}));

vi.mock("../auth/AuthProvider", () => ({
  useAuth: () => ({
    user: null,
    isAuthenticated: false,
    isLoading: false,
    isSuspended: false,
    login: mocks.login,
    register: mocks.register,
  }),
}));

vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: () => ({
    documents: {
      TERMS_OF_USE: { id: 3, type: "TERMS_OF_USE", version: "1", title: "Kullanım Koşulları", content: "Koşullar", contentHash: "a", effectiveAt: "2026-01-01" },
      EXPLICIT_CONSENT: { id: 2, type: "EXPLICIT_CONSENT", version: "1", title: "Açık Rıza Metni", content: "Rıza", contentHash: "b", effectiveAt: "2026-01-01" },
      KVKK_NOTICE: { id: 1, type: "KVKK_NOTICE", version: "1", title: "KVKK Aydınlatma Metni", content: "Aydınlatma", contentHash: "c", effectiveAt: "2026-01-01" },
    },
    loading: false,
    error: null,
    ready: true,
    reload: mocks.legalReload,
  }),
}));

beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe("auth page family", () => {
  const fillRegistration = (email: string) => {
    fireEvent.change(screen.getByLabelText("Ad:"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Soyad:"), { target: { value: "Yılmaz" } });
    fireEvent.change(screen.getByLabelText("E-posta:"), { target: { value: email } });
    fireEvent.change(screen.getByLabelText(/Şifre/), { target: { value: "Password123!" } });
    fireEvent.change(screen.getByLabelText("Doğum Tarihi:"), { target: { value: "2008-05-01" } });
    fireEvent.click(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/));
    fireEvent.click(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/));
  };

  it("preserves email/password, Google, forgot-password and register login actions", async () => {
    mocks.login.mockResolvedValue(undefined);
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    expect(screen.getByRole("heading", { level: 1, name: "Tekrar Hoş Geldin!" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google ile Giriş Yap" })).toHaveAttribute("href", "http://localhost:8080/oauth2/authorization/google");
    expect(screen.getByRole("link", { name: "Parolamı Unuttum" })).toHaveAttribute("href", "/forgot-password");
    expect(screen.getByRole("link", { name: "Kayıt Ol" })).toHaveAttribute("href", "/register");

    fireEvent.change(screen.getByLabelText("E-posta Adresi"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText("Şifre"), { target: { value: "Password123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Giriş Yap" }));
    await waitFor(() => expect(mocks.login).toHaveBeenCalledWith("ada@example.com", "Password123!"));
  });

  it("keeps a safe login error state", async () => {
    mocks.login.mockRejectedValue(new Error("Giriş bilgileri geçersiz."));
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("E-posta Adresi"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText("Şifre"), { target: { value: "yanlis" } });
    fireEvent.click(screen.getByRole("button", { name: "Giriş Yap" }));
    expect(await screen.findByText("Giriş bilgileri geçersiz.")).toBeInTheDocument();
  });

  it("preserves registration fields and independent legal and marketing choices", async () => {
    mocks.register.mockResolvedValue(undefined);
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);

    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google ile Kayıt Ol" })).toHaveAttribute("href", "http://localhost:8080/oauth2/authorization/google");
    expect(screen.getByLabelText("Ad:")).toBeRequired();
    expect(screen.getByLabelText("Soyad:")).toBeRequired();
    expect(screen.getByLabelText("E-posta:")).toBeRequired();
    expect(screen.getByLabelText(/Şifre/)).toBeRequired();
    expect(screen.getByLabelText("Doğum Tarihi:")).toBeRequired();
    expect(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/)).toBeRequired();
    expect(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/)).toBeRequired();
    expect(screen.getByLabelText(/e-posta almak istiyorum/)).not.toBeRequired();
    expect(screen.getByLabelText(/SMS almak istiyorum/)).not.toBeRequired();
    expect(screen.getByRole("link", { name: "Giriş Yap" })).toHaveAttribute("href", "/login");

    fireEvent.change(screen.getByLabelText("Ad:"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Soyad:"), { target: { value: "Yılmaz" } });
    fireEvent.change(screen.getByLabelText("E-posta:"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText(/Şifre/), { target: { value: "Password123!" } });
    fireEvent.change(screen.getByLabelText("Doğum Tarihi:"), { target: { value: "2008-05-01" } });
    fireEvent.click(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/));
    fireEvent.click(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));

    await waitFor(() => expect(mocks.register).toHaveBeenCalledWith(expect.objectContaining({
      email: "ada@example.com",
      fullName: "Ada Yılmaz",
      dateOfBirth: "2008-05-01",
      acceptedTermsDocumentId: 3,
      acceptedExplicitConsentDocumentId: 2,
      marketingEmailOptIn: false,
      marketingSmsOptIn: false,
    })));
    expect(mocks.register.mock.calls[0][0]).not.toHaveProperty("role");
  });

  it("shows blank and malformed registration email errors only below the field", () => {
    const { unmount } = render(<MemoryRouter><RegisterPage /></MemoryRouter>);
    fillRegistration("");
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));
    expect(screen.getByText("E-posta adresi zorunludur.", { selector: ".auth-field-error" })).toBeInTheDocument();
    expect(screen.getByLabelText("E-posta:")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("E-posta:")).toHaveAttribute("aria-describedby", "register-email-error");
    expect(mocks.register).not.toHaveBeenCalled();

    unmount();
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);
    fillRegistration("emre..test@gmail.com");
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));
    expect(screen.getByText("Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com", { selector: ".auth-field-error" })).toBeInTheDocument();
    expect(mocks.register).not.toHaveBeenCalled();
  });

  it("maps a backend email validation error to one field message without technical text", async () => {
    mocks.register.mockRejectedValue(new ApiError(400, "Bad Request", "Doğrulama hatası", undefined, [
      { field: "email", message: "Geçerli bir e-posta girin" },
    ]));
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);
    fillRegistration("frontend-accepted@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));

    const messages = await screen.findAllByText("Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com");
    expect(messages).toHaveLength(1);
    expect(screen.queryByText(/Bad Request|Doğrulama hatası|email:/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText("E-posta:")).toHaveFocus();
  });

  it("shows a safe Turkish top-level message for an unexpected registration failure", async () => {
    mocks.register.mockRejectedValue(new ApiError(500, "Server Error", "internal trace"));
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);
    fillRegistration("ada@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));

    expect(await screen.findByText("İşlem sırasında bir hata oluştu. Lütfen tekrar deneyin.")).toBeInTheDocument();
    expect(screen.queryByText(/Server Error|internal trace/i)).not.toBeInTheDocument();
  });
});
