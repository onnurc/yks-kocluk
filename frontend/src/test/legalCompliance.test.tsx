import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RegisterPage } from "../pages/RegisterPage";
import { CheckoutSection } from "../subscriptionCheckout/CheckoutSection";
import { LegalOnboardingRoute } from "../routes/LegalOnboardingRoute";
import { OAuthCallbackPage } from "../pages/OAuthCallbackPage";
import { LegalOnboardingPage } from "../pages/LegalOnboardingPage";
import { PrivacySettingsPage } from "../pages/PrivacySettingsPage";
import { ApiError } from "../api/ApiError";

const mocks = vi.hoisted(() => ({
  auth: {
    user: null as null | { id: number; email: string; fullName: string; role: "STUDENT" | "COACH" | "ADMIN"; status: "ACTIVE"; emailVerified: boolean; legalOnboardingCompleted: boolean },
    isAuthenticated: false,
    isLoading: false,
    isSuspended: false,
    register: vi.fn(),
    login: vi.fn(),
    completeOAuthLogin: vi.fn(),
    logout: vi.fn(),
    refreshCurrentUser: vi.fn(),
    clearSession: vi.fn(),
  },
  register: vi.fn(),
  checkout: vi.fn(),
  completeOnboarding: vi.fn(),
  getCurrentUser: vi.fn(),
  getMarketing: vi.fn(),
  updateMarketing: vi.fn(),
  getPrivacy: vi.fn(),
  updatePrivacy: vi.fn(),
  getDeletion: vi.fn(),
  deleteAccount: vi.fn(),
  withdraw: vi.fn(),
  legalReload: vi.fn(),
}));

vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth }));
vi.mock("../subscriptionCheckout/subscriptionCheckoutApi", () => ({
  subscriptionCheckoutApi: { checkout: mocks.checkout },
}));
vi.mock("../auth/authApi", () => ({
  authApi: { completeLegalOnboarding: mocks.completeOnboarding, getCurrentUser: mocks.getCurrentUser },
}));
vi.mock("../privacy/privacyApi", () => ({
  privacyApi: {
    getMarketingPreferences: mocks.getMarketing,
    updateMarketingPreferences: mocks.updateMarketing,
    getPrivacyPreferences: mocks.getPrivacy,
    updatePrivacyPreferences: mocks.updatePrivacy,
    getAccountDeletion: mocks.getDeletion,
    deleteAccount: mocks.deleteAccount,
    withdrawExplicitConsent: mocks.withdraw,
  },
}));

const documentFor = (type: string, id: number, title: string) => ({
  id,
  type,
  version: "1.0",
  title,
  content: `${title} backend içeriği`,
  contentHash: `hash-${id}`,
  effectiveAt: "2026-01-01T00:00:00Z",
});

const legalDocuments = {
  TERMS_OF_USE: documentFor("TERMS_OF_USE", 3, "Kullanım Koşulları"),
  EXPLICIT_CONSENT: documentFor("EXPLICIT_CONSENT", 2, "Açık Rıza Metni"),
  KVKK_NOTICE: documentFor("KVKK_NOTICE", 1, "KVKK Aydınlatma Metni"),
  PRE_INFORMATION_FORM: documentFor("PRE_INFORMATION_FORM", 6, "Ön Bilgilendirme Formu"),
  DISTANCE_SALES_AGREEMENT: documentFor("DISTANCE_SALES_AGREEMENT", 7, "Mesafeli Satış Sözleşmesi"),
  REFUND_CANCELLATION_POLICY: documentFor("REFUND_CANCELLATION_POLICY", 8, "İade / İptal Politikası"),
  COOKIE_POLICY: documentFor("COOKIE_POLICY", 5, "Çerez Politikası"),
};

vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: (types: string[]) => ({
    documents: Object.fromEntries(types.map((type) => [type, legalDocuments[type as keyof typeof legalDocuments]])),
    loading: false,
    error: null,
    ready: true,
    reload: mocks.legalReload,
  }),
}));

afterEach(cleanup);

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(mocks.auth, {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    isSuspended: false,
  });
  mocks.getMarketing.mockResolvedValue({
    email: { granted: true, grantedAt: null, withdrawnAt: null },
    sms: { granted: false, grantedAt: null, withdrawnAt: null },
  });
  mocks.updateMarketing.mockImplementation(async (request: { email?: boolean; sms?: boolean }) => ({
    email: { granted: request.email ?? true, grantedAt: null, withdrawnAt: null },
    sms: { granted: request.sms ?? false, grantedAt: null, withdrawnAt: null },
  }));
  mocks.getPrivacy.mockResolvedValue({ necessaryAllowed: true, analyticsAllowed: false, marketingAllowed: false, cookiePolicyDocumentId: 5, policyVersion: "1.0", grantedAt: null, updatedAt: null });
  mocks.updatePrivacy.mockImplementation(async (request: object) => ({ ...request, policyVersion: "1.0", grantedAt: null, updatedAt: null }));
  mocks.getDeletion.mockRejectedValue(new ApiError(404, "Not found", "Talep yok", "ACCOUNT_DELETION_NOT_REQUESTED"));
});

describe("registration legal flow", () => {
  it("requires Terms and Explicit Consent, keeps marketing optional, sends current IDs, and has no guardian UI", async () => {
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);

    expect(screen.queryByText(/veli onayı/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kayıt Ol" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "KVKK Aydınlatma Metni" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("KVKK Aydınlatma Metni backend içeriği");
    fireEvent.click(screen.getByLabelText("Hukuki metni kapat"));

    fireEvent.change(screen.getByLabelText("Ad:"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Soyad:"), { target: { value: "Yılmaz" } });
    fireEvent.change(screen.getByLabelText("E-posta:"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText(/Şifre/), { target: { value: "Password123!" } });
    fireEvent.change(screen.getByLabelText("Rol Seçimi:"), { target: { value: "COACH" } });
    fireEvent.click(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/));
    fireEvent.click(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));

    await waitFor(() => expect(mocks.auth.register).toHaveBeenCalledWith(expect.objectContaining({
      acceptedTermsDocumentId: 3,
      acceptedExplicitConsentDocumentId: 2,
      marketingEmailOptIn: false,
      marketingSmsOptIn: false,
    })));
  });

  it("shows stale-document guidance and re-fetches documents", async () => {
    mocks.auth.register.mockRejectedValue(new ApiError(409, "Stale", "Stale", "LEGAL_DOCUMENT_NOT_CURRENT"));
    render(<MemoryRouter><RegisterPage /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("Ad:"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Soyad:"), { target: { value: "Yılmaz" } });
    fireEvent.change(screen.getByLabelText("E-posta:"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText(/Şifre/), { target: { value: "Password123!" } });
    fireEvent.change(screen.getByLabelText("Rol Seçimi:"), { target: { value: "COACH" } });
    fireEvent.click(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/));
    fireEvent.click(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Kayıt Ol" }));
    expect(await screen.findByText(/yeni bir sürümü yayımlandı/i)).toBeInTheDocument();
    expect(mocks.legalReload).toHaveBeenCalled();
  });
});

describe("OAuth and onboarding gates", () => {
  it("redirects an incomplete authenticated user away from protected product routes", () => {
    Object.assign(mocks.auth, { user: { id: 9, email: "oauth@example.com", fullName: "OAuth User", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: false }, isAuthenticated: true });
    render(
      <MemoryRouter initialEntries={["/coaches"]}>
        <Routes>
          <Route element={<LegalOnboardingRoute />}><Route path="/coaches" element={<p>Koçlar</p>} /></Route>
          <Route path="/legal-onboarding" element={<p>Onboarding ekranı</p>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText("Onboarding ekranı")).toBeInTheDocument();
    expect(screen.queryByText("Koçlar")).not.toBeInTheDocument();
  });

  it("exchanges the OAuth code and redirects incomplete users to onboarding", async () => {
    mocks.auth.completeOAuthLogin.mockResolvedValue({ id: 9, email: "oauth@example.com", fullName: "OAuth User", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: false });
    render(
      <MemoryRouter initialEntries={["/oauth/callback?code=once"]}>
        <Routes>
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/legal-onboarding" element={<p>Onboarding ekranı</p>} />
        </Routes>
      </MemoryRouter>
    );
    await waitFor(() => expect(mocks.auth.completeOAuthLogin).toHaveBeenCalledWith("once"));
    expect(await screen.findByText("Onboarding ekranı")).toBeInTheDocument();
  });

  it("sends current Terms and Explicit Consent IDs during onboarding", async () => {
    Object.assign(mocks.auth, { user: { id: 9, email: "oauth@example.com", fullName: "OAuth User", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: false }, isAuthenticated: true });
    mocks.completeOnboarding.mockResolvedValue({ legalOnboardingCompleted: true });
    mocks.getCurrentUser.mockResolvedValue({ ...mocks.auth.user, legalOnboardingCompleted: true });
    render(<MemoryRouter><LegalOnboardingPage /></MemoryRouter>);
    fireEvent.click(screen.getByLabelText(/Kullanım Koşulları.*Zorunlu/));
    fireEvent.click(screen.getByLabelText(/Açık Rıza Metni.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Onayla ve devam et" }));
    await waitFor(() => expect(mocks.completeOnboarding).toHaveBeenCalledWith(expect.objectContaining({ termsDocumentId: 3, explicitConsentDocumentId: 2 })));
    expect(mocks.auth.refreshCurrentUser).toHaveBeenCalled();
  });
});

describe("checkout legal flow", () => {
  it("requires one checkbox and sends all three current document IDs", async () => {
    Object.assign(mocks.auth, { user: { id: 4, email: "student@example.com", fullName: "Student", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true }, isAuthenticated: true });
    mocks.checkout.mockResolvedValue({ checkoutUrl: "https://checkout.stub.local/session" });
    render(<MemoryRouter><CheckoutSection coachId={10} coachName="Koç" packageId={20} packageName="Paket" price={1000} dashboardData={null} /></MemoryRouter>);
    expect(screen.getByRole("button", { name: "Sözleşmeleri Kabul Edin" })).toBeDisabled();
    fireEvent.click(screen.getByLabelText(/Ön Bilgilendirme Formu.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Ödemeye Geç" }));
    await waitFor(() => expect(mocks.checkout).toHaveBeenCalledWith({
      coachId: 10,
      packageId: 20,
      preInformationDocumentId: 6,
      distanceSalesDocumentId: 7,
      refundCancellationPolicyDocumentId: 8,
      legalDocumentsAccepted: true,
    }));
  });

  it("does not expose a payment redirect after legal validation failure", async () => {
    Object.assign(mocks.auth, { user: { id: 4, email: "student@example.com", fullName: "Student", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true }, isAuthenticated: true });
    mocks.checkout.mockRejectedValue(new ApiError(409, "Stale", "Stale", "CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT"));
    render(<MemoryRouter><CheckoutSection coachId={10} coachName="Koç" packageId={20} packageName="Paket" price={1000} dashboardData={null} /></MemoryRouter>);
    fireEvent.click(screen.getByLabelText(/Ön Bilgilendirme Formu.*Zorunlu/));
    fireEvent.click(screen.getByRole("button", { name: "Ödemeye Geç" }));
    expect(await screen.findByText(/yeni bir sürümü yayımlandı/i)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /İyzico Ödeme Sayfası/ })).not.toBeInTheDocument();
  });
});

describe("privacy settings", () => {
  beforeEach(() => {
    Object.assign(mocks.auth, { user: { id: 4, email: "student@example.com", fullName: "Student", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true }, isAuthenticated: true });
  });

  it("loads independent marketing choices and saves cookies with the current policy ID", async () => {
    render(<MemoryRouter><PrivacySettingsPage /></MemoryRouter>);
    const necessary = await screen.findByLabelText(/Zorunlu/);
    expect(necessary).toBeChecked();
    expect(necessary).toBeDisabled();
    fireEvent.click(screen.getByLabelText(/E-posta ile kampanya/));
    await waitFor(() => expect(mocks.updateMarketing).toHaveBeenCalledWith({ email: false }));
    fireEvent.click(screen.getByLabelText(/Analitik/));
    fireEvent.click(screen.getByRole("button", { name: "Çerez tercihlerini kaydet" }));
    await waitFor(() => expect(mocks.updatePrivacy).toHaveBeenCalledWith(expect.objectContaining({ necessaryAllowed: true, analyticsAllowed: true, cookiePolicyDocumentId: 5 })));
  });

  it("requires DELETE and clears the local session after successful deletion", async () => {
    mocks.deleteAccount.mockResolvedValue({ status: "COMPLETED", requestedAt: "2026-01-01", completedAt: "2026-01-01" });
    render(
      <MemoryRouter initialEntries={["/privacy"]}>
        <Routes>
          <Route path="/privacy" element={<PrivacySettingsPage />} />
          <Route path="/login" element={<p>Giriş ekranı</p>} />
        </Routes>
      </MemoryRouter>
    );
    fireEvent.click(await screen.findByRole("button", { name: "Hesabımı sil" }));
    const confirm = screen.getByRole("button", { name: "Hesabı sil" });
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Hesap silme onayı"), { target: { value: "DELETE" } });
    fireEvent.click(confirm);
    await waitFor(() => expect(mocks.deleteAccount).toHaveBeenCalled());
    expect(mocks.auth.clearSession).toHaveBeenCalled();
    expect(await screen.findByText("Giriş ekranı")).toBeInTheDocument();
  });

  it("withdraws Explicit Consent, refreshes auth state, and redirects to onboarding", async () => {
    mocks.withdraw.mockResolvedValue({ legalOnboardingCompleted: false, withdrawnAt: "2026-01-01" });
    render(
      <MemoryRouter initialEntries={["/privacy"]}>
        <Routes>
          <Route path="/privacy" element={<PrivacySettingsPage />} />
          <Route path="/legal-onboarding" element={<p>Onboarding ekranı</p>} />
        </Routes>
      </MemoryRouter>
    );
    fireEvent.click(await screen.findByRole("button", { name: "Açık rızayı geri çek" }));
    fireEvent.click(screen.getByRole("button", { name: "Rızayı geri çek" }));
    await waitFor(() => expect(mocks.withdraw).toHaveBeenCalled());
    expect(mocks.auth.refreshCurrentUser).toHaveBeenCalled();
    expect(await screen.findByText("Onboarding ekranı")).toBeInTheDocument();
  });
});
