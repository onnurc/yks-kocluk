import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminDashboardPage } from "../pages/admin/AdminDashboardPage";
import { AdminUsersPage } from "../pages/admin/AdminUsersPage";
import { AdminCoachesPage } from "../pages/admin/AdminCoachesPage";
import { AdminSessionsPage } from "../pages/admin/AdminSessionsPage";
import { AdminFinancePage } from "../pages/admin/AdminFinancePage";
import { AdminSafetyPage } from "../pages/admin/AdminSafetyPage";
import { AdminCoachApplicationsPage } from "../pages/admin/AdminCoachApplicationsPage";

const mocks = vi.hoisted(() => ({
  summary: vi.fn(), users: vi.fn(), coaches: vi.fn(), coach: vi.fn(), coachStudents: vi.fn(),
  suspendUser: vi.fn(), activateUser: vi.fn(), sessions: vi.fn(),
  financeSummary: vi.fn(), payments: vi.fn(), subscriptions: vi.fn(), refund: vi.fn(), terminate: vi.fn(),
  reports: vi.fn(), updateReport: vi.fn(), applications: vi.fn(), approve: vi.fn(), reject: vi.fn(),
}));

vi.mock("../admin/adminApi", () => ({ adminApi: {
  summary: mocks.summary, users: mocks.users, coaches: mocks.coaches, coach: mocks.coach,
  coachStudents: mocks.coachStudents, suspendUser: mocks.suspendUser, activateUser: mocks.activateUser,
  sessions: mocks.sessions,
} }));
vi.mock("../safety/financeApi", () => ({ financeApi: {
  summary: mocks.financeSummary, listPayments: mocks.payments, listSubscriptions: mocks.subscriptions,
  refund: mocks.refund, terminateSubscription: mocks.terminate,
} }));
vi.mock("../safety/safetyApi", () => ({ safetyApi: {
  listReports: mocks.reports, updateReportStatus: mocks.updateReport,
} }));
vi.mock("../coachApplications/coachApplicationAdminApi", () => ({ coachApplicationAdminApi: {
  list: mocks.applications, approve: mocks.approve, reject: mocks.reject,
} }));

const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true });
const coach = { id: 7, userId: 70, coachProfileId: 7, name: "Derya Koç", email: "derya@example.com", status: "APPROVED", approvalState: "APPROVED", accountStatus: "ACTIVE" as const, universityId: 4, university: "ODTÜ", department: "Fizik", publiclyVisible: true, createdAt: "2026-01-01T10:00:00Z" };

beforeEach(() => {
  vi.clearAllMocks();
  mocks.summary.mockResolvedValue({ totalStudentCount: 12, totalCoachCount: 5, activeCoachCount: 4, pendingCoachApplicationCount: 2, activeSubscriptionCount: 9, salesThisMonthCount: 6, grossRevenueThisMonth: 12000, refundAmountThisMonth: 1500, netCollectedThisMonth: 10500, openReportCount: 3, scheduledSessionCount: 8, completedSessionCountThisMonth: 10 });
  mocks.sessions.mockResolvedValue(page([])); mocks.users.mockResolvedValue(page([])); mocks.coaches.mockResolvedValue(page([]));
  mocks.financeSummary.mockResolvedValue({ from: null, to: null, grossRevenue: 12000, successfulPaymentCount: 6, failedPaymentCount: 0, pendingPaymentCount: 0, refundTotal: 1500, netCollectedAmount: 10500 });
  mocks.payments.mockResolvedValue(page([])); mocks.subscriptions.mockResolvedValue(page([])); mocks.reports.mockResolvedValue(page([])); mocks.applications.mockResolvedValue(page([]));
});
afterEach(cleanup);

describe("admin management experience", () => {
  it("renders real dashboard summary values and an intentional empty session state", async () => {
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    expect(await screen.findByText("12")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText(/10\.500/)).toBeInTheDocument();
    expect(screen.getByText("Yaklaşan seans veya deneme görüşmesi yok.")).toBeInTheDocument();
    expect(mocks.summary).toHaveBeenCalledTimes(1);
  });

  it("keeps Kullanıcılar limited to the real student directory", async () => {
    mocks.users.mockResolvedValue(page([{ id: 2, name: "Selin Öğrenci", email: "selin@example.com", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, anonymized: false, createdAt: "2026-02-01T10:00:00Z" }]));
    render(<AdminUsersPage />);
    expect(await screen.findByText("Selin Öğrenci")).toBeInTheDocument();
    expect(mocks.users).toHaveBeenCalledWith("STUDENT", "", undefined, 0);
    expect(screen.queryByText("Admin hesapları")).not.toBeInTheDocument();
  });

  it("opens coach detail with batched active students and confirms suspension", async () => {
    mocks.coaches.mockResolvedValue(page([coach])); mocks.coach.mockResolvedValue(coach);
    mocks.coachStudents.mockResolvedValue(page([{ studentId: 22, displayName: "Ece Öğrenci", packageId: 2, packageName: "Mentorluk Paketi", subscriptionStatus: "ACTIVE", subscriptionStart: "2026-01-01T00:00:00Z", subscriptionEnd: "2026-12-31T00:00:00Z", sessionsUsedInCurrentWeek: 1, sessionsRemainingInCurrentWeek: 1, conversationId: 3, nextSession: null }]));
    mocks.suspendUser.mockResolvedValue({ userId: 70, status: "SUSPENDED", reason: "Operasyon" });
    render(<AdminCoachesPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Detay" }));
    expect(await screen.findByText("Ece Öğrenci")).toBeInTheDocument();
    expect(mocks.coachStudents).toHaveBeenCalledWith(7);
    fireEvent.click(screen.getByRole("button", { name: "Koçu Askıya Al" }));
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: "Operasyon" } });
    fireEvent.click(screen.getByRole("button", { name: "Onayla" }));
    await waitFor(() => expect(mocks.suspendUser).toHaveBeenCalledWith(70, "Operasyon"));
  });

  it("distinguishes paid sessions and trials and treats an empty result as valid", async () => {
    mocks.sessions.mockResolvedValueOnce(page([{ id: 9, type: "TRIAL", coachProfileId: 7, coachUserId: 70, coachName: "Derya Koç", studentId: 4, studentName: "Can Öğrenci", startsAt: "2026-09-01T10:00:00Z", endsAt: "2026-09-01T10:30:00Z", status: "CONFIRMED", subscriptionId: null }]));
    const { unmount } = render(<AdminSessionsPage />);
    expect(await screen.findByText("Deneme")).toBeInTheDocument();
    unmount(); mocks.sessions.mockResolvedValue(page([])); render(<AdminSessionsPage />);
    expect(await screen.findByText("Bu filtrelerle eşleşen seans veya deneme görüşmesi yok.")).toBeInTheDocument();
  });

  it("uses backend refund eligibility and never enables an expired payment", async () => {
    mocks.payments.mockResolvedValue(page([
      { id: 1, subscriptionId: 2, studentEmail: "a@b.com", studentFullName: "A Öğrenci", coachFullName: "B Koç", packageName: "Paket", type: "CHARGE", amount: 1000, status: "SUCCESS", providerReference: "p1", createdAt: "2026-08-01T10:00:00Z", succeededAt: "2026-08-01T10:00:00Z", refundedAmount: 0, remainingRefundableAmount: 1000, refundEligible: false, refundDeadline: "2026-08-08T10:00:00Z", refundIneligibleReason: "Satın alma tarihinden itibaren 7 günlük iade süresi doldu." },
      { id: 2, subscriptionId: 3, studentEmail: "c@d.com", studentFullName: "C Öğrenci", coachFullName: "D Koç", packageName: "Paket", type: "CHARGE", amount: 800, status: "SUCCESS", providerReference: "p2", createdAt: "2026-08-22T10:00:00Z", succeededAt: "2026-08-22T10:00:00Z", refundedAmount: 0, remainingRefundableAmount: 800, refundEligible: true, refundDeadline: "2026-08-29T10:00:00Z", refundIneligibleReason: null },
    ]));
    render(<AdminFinancePage />);
    expect(await screen.findByText("Satın alma tarihinden itibaren 7 günlük iade süresi doldu.")).toBeInTheDocument();
    const refundButtons = screen.getAllByRole("button", { name: "İade Et" });
    expect(refundButtons).toHaveLength(2);
    expect(refundButtons[0]).toBeDisabled();
    const refundButton = refundButtons[1];
    fireEvent.click(refundButton); fireEvent.click(screen.getByRole("button", { name: "İadeyi Gerçekleştir" }));
    await waitFor(() => expect(mocks.refund).toHaveBeenCalledWith(2, 800, "Admin paneli - 7 günlük cayma hakkı"));
  });

  it("filters and updates reports through the real status client", async () => {
    const report = { id: 5, reporterUserId: 8, targetType: "USER" as const, targetId: 9, reason: "Uygunsuz davranış", details: "İnceleme gerekli", status: "OPEN" as const, createdAt: "2026-08-20T10:00:00Z", reviewedAt: null, reviewedByAdminId: null };
    mocks.reports.mockResolvedValue(page([report])); mocks.updateReport.mockResolvedValue({ ...report, status: "REVIEWED" });
    render(<AdminSafetyPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Durumu yönet" }));
    fireEvent.click(screen.getByRole("button", { name: "Durumu Güncelle" }));
    await waitFor(() => expect(mocks.updateReport).toHaveBeenCalledWith(5, "REVIEWED"));
  });

  it("preserves explicit coach-application approval confirmation", async () => {
    mocks.applications.mockResolvedValue(page([{ id: 4, fullName: "Aday Koç", email: "aday@example.com", phone: null, experience: "Deneyim", status: "PENDING", createdAt: "2026-08-01T10:00:00Z", reviewedAt: null, reviewNote: null, linkedUserId: null }])); mocks.approve.mockResolvedValue({});
    render(<AdminCoachApplicationsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Onayla" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Onayı doğrula" }));
    await waitFor(() => expect(mocks.approve).toHaveBeenCalledWith(4));
  });
});
