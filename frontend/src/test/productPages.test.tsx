import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/ApiError";
import type { CurrentUser } from "../auth/authTypes";
import type { SessionResponse } from "../booking/bookingTypes";
import { AppLayout } from "../components/AppLayout";
import { BookingsPage } from "../pages/BookingsPage";
import { SecuritySettingsPage } from "../pages/SecuritySettingsPage";
import { TestAuthProvider } from "./TestAuthProvider";

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  listMySessions: vi.fn(),
  changePassword: vi.fn(),
  clearSession: vi.fn(),
  useNotificationSocket: vi.fn(),
}));

vi.mock("../messaging/messagingApi", () => ({ messagingApi: { listConversations: mocks.listConversations } }));
vi.mock("../messaging/useNotificationSocket", () => ({ useNotificationSocket: mocks.useNotificationSocket }));
vi.mock("../booking/bookingApi", () => ({ bookingApi: { listMySessions: mocks.listMySessions } }));
vi.mock("../auth/authApi", () => ({ authApi: { changePassword: mocks.changePassword } }));
vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: () => ({ documents: {}, loading: false, error: null, reload: vi.fn() }),
}));

const student: CurrentUser = {
  id: 21,
  email: "selin@example.com",
  fullName: "Selin Erdem",
  role: "STUDENT",
  status: "ACTIVE",
  emailVerified: true,
  legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

const plannedSession: SessionResponse = {
  id: 41,
  coachProfileId: 7,
  coachName: "Ece Demir",
  studentName: "Selin Erdem",
  availabilityId: 61,
  status: "PLANNED",
  startTime: "2026-09-12T11:00:00+03:00",
  endTime: "2026-09-12T12:00:00+03:00",
  meetLink: "https://meet.example.test/session-41",
};

function renderProductPage(path: string, user: CurrentUser = student) {
  return render(
    <TestAuthProvider value={{ user, isAuthenticated: true, clearSession: mocks.clearSession }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/bookings" element={<BookingsPage />} />
            <Route path="/security" element={<SecuritySettingsPage />} />
            <Route path="/dashboard" element={<div>Dashboard body</div>} />
            <Route path="/messages" element={<div>Messages body</div>} />
          </Route>
          <Route path="/coaches" element={<div>Coaches body</div>} />
          <Route path="/login" element={<div>Login body</div>} />
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

afterEach(cleanup);

describe("authenticated meetings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([]);
  });

  it("renders real session fields in the shared shell and activates Görüşmeler", async () => {
    mocks.listMySessions.mockResolvedValue([
      plannedSession,
      { ...plannedSession, id: 42, coachName: "Mert Kaya", status: "PLANNED", meetLink: null },
      { ...plannedSession, id: 43, coachName: "Derya Yalçın", status: "COMPLETED", meetLink: "https://meet.example.test/expired" },
    ]);
    const { container } = renderProductPage("/bookings");

    expect(await screen.findByText("Ece Demir")).toBeInTheDocument();
    expect(screen.getByText("Mert Kaya")).toBeInTheDocument();
    expect(screen.getByText("Derya Yalçın")).toBeInTheDocument();
    expect(screen.getAllByText("11:00 – 12:00")).toHaveLength(3);
    expect(screen.getByText("Tamamlandı")).toBeInTheDocument();
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Görüşmeler" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Koçları Keşfet" })).toHaveAttribute("href", "/coaches");
    expect(screen.queryByText(/Matematik Analizi|Termodinamik|Can K\./)).not.toBeInTheDocument();

    const joinLinks = screen.getAllByRole("link", { name: /ile görüşmeye katıl/ });
    expect(joinLinks).toHaveLength(1);
    expect(joinLinks[0]).toHaveAttribute("href", plannedSession.meetLink);
    expect(screen.getByLabelText("Görüşme bağlantısı henüz hazır değil")).toBeInTheDocument();
  });

  it("shows one real discovery CTA in the branded empty state", async () => {
    mocks.listMySessions.mockResolvedValue([]);
    renderProductPage("/bookings");

    const emptyState = await screen.findByLabelText("Boş görüşmeler durumu");
    expect(within(emptyState).getByText("Kayıtlı görüşmeniz bulunmuyor.")).toBeInTheDocument();
    expect(within(emptyState).getAllByRole("link", { name: "Koçları Keşfet" })).toHaveLength(1);
    expect(screen.getAllByRole("link", { name: "Koçları Keşfet" })).toHaveLength(1);
    fireEvent.click(within(emptyState).getByRole("link", { name: "Koçları Keşfet" }));
    expect(screen.getByText("Coaches body")).toBeInTheDocument();
  });

  it("keeps API failures safe and retryable", async () => {
    mocks.listMySessions.mockRejectedValueOnce(new Error("internal stack trace"));
    renderProductPage("/bookings");

    expect(await screen.findByRole("alert")).toHaveTextContent("Görüşmeler yüklenemedi");
    expect(screen.queryByText("internal stack trace")).not.toBeInTheDocument();
    mocks.listMySessions.mockResolvedValueOnce([]);
    fireEvent.click(screen.getByRole("button", { name: "Yeniden dene" }));
    expect(await screen.findByLabelText("Boş görüşmeler durumu")).toBeInTheDocument();
  });
});

describe("authenticated security settings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([]);
  });

  it("renders only the real password setting in the shared shell and activates Ayarlar", () => {
    const { container } = renderProductPage("/security");

    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ayarlar" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("heading", { name: "Şifre Değiştir" })).toBeInTheDocument();
    expect(screen.getByLabelText("Mevcut şifre")).toBeInTheDocument();
    expect(screen.getByLabelText("Yeni şifre")).toBeInTheDocument();
    expect(screen.getByLabelText("Yeni şifre tekrar")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Şifreyi Değiştir" })).toBeInTheDocument();
    expect(screen.queryByText("Ayarlar → Güvenlik → Şifre Değiştir")).not.toBeInTheDocument();
    expect(screen.queryByText(/bildirim tercihleri|tema ayarları/i)).not.toBeInTheDocument();
  });

  it("uses the existing password client and preserves session invalidation", async () => {
    mocks.changePassword.mockResolvedValue({ message: "ok", reloginRequired: true });
    renderProductPage("/security");
    fireEvent.change(screen.getByLabelText("Mevcut şifre"), { target: { value: "old-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "new-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "new-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi Değiştir" }));

    await waitFor(() => expect(mocks.changePassword).toHaveBeenCalledWith("old-password", "new-password"));
    expect(mocks.clearSession).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("Login body")).toBeInTheDocument();
  });

  it("validates password confirmation before calling the client", () => {
    renderProductPage("/security");
    fireEvent.change(screen.getByLabelText("Mevcut şifre"), { target: { value: "old-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "new-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "different-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi Değiştir" }));

    expect(screen.getByText("Yeni şifreler eşleşmiyor.")).toBeInTheDocument();
    expect(mocks.changePassword).not.toHaveBeenCalled();
  });

  it("shows password errors safely without changing the existing client contract", async () => {
    mocks.changePassword.mockRejectedValue(new ApiError(400, "Şifre değiştirilemedi", "Mevcut şifre hatalı.", "WRONG_CURRENT_PASSWORD"));
    renderProductPage("/security");
    fireEvent.change(screen.getByLabelText("Mevcut şifre"), { target: { value: "wrong-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre"), { target: { value: "new-password" } });
    fireEvent.change(screen.getByLabelText("Yeni şifre tekrar"), { target: { value: "new-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi Değiştir" }));

    expect(await screen.findByText("Mevcut şifre hatalı.")).toBeInTheDocument();
    expect(screen.queryByText(/stack trace/i)).not.toBeInTheDocument();
    expect(mocks.clearSession).not.toHaveBeenCalled();
  });
});
