import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../auth/authTypes";
import { AppLayout } from "../components/AppLayout";
import { PrivacySettingsPage } from "../pages/PrivacySettingsPage";
import { TestAuthProvider } from "./TestAuthProvider";

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  logout: vi.fn(),
  useNotificationSocket: vi.fn(),
  getStudentProfile: vi.fn(),
  getCoachProfile: vi.fn(),
  getMarketing: vi.fn(),
  updateMarketing: vi.fn(),
  getPrivacy: vi.fn(),
  updatePrivacy: vi.fn(),
  getDeletion: vi.fn(),
  deleteAccount: vi.fn(),
  withdraw: vi.fn(),
}));

vi.mock("../messaging/messagingApi", () => ({
  messagingApi: { listConversations: mocks.listConversations },
}));
vi.mock("../messaging/useNotificationSocket", () => ({
  useNotificationSocket: mocks.useNotificationSocket,
}));
vi.mock("../account/accountApi", () => ({
  accountApi: {
    getStudentProfile: mocks.getStudentProfile,
    getCoachProfile: mocks.getCoachProfile,
  },
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
vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: () => ({
    documents: { COOKIE_POLICY: { id: 5, type: "COOKIE_POLICY", version: "1.0", title: "Çerez Politikası", content: "Metin", contentHash: "hash", effectiveAt: "2026-01-01T00:00:00Z" } },
    loading: false,
    error: null,
    ready: true,
    reload: vi.fn(),
  }),
}));

const student: CurrentUser = {
  id: 9,
  email: "selin@example.com",
  fullName: "Selin Erdem",
  role: "STUDENT",
  status: "ACTIVE",
  emailVerified: true,
  legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

function renderShell(path: string, user: CurrentUser = student) {
  return render(
    <TestAuthProvider value={{ user, isAuthenticated: true, logout: mocks.logout }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<div>Dashboard body</div>} />
            <Route path="/messages" element={<div>Messages body</div>} />
            <Route path="/messages/:conversationId" element={<div>Conversation body</div>} />
            <Route path="/privacy" element={<PrivacySettingsPage />} />
            <Route path="/admin" element={<div>Admin body</div>} />
          </Route>
          <Route path="/login" element={<div>Login body</div>} />
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

afterEach(cleanup);

describe("shared authenticated product shell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([]);
    mocks.logout.mockResolvedValue(undefined);
    mocks.getStudentProfile.mockResolvedValue({ profileImageUrl: null });
    mocks.getCoachProfile.mockResolvedValue({ profileImageUrl: null });
    mocks.getMarketing.mockResolvedValue({ email: { granted: true }, sms: { granted: false } });
    mocks.getPrivacy.mockResolvedValue({ necessaryAllowed: true, analyticsAllowed: false, marketingAllowed: false, cookiePolicyDocumentId: 5, policyVersion: "1.0", grantedAt: null, updatedAt: null });
    mocks.getDeletion.mockResolvedValue(null);
  });

  it("renders the student's saved profile image in every responsive avatar slot", async () => {
    mocks.getStudentProfile.mockResolvedValue({ profileImageUrl: "/api/v1/public/media/41" });
    const { container } = renderShell("/dashboard");

    await waitFor(() => expect(container.querySelectorAll('.app-layout__avatar img[src="/api/v1/public/media/41"]')).toHaveLength(3));
    expect(mocks.getStudentProfile).toHaveBeenCalledTimes(1);
    expect(mocks.getCoachProfile).not.toHaveBeenCalled();
  });

  it("renders the coach's saved profile image in every responsive avatar slot", async () => {
    mocks.getCoachProfile.mockResolvedValue({ profileImageUrl: "/api/v1/public/media/57" });
    const { container } = renderShell("/dashboard", { ...student, role: "COACH", fullName: "Ece Koç" });

    await waitFor(() => expect(container.querySelectorAll('.app-layout__avatar img[src="/api/v1/public/media/57"]')).toHaveLength(3));
    expect(mocks.getCoachProfile).toHaveBeenCalledTimes(1);
    expect(mocks.getStudentProfile).not.toHaveBeenCalled();
  });

  it("keeps initials as the safe fallback when no profile image exists or an image fails", async () => {
    const { container } = renderShell("/dashboard");

    await waitFor(() => expect(mocks.getStudentProfile).toHaveBeenCalledTimes(1));
    expect(container.querySelectorAll(".app-layout__avatar img")).toHaveLength(0);
    expect(screen.getAllByText("SE")).toHaveLength(3);

    mocks.getStudentProfile.mockResolvedValue({ profileImageUrl: "/api/v1/public/media/unavailable" });
    const failedImageView = renderShell("/dashboard");
    await waitFor(() => expect(failedImageView.container.querySelector(".app-layout__avatar img")).toBeInTheDocument());
    const failedImage = failedImageView.container.querySelector(".app-layout__avatar img")!;
    fireEvent.error(failedImage);
    expect(failedImageView.container.querySelector(".app-layout__avatar")?.textContent).toBe("SE");
  });

  it.each([
    ["/dashboard", "Dashboard body"],
    ["/messages", "Messages body"],
    ["/messages/12", "Conversation body"],
    ["/privacy", "Gizlilik ve hukuki tercihler"],
  ])("keeps the same Uniform shell at %s", (path, body) => {
    const { container } = renderShell(path);

    expect(screen.getByText(body)).toBeInTheDocument();
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(container.querySelector(".app-layout__sidebar")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Uniform Akademi ana sayfa" })).toBeInTheDocument();
    expect(screen.getByText("Mentorluk")).toBeInTheDocument();
    expect(screen.queryByText("YKS Koçluk")).not.toBeInTheDocument();
  });

  it("keeps the privacy page available to coaches in the authenticated shell", () => {
    const { container } = renderShell("/privacy", { ...student, role: "COACH", fullName: "Ece Koç" });

    expect(screen.getByRole("heading", { level: 1, name: "Gizlilik ve hukuki tercihler" })).toBeInTheDocument();
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(container.querySelector(".app-layout__sidebar")).toBeInTheDocument();
  });

  it("renders the responsive authenticated header and opens its real mobile menu", () => {
    const { container } = renderShell("/dashboard");
    const menuButton = screen.getByRole("button", { name: "Menüyü aç" });

    expect(menuButton).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(menuButton);

    expect(screen.getByRole("button", { name: "Menüyü kapat" })).toHaveAttribute("aria-expanded", "true");
    expect(container.querySelector(".app-layout__sidebar")).toHaveClass("is-open");
    expect(screen.getByRole("button", { name: "Hesap menüsünü aç" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ayarlar" })).not.toBeInTheDocument();
  });

  it("uses only real student product destinations and keeps logout in the menu", async () => {
    renderShell("/dashboard");
    const navigation = screen.getByRole("navigation", { name: "Ürün navigasyonu" });

    expect(within(navigation).getByRole("link", { name: "Panel" })).toHaveAttribute("href", "/dashboard");
    expect(within(navigation).getByRole("link", { name: "Koçlar" })).toHaveAttribute("href", "/coaches");
    expect(within(navigation).getByRole("link", { name: "Mesajlar" })).toHaveAttribute("href", "/messages");
    expect(within(navigation).getByRole("link", { name: "Görüşmeler" })).toHaveAttribute("href", "/bookings");
    expect(within(navigation).getByRole("link", { name: "Hesabım" })).toHaveAttribute("href", "/account");
    expect(within(navigation).queryByRole("link", { name: "Ayarlar" })).not.toBeInTheDocument();
    expect(screen.queryByText("Courses")).not.toBeInTheDocument();
    expect(screen.queryByText("Grades")).not.toBeInTheDocument();

    const logout = screen.getByRole("button", { name: "Çıkış yap" });
    expect(logout).not.toHaveStyle({ backgroundColor: "#dc3545" });
    fireEvent.click(logout);

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
    expect(await screen.findByText("Login body")).toBeInTheDocument();
  });

  it("keeps Ayarlar out of the coach navigation and responsive drawer", () => {
    renderShell("/dashboard", { ...student, role: "COACH", fullName: "Ece Koç" });
    fireEvent.click(screen.getByRole("button", { name: "Menüyü aç" }));

    const navigation = screen.getByRole("navigation", { name: "Ürün navigasyonu" });
    expect(within(navigation).getByRole("link", { name: "Panel" })).toHaveAttribute("href", "/dashboard");
    expect(within(navigation).getByRole("link", { name: "Mesajlar" })).toHaveAttribute("href", "/messages");
    expect(within(navigation).getByRole("link", { name: "Hesabım" })).toHaveAttribute("href", "/account");
    expect(within(navigation).queryByRole("link", { name: "Ayarlar" })).not.toBeInTheDocument();
  });

  it("keeps student-only navigation out of the admin shell", () => {
    renderShell("/admin", { ...student, role: "ADMIN", fullName: "Deniz Admin" });
    const navigation = screen.getByRole("navigation", { name: "Ürün navigasyonu" });

    expect(within(navigation).getByRole("link", { name: "Admin Paneli" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Kullanıcılar" })).toHaveAttribute("href", "/admin/users");
    expect(within(navigation).getByRole("link", { name: "Koçlar" })).toHaveAttribute("href", "/admin/coaches");
    expect(within(navigation).getByRole("link", { name: "Koç Başvuruları" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Abonelik & Finans" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Seanslar" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Raporlar" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Mesaj Gözlemi" })).toBeInTheDocument();
    expect(within(navigation).queryByRole("link", { name: "Görüşmeler" })).not.toBeInTheDocument();
    expect(mocks.listConversations).not.toHaveBeenCalled();
  });
});
