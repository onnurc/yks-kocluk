import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Navigate, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../auth/authTypes";
import type { SessionResponse } from "../booking/bookingTypes";
import { AppLayout } from "../components/AppLayout";
import { BookingsPage } from "../pages/BookingsPage";
import { TestAuthProvider } from "./TestAuthProvider";

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  listMySessions: vi.fn(),
  clearSession: vi.fn(),
  useNotificationSocket: vi.fn(),
}));

vi.mock("../messaging/messagingApi", () => ({ messagingApi: { listConversations: mocks.listConversations } }));
vi.mock("../messaging/useNotificationSocket", () => ({ useNotificationSocket: mocks.useNotificationSocket }));
vi.mock("../booking/bookingApi", () => ({ bookingApi: { listMySessions: mocks.listMySessions } }));
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
            <Route path="/security" element={<Navigate to="/dashboard" replace />} />
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
    const timeFormatter = new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit" });
    const expectedTimeRange = `${timeFormatter.format(new Date(plannedSession.startTime))} – ${timeFormatter.format(new Date(plannedSession.endTime))}`;
    expect(screen.getAllByText(expectedTimeRange)).toHaveLength(3);
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

describe("obsolete authenticated security destination", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([]);
  });

  it.each([
    [student],
    [{ ...student, role: "COACH" as const, fullName: "Ece Koç" }],
  ])("redirects %s away from the removed password-change page", (user) => {
    renderProductPage("/security", user);

    expect(screen.getByText("Dashboard body")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Şifre Değiştir" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Mevcut şifre")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ayarlar" })).not.toBeInTheDocument();
  });
});
