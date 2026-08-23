/// <reference types="node" />
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "../pages/DashboardPage";

const mocks = vi.hoisted(() => ({
  auth: vi.fn(),
  dashboard: vi.fn(),
  conversations: vi.fn(),
  sessions: vi.fn(),
  coach: vi.fn(),
  coachSummary: vi.fn(),
  coachSessions: vi.fn(),
  coachAvailability: vi.fn(),
  cancelRenewal: vi.fn(),
}));

vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth() }));
vi.mock("../studentDashboard/studentDashboardApi", () => ({
  studentDashboardApi: { getDashboardData: mocks.dashboard },
}));
vi.mock("../messaging/messagingApi", () => ({
  messagingApi: { listConversations: mocks.conversations },
}));
vi.mock("../booking/bookingApi", () => ({
  bookingApi: { listMySessions: mocks.sessions },
}));
vi.mock("../coaches/coachDiscoveryApi", () => ({
  coachDiscoveryApi: { getPublicCoachDetail: mocks.coach },
}));
vi.mock("../coachDashboard/coachDashboardApi", () => ({
  coachDashboardApi: {
    getSummary: mocks.coachSummary,
    getUpcomingSessions: mocks.coachSessions,
    getAvailability: mocks.coachAvailability,
  },
}));
vi.mock("../subscriptionManagement/subscriptionManagementApi", () => ({
  subscriptionManagementApi: { cancelRenewal: mocks.cancelRenewal },
}));

const student = {
  id: 71,
  email: "selin@example.com",
  fullName: "Selin Gerçek",
  role: "STUDENT" as const,
  status: "ACTIVE" as const,
  emailVerified: true,
  legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

const subscription = {
  id: 41,
  status: "ACTIVE" as const,
  coachId: 9,
  coachName: "Backend Koç",
  packageId: 5,
  packageName: "Sınav Odaklı 3 Ay",
  startAt: "2029-01-01T00:00:00Z",
  endAt: "2030-01-01T00:00:00Z",
  autoRenew: true,
  cancelledAt: null,
  terminationReason: null,
};

const dashboardResponse = { user: student, subscription, payment: null };

const conversation = {
  id: 18,
  studentName: student.fullName,
  coachProfileId: 9,
  coachName: "Derya Koç",
  lastMessage: "Gerçek programını haftaya göre güncelledim.",
  lastMessageAt: "2029-05-12T09:30:00Z",
  unreadCount: 5,
  counterpartUserId: 91,
  counterpartOnline: true,
  observer: { type: "ADMIN" as const, displayName: "Platform Yöneticisi", readOnly: true as const },
};

const upcomingSession = {
  id: 81,
  coachProfileId: 12,
  coachName: "Mert Seans Koçu",
  studentName: student.fullName,
  availabilityId: 33,
  status: "PLANNED" as const,
  startTime: "2030-10-12T10:00:00Z",
  endTime: "2030-10-12T11:00:00Z",
  meetLink: "https://meet.example/session-81",
};

const coachDetail = {
  id: 9,
  fullName: "Dr. Derya Gerçek",
  headline: "YKS mentoru",
  bio: "",
  universityName: "Boğaziçi Üniversitesi",
  department: "Psikoloji",
  graduationYear: 2024,
  tracks: ["EQUAL_WEIGHT"],
  rating: 4.9,
  totalSessions: 42,
  acceptingNewStudents: false,
  profileImageUrl: "https://cdn.example/coach-9.jpg",
  introVideoUrl: null,
};

const Location = () => <output data-testid="location">{useLocation().pathname}</output>;

function renderDashboard(path = "/dashboard") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/dashboard" element={<><DashboardPage /><Location /></>} />
        <Route path="/messages" element={<><span>Mesajlar rotası</span><Location /></>} />
        <Route path="/messages/:conversationId" element={<><span>Konuşma rotası</span><Location /></>} />
        <Route path="/bookings" element={<span>Randevular rotası</span>} />
        <Route path="/coaches/:id" element={<span>Koç profili rotası</span>} />
        <Route path="/admin" element={<><span>Admin rotası</span><Location /></>} />
        <Route path="/suspended" element={<span>Askıya alınmış hesap</span>} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(cleanup);

describe("responsive student dashboard", () => {
  beforeEach(() => {
    mocks.coachSummary.mockResolvedValue({ activeStudentCount: 0, completedSessionsThisMonth: 0, upcomingSessionCount: 0, unreadMessageCount: 0, availabilityConfigured: false, nextSession: null, pendingTrialConsultationCount: 0 });
    mocks.coachSessions.mockResolvedValue({ content: [], page: 0, size: 4, totalElements: 0, totalPages: 0, last: true });
    mocks.coachAvailability.mockResolvedValue([]);
    vi.clearAllMocks();
    mocks.auth.mockReturnValue({ user: student, isSuspended: false });
    mocks.dashboard.mockResolvedValue(dashboardResponse);
    mocks.conversations.mockResolvedValue([conversation]);
    mocks.sessions.mockResolvedValue([upcomingSession]);
    mocks.coach.mockResolvedValue(coachDetail);
    mocks.cancelRenewal.mockResolvedValue({});
  });

  it("renders the student route with authenticated and real API/client data", async () => {
    renderDashboard();

    expect(screen.getByText("Tekrar hoş geldin, Selin.")).toBeInTheDocument();
    expect(await screen.findByText("Dr. Derya Gerçek")).toBeInTheDocument();
    expect(screen.getByText("Boğaziçi Üniversitesi · Psikoloji")).toBeInTheDocument();
    expect(screen.getByText("Sınav Odaklı 3 Ay")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "5 okunmamış mesaj, Mesajlara git" })).toBeInTheDocument();
    expect(screen.getByText("Gerçek programını haftaya göre güncelledim.")).toBeInTheDocument();
    expect(screen.getByText("Mert Seans Koçu")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mert Seans Koçu ile görüşmeye katıl" })).toHaveAttribute("href", upcomingSession.meetLink);

    expect(mocks.dashboard).toHaveBeenCalledOnce();
    expect(mocks.coach).toHaveBeenCalledWith(subscription.coachId);
    expect(mocks.conversations).toHaveBeenCalledOnce();
    expect(mocks.sessions).toHaveBeenCalledOnce();
  });

  it("routes the messages CTA to the existing messages page", async () => {
    renderDashboard();
    const messagesCta = await screen.findByRole("link", { name: "5 okunmamış mesaj, Mesajlara git" });
    fireEvent.click(messagesCta);
    expect(screen.getByTestId("location")).toHaveTextContent("/messages");
  });

  it("keeps real subscription data and actions while using the Uniform management surface", async () => {
    mocks.dashboard.mockResolvedValue({
      ...dashboardResponse,
      payment: { id: 52, status: "SUCCESS", amount: 2450, createdAt: "2029-01-01T00:00:00Z" },
    });
    const { container } = renderDashboard();

    fireEvent.click(await screen.findByRole("button", { name: "Aboneliği yönet" }));

    const management = await screen.findByRole("article", { name: "Aktif Koçluk Aboneliği" });
    expect(management).toHaveClass("subscription-management");
    expect(management).not.toHaveAttribute("style");
    expect(within(management).getByText(subscription.coachName)).toBeInTheDocument();
    expect(within(management).getByText(subscription.packageName)).toBeInTheDocument();
    expect(within(management).getByText("₺2.450,00")).toBeInTheDocument();
    const coachProfileAction = within(management).getByRole("link", { name: "Koç Profilini Gör" });
    const meetingsAction = within(management).getByRole("link", { name: "Görüşmelerim" });
    const messagesAction = within(management).getByRole("link", { name: "Mesajlarım" });
    expect(coachProfileAction).toHaveAttribute("href", `/coaches/${subscription.coachId}`);
    expect(meetingsAction).toHaveAttribute("href", "/bookings");
    expect(messagesAction).toHaveAttribute("href", "/messages");
    expect([coachProfileAction, meetingsAction, messagesAction].every((action) => action.className === "subscription-management__action")).toBe(true);
    expect(screen.queryByText(/Yönetici Sonlandırması|Yenileme İptali ve Yönetici/i)).not.toBeInTheDocument();
    expect(container.querySelector('[style*="#d4edda"]')).not.toBeInTheDocument();

    fireEvent.click(within(management).getByRole("button", { name: "Yenilemeyi İptal Et" }));
    const dialog = screen.getByRole("dialog", { name: "Otomatik yenilemeyi kapat" });
    expect(dialog).toHaveTextContent(/aboneliğinizi hemen sonlandırmaz/i);
    fireEvent.click(within(dialog).getByRole("button", { name: "Yenilemeyi İptal Et" }));

    await waitFor(() => expect(mocks.cancelRenewal).toHaveBeenCalledWith(subscription.id));
    expect(await screen.findByText(/Otomatik yenileme kapatıldı/)).toBeInTheDocument();
    await waitFor(() => expect(mocks.dashboard).toHaveBeenCalledTimes(2));
  });

  it("opens an actual recent conversation route", async () => {
    renderDashboard();
    fireEvent.click(await screen.findByText(conversation.lastMessage));
    expect(screen.getByTestId("location")).toHaveTextContent("/messages/18");
  });

  it("refreshes unread totals and previews through the existing notification event", async () => {
    renderDashboard();
    expect(await screen.findByRole("link", { name: "5 okunmamış mesaj, Mesajlara git" })).toBeInTheDocument();
    mocks.conversations.mockResolvedValue([{ ...conversation, unreadCount: 2, lastMessage: "Canlı gerçek önizleme" }]);

    await act(async () => {
      window.dispatchEvent(new CustomEvent("message-notification", {
        detail: { type: "NEW_MESSAGE", conversationId: conversation.id, unreadTotal: 2 },
      }));
    });

    expect(await screen.findByRole("link", { name: "2 okunmamış mesaj, Mesajlara git" })).toBeInTheDocument();
    expect(screen.getByText("Canlı gerçek önizleme")).toBeInTheDocument();
  });

  it("shows honest empty states, including zero unread messages", async () => {
    mocks.dashboard.mockResolvedValue({ user: student, subscription: null, payment: null });
    mocks.conversations.mockResolvedValue([]);
    mocks.sessions.mockResolvedValue([]);
    renderDashboard();

    expect(await screen.findByText("Aktif koçunuz yok")).toBeInTheDocument();
    const coachCard = screen.getByRole("article", { name: "Aktif koç" });
    const subscriptionCard = screen.getByRole("article", { name: "Abonelik" });
    expect(within(coachCard).getAllByRole("link", { name: "Koçları keşfet" })).toHaveLength(1);
    expect(within(subscriptionCard).getAllByRole("link", { name: "Koçları keşfet" })).toHaveLength(1);
    expect(within(coachCard).queryByRole("link", { name: "Aktif koç" })).not.toBeInTheDocument();
    expect(within(subscriptionCard).queryByRole("link", { name: "Abonelik" })).not.toBeInTheDocument();
    expect(screen.getByText("Aktif abonelik yok")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "0 okunmamış mesaj, Mesajlara git" })).toBeInTheDocument();
    expect(screen.getByText("Yaklaşan görüşmeniz yok.")).toBeInTheDocument();
    expect(screen.getByText("Henüz mesajınız yok.")).toBeInTheDocument();
    expect(mocks.coach).not.toHaveBeenCalled();
  });

  it("keeps loading and section-level error states safe", async () => {
    mocks.dashboard.mockRejectedValue(new Error("backend details must stay hidden"));
    mocks.conversations.mockRejectedValue(new Error("conversation stack"));
    mocks.sessions.mockRejectedValue(new Error("session stack"));
    renderDashboard();

    expect(screen.getByLabelText("Aktif koç yükleniyor")).toBeInTheDocument();
    expect(await screen.findByText("Koç bilginiz şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.getByText("Abonelik bilginiz şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.getByText("Mesaj bilginiz şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.getByText("Görüşmeleriniz şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.queryByText(/backend details|conversation stack|session stack/i)).not.toBeInTheDocument();
  });

  it("preserves coach content and redirects admin users away from the student body", async () => {
    mocks.auth.mockReturnValue({ user: { ...student, fullName: "Ece Koç", role: "COACH" }, isSuspended: false });
    const coachView = renderDashboard();
    expect(screen.getByRole("heading", { name: "Merhaba, Ece!" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Mesajlar/ })).toHaveAttribute("href", "/messages");
    expect(mocks.dashboard).not.toHaveBeenCalled();
    coachView.unmount();

    mocks.auth.mockReturnValue({ user: { ...student, fullName: "Deniz Admin", role: "ADMIN" }, isSuspended: false });
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/admin"));
    expect(screen.queryByText("Yolculuğuna kaldığın yerden devam et.")).not.toBeInTheDocument();
  });

  it("contains no Stitch demo data or fake mobile product routes in runtime source", () => {
    const dashboardSource = readFileSync(resolve(process.cwd(), "src/pages/DashboardPage.tsx"), "utf8");
    const dashboardStyles = readFileSync(resolve(process.cwd(), "src/pages/student-dashboard.css"), "utf8");
    const appSource = readFileSync(resolve(process.cwd(), "src/App.tsx"), "utf8");
    const layoutSource = readFileSync(resolve(process.cwd(), "src/components/AppLayout.tsx"), "utf8");
    expect(dashboardSource).not.toMatch(/Ahmet|Can K\.|ODTÜ Bilgisayar|Yıllık Premium|Matematik Analizi|Termodinamik/);
    expect(`${dashboardSource}\n${appSource}\n${layoutSource}`).not.toMatch(/Courses|Grades/);
    expect(dashboardSource).not.toMatch(/StudentDashboard(?:Desktop|Mobile)/);
    expect(dashboardStyles).toContain("@media (max-width: 900px)");
    expect(dashboardStyles).toContain("@media (max-width: 720px)");
  });
});
