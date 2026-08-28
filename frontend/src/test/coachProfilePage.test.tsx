import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import { CoachProfilePage } from "../public/CoachProfilePage";
import { CoachesPage } from "../public/CoachesPage";
import { PublicLayout } from "../public/PublicLayout";
import { TestAuthProvider } from "./TestAuthProvider";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import { trialConsultationApi } from "../trial/trialConsultationApi";
import { AppRoutes } from "../App";
import type { AuthContextType } from "../auth/AuthContext";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";

vi.mock("../coaches/coachDiscoveryApi", () => ({
  coachDiscoveryApi: { getPublicCoachDetail: vi.fn(), getCoachDetail: vi.fn(), listCoaches: vi.fn(), listPackages: vi.fn() },
}));

vi.mock("../studentDashboard/studentDashboardApi", () => ({
  studentDashboardApi: { getDashboardData: vi.fn() },
}));

vi.mock("../trial/trialConsultationApi", () => ({
  trialConsultationApi: { myTrials: vi.fn(), listCoachTrialAvailability: vi.fn(), request: vi.fn(), cancel: vi.fn() },
}));

vi.mock("../subscriptionCheckout/CheckoutSection", () => ({
  CheckoutSection: ({ coachId, coachName, packageId, packageName, price, dashboardData }: {
    coachId: number;
    coachName: string;
    packageId: number;
    packageName: string;
    price: number;
    dashboardData: StudentDashboardResponse | null;
  }) => {
    const status = dashboardData?.subscription?.status;
    const label = status === "ACTIVE" ? "Aktif Abonelik Mevcut" : status === "PENDING_PAYMENT" ? "Bekleyen Ödeme Mevcut" : "Ödemeye Geç";
    return (
      <div data-testid="checkout-section">
        <span>{`Seçilen checkout: ${coachId} ${coachName} ${packageId} ${packageName} ${price}`}</span>
        <button disabled={status === "ACTIVE" || status === "PENDING_PAYMENT"}>{label}</button>
      </div>
    );
  },
}));

vi.mock("../booking/BookingSection", () => ({
  BookingSection: ({ coachId }: { coachId: number }) => <div>{`Randevu akışı ${coachId}`}</div>,
}));

const detail = {
  id: 42,
  fullName: "Ayşe Yılmaz",
  headline: "Matematik ve çalışma planı mentörü",
  bio: "Öğrencilerin planlı ve sürdürülebilir çalışmasına destek olur.",
  universityName: "Boğaziçi Üniversitesi",
  department: "Matematik",
  graduationYear: 2025,
  tracks: ["NUMERICAL"],
  rating: null,
  totalSessions: 18,
  acceptingNewStudents: true,
  profileImageUrl: null,
  introVideoEmbedUrl: null,
};

const similar = {
  id: 77,
  fullName: "Mert Kaya",
  headline: "Sayısal mentörü",
  universityName: "ODTÜ",
  tracks: ["NUMERICAL"],
  rating: null,
  totalSessions: 2,
  acceptingNewStudents: true,
  profileImageUrl: null,
  introVideoEmbedUrl: null,
};

const page = { content: [detail, similar], page: 0, size: 4, totalElements: 2, totalPages: 1, last: true };

const packages = [
  { id: 5, name: "Başlangıç Paketi", weeklySessions: 1, durationDays: 30, price: 1250 },
  { id: 6, name: "Yoğun Program", weeklySessions: 2, durationDays: 60, price: 2200 },
];

const student = {
  id: 10, email: "student@example.com", fullName: "Öğrenci", role: "STUDENT" as const,
  status: "ACTIVE" as const, emailVerified: true, legalOnboardingCompleted: true, hasLocalPassword: true,
};
const coachUser = { ...student, id: 11, email: "coach@example.com", role: "COACH" as const };
const admin = { ...student, id: 12, email: "admin@example.com", role: "ADMIN" as const };

function dashboard(status: "ACTIVE" | "PENDING_PAYMENT" | null) {
  return {
    user: student,
    subscription: status ? {
      id: 90, status, coachId: 42, coachName: "Ayşe Yılmaz", packageId: 5, packageName: "Başlangıç Paketi",
      startAt: "2026-01-01T00:00:00Z", endAt: "2026-02-01T00:00:00Z", autoRenew: true,
      cancelledAt: null, terminationReason: null,
    } : null,
    payment: null,
  };
}

beforeEach(() => {
  vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue(detail);
  vi.mocked(coachDiscoveryApi.getCoachDetail).mockResolvedValue({ ...detail, userId: 99 });
  vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
  vi.mocked(coachDiscoveryApi.listPackages).mockResolvedValue(packages);
  vi.mocked(studentDashboardApi.getDashboardData).mockResolvedValue(dashboard(null));
  vi.mocked(trialConsultationApi.myTrials).mockResolvedValue([]);
  vi.mocked(trialConsultationApi.listCoachTrialAvailability).mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderRoute(path = "/coaches/42", authValue?: Partial<AuthContextType>) {
  return render(
    <TestAuthProvider value={authValue}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="coaches" element={<CoachesPage />} />
            <Route path="coaches/:id" element={<CoachProfilePage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Public koç profili", () => {
  it("renders real detail data by route id inside the shared layout", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    renderRoute();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
    expect(screen.getByText(/Boğaziçi Üniversitesi/)).toBeInTheDocument();
    expect(screen.getByText("18")).toBeInTheDocument();
    expect(screen.getByText("Öğrencilerin planlı ve sürdürülebilir çalışmasına destek olur.")).toBeInTheDocument();
    expect(coachDiscoveryApi.getPublicCoachDetail).toHaveBeenCalledWith(42);
    expect(coachDiscoveryApi.listCoaches).toHaveBeenCalledWith(0, 4, { track: "NUMERICAL", sort: "newest" });
    expect(screen.getByRole("link", { name: "Mert Kaya profilini incele" })).toHaveAttribute("href", "/coaches/77");
  });

  it("uses fallback media safely and routes trial CTA through registration", async () => {
    renderRoute();

    expect(await screen.findByLabelText("Ayşe Yılmaz için profil fotoğrafı bulunmuyor")).toHaveTextContent("AY");
    expect(screen.getByText("Tanıtım videosu henüz eklenmedi.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Ayşe Yılmaz tanıtım videosu")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ücretsiz Görüşme İçin Kayıt Ol" })).toHaveAttribute("href", "/register?coachId=42");
    expect(screen.queryByText(/9\.8|500\+|Efe Ali/)).not.toBeInTheDocument();
  });

  it("renders a real profile image and the generated privacy-enhanced YouTube embed", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue({
      ...detail,
      profileImageUrl: "https://media.example/profile.jpg",
      introVideoEmbedUrl: "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
    });
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    renderRoute();

    expect(await screen.findByRole("img", { name: "Ayşe Yılmaz profil fotoğrafı" })).toHaveAttribute("src", "https://media.example/profile.jpg");
    const video = screen.getByTitle("Ayşe Yılmaz tanıtım videosu");
    expect(video).toHaveAttribute("src", "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
    expect(video).toHaveAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation");
    expect(video).toHaveAttribute("referrerpolicy", "strict-origin-when-cross-origin");
  });

  it("rejects a non-YouTube embed URL and keeps the clean no-video state", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue({
      ...detail,
      introVideoEmbedUrl: "https://evil.example/embed/dQw4w9WgXcQ",
    });
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    renderRoute();

    expect(await screen.findByText("Tanıtım videosu henüz eklenmedi.")).toBeInTheDocument();
    expect(screen.queryByTitle("Ayşe Yılmaz tanıtım videosu")).not.toBeInTheDocument();
  });

  it("shows generic not-found and retryable service errors", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockRejectedValue(new ApiError(404, "Not found", "özel durum"));
    const missing = renderRoute();
    expect(await screen.findByText("Koç profili bulunamadı.")).toBeInTheDocument();
    expect(screen.queryByText("özel durum")).not.toBeInTheDocument();
    missing.unmount();

    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockRejectedValue(new Error("özel servis hatası"));
    renderRoute();
    expect(await screen.findByText("Profil şu anda görüntülenemiyor.")).toBeInTheDocument();
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue(detail);
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    fireEvent.click(screen.getByRole("button", { name: "Yeniden Dene" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
  });

  it("keeps the public coaches catalogue route functional", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [similar], size: 9 });
    renderRoute("/coaches");
    expect(await screen.findByRole("heading", { level: 2, name: "Mert Kaya" })).toBeInTheDocument();
  });

  it("loads active packages for a student and renders the existing checkout after selection", async () => {
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    expect(await screen.findByRole("heading", { name: "Abonelik Paketleri" })).toBeInTheDocument();
    expect(screen.getByText("Başlangıç Paketi")).toBeInTheDocument();
    expect(screen.getByText("Yoğun Program")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Paketi Seç" })[0]);

    expect(await screen.findByText("Seçilen checkout: 42 Ayşe Yılmaz 5 Başlangıç Paketi 1250")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ödemeye Geç" })).toBeEnabled();
    expect(coachDiscoveryApi.listPackages).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["ACTIVE", "Aktif Abonelik Mevcut"],
    ["PENDING_PAYMENT", "Bekleyen Ödeme Mevcut"],
  ] as const)("keeps %s subscriptions from exposing an actionable checkout", async (status, label) => {
    vi.mocked(studentDashboardApi.getDashboardData).mockResolvedValue(dashboard(status));
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    fireEvent.click((await screen.findAllByRole("button", { name: "Paketi Seç" }))[0]);
    expect(await screen.findByRole("button", { name: label })).toBeDisabled();
  });

  it("keeps messaging and booking available for an eligible subscribed student", async () => {
    vi.mocked(studentDashboardApi.getDashboardData).mockResolvedValue(dashboard("ACTIVE"));
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    expect(await screen.findByRole("button", { name: "Mesaj Gönder" })).toBeInTheDocument();
    expect(screen.getByText("Randevu akışı 42")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ücretsiz Görüşme Planla" })).not.toBeInTheDocument();
  });

  it("keeps authenticated coach reporting available without exposing internal user ids publicly", async () => {
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    fireEvent.click(await screen.findByRole("button", { name: "Koçu Bildir" }));
    expect(screen.getByRole("dialog", { name: "Kullanıcıyı Bildir" })).toBeInTheDocument();
    expect(coachDiscoveryApi.getCoachDetail).toHaveBeenCalledWith(42);
  });

  it.each([
    ["COACH", coachUser],
    ["ADMIN", admin],
  ] as const)("does not expose student purchasing to %s users", async (_role, account) => {
    renderRoute("/coaches/42", { user: account, isAuthenticated: true });
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Abonelik Paketleri" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Paketi Seç" })).not.toBeInTheDocument();
  });

  it("keeps the authenticated trial consultation request flow available", async () => {
    vi.mocked(trialConsultationApi.listCoachTrialAvailability).mockResolvedValue([
      { id: 501, coachProfileId: 42, startTime: "2026-09-10T10:00:00Z", endTime: "2026-09-10T10:30:00Z", booked: false },
    ]);
    vi.mocked(trialConsultationApi.request).mockResolvedValue({
      id: 700, coachProfileId: 42, coachName: "Ayşe Yılmaz", studentId: 10, studentName: "Öğrenci",
      availabilityId: 501, status: "REQUESTED", startsAt: "2026-09-10T10:00:00Z", endsAt: "2026-09-10T10:30:00Z",
      requestedAt: "2026-09-01T10:00:00Z", updatedAt: "2026-09-01T10:00:00Z",
    });
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    const slot = await screen.findByRole("button", { name: /10 Eylül Perşembe.*13:00/i });
    fireEvent.click(slot);
    fireEvent.click(screen.getByRole("button", { name: "Ücretsiz Görüşme Planla" }));
    await waitFor(() => expect(trialConsultationApi.request).toHaveBeenCalledWith({ availabilityId: 501 }));
    expect(await screen.findByText("Durum: Onay bekliyor")).toBeInTheDocument();
  });

  it("shows the clear UTF-8 trial empty state only after availability loads empty", async () => {
    renderRoute("/coaches/42", { user: student, isAuthenticated: true });

    expect(await screen.findByText("Bu koçun şu anda tanımlı uygun deneme görüşmesi saati bulunmuyor.")).toBeInTheDocument();
    expect(trialConsultationApi.listCoachTrialAvailability).toHaveBeenCalledWith(42);
    expect(screen.queryByText(/Ã|Ä|Å|�/)).not.toBeInTheDocument();
  });

  it("the real /coaches/:id application route still renders CoachProfilePage", async () => {
    render(
      <TestAuthProvider>
        <MemoryRouter initialEntries={["/coaches/42"]}><AppRoutes /></MemoryRouter>
      </TestAuthProvider>,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
    expect(coachDiscoveryApi.getPublicCoachDetail).toHaveBeenCalledWith(42);
  });
});
