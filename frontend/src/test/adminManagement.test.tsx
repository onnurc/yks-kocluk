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
  summary: vi.fn(), users: vi.fn(), user: vi.fn(), coaches: vi.fn(), coach: vi.fn(), coachStudents: vi.fn(),
  createCoach: vi.fn(), approveCoachProfile: vi.fn(), rejectCoachProfile: vi.fn(),
  suspendUser: vi.fn(), activateUser: vi.fn(), sessions: vi.fn(),
  removeProfileImage: vi.fn(), confirmTrial: vi.fn(),
  packages: vi.fn(), updatePackage: vi.fn(), setPackageActive: vi.fn(), upsertPackageTier: vi.fn(), deletePackageTier: vi.fn(), upsertCampaign: vi.fn(), setCampaignEnabled: vi.fn(), setExamSettings: vi.fn(),
  financeSummary: vi.fn(), payments: vi.fn(), subscriptions: vi.fn(), refundAudit: vi.fn(), refund: vi.fn(), terminate: vi.fn(),
  reports: vi.fn(), updateReport: vi.fn(), applications: vi.fn(), approve: vi.fn(), reject: vi.fn(),
}));

vi.mock("../admin/adminApi", () => ({ adminApi: {
  summary: mocks.summary, users: mocks.users, coaches: mocks.coaches, coach: mocks.coach,
  user: mocks.user,
  createCoach: mocks.createCoach, approveCoachProfile: mocks.approveCoachProfile, rejectCoachProfile: mocks.rejectCoachProfile,
  coachStudents: mocks.coachStudents, suspendUser: mocks.suspendUser, activateUser: mocks.activateUser,
  sessions: mocks.sessions, removeProfileImage: mocks.removeProfileImage, confirmTrial: mocks.confirmTrial,
  packages: mocks.packages, updatePackage: mocks.updatePackage, setPackageActive: mocks.setPackageActive,
  upsertPackageTier: mocks.upsertPackageTier, deletePackageTier: mocks.deletePackageTier,
  upsertCampaign: mocks.upsertCampaign, setCampaignEnabled: mocks.setCampaignEnabled, setExamSettings: mocks.setExamSettings,
} }));
vi.mock("../safety/financeApi", () => ({ financeApi: {
  summary: mocks.financeSummary, listPayments: mocks.payments, listSubscriptions: mocks.subscriptions,
  listRefundAudit: mocks.refundAudit,
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
const pendingCoach = { ...coach, id: 8, userId: 80, coachProfileId: 8, name: "Yeni Koç", email: "yeni@example.com", status: "PENDING", approvalState: "PENDING", publiclyVisible: false };

beforeEach(() => {
  vi.clearAllMocks();
  mocks.summary.mockResolvedValue({ totalStudentCount: 12, totalCoachCount: 5, activeCoachCount: 4, pendingCoachApplicationCount: 2, activeSubscriptionCount: 9, salesThisMonthCount: 6, grossRevenueThisMonth: 12000, refundAmountThisMonth: 1500, netCollectedThisMonth: 10500, openReportCount: 3, scheduledSessionCount: 8, completedSessionCountThisMonth: 10 });
  mocks.sessions.mockResolvedValue(page([])); mocks.users.mockResolvedValue(page([])); mocks.coaches.mockResolvedValue(page([]));
  mocks.financeSummary.mockResolvedValue({ from: null, to: null, grossRevenue: 12000, successfulPaymentCount: 6, failedPaymentCount: 0, pendingPaymentCount: 0, refundTotal: 1500, netCollectedAmount: 10500 });
  mocks.payments.mockResolvedValue(page([])); mocks.subscriptions.mockResolvedValue(page([])); mocks.refundAudit.mockResolvedValue(page([])); mocks.reports.mockResolvedValue(page([])); mocks.applications.mockResolvedValue(page([]));
  mocks.packages.mockResolvedValue({ yksExamYear: 2027, yksExamDate: "2027-06-20", yksExamActive: true, applicableMonthsRemaining: 10, packages: [
    { id:1,packageType:"ONE_MONTH",name:"1 Aylık",basePrice:3000,effectivePrice:3000,active:true,durationMonths:1,applicableMonthsRemaining:null,evaluationMeetingsPerMonth:1,weeklyMeetingsPerMonth:4,totalMeetingsPerMonth:5,campaign:null,priceTiers:[] },
    { id:2,packageType:"THREE_MONTHS",name:"3 Aylık",basePrice:8000,effectivePrice:7000,active:true,durationMonths:3,applicableMonthsRemaining:null,evaluationMeetingsPerMonth:1,weeklyMeetingsPerMonth:4,totalMeetingsPerMonth:5,campaign:{enabled:true,currentlyActive:true,title:"Erken Kayıt",description:null,startsAt:"2026-09-01T00:00:00Z",endsAt:"2026-10-01T00:00:00Z",discountType:"FIXED_AMOUNT",discountValue:1000},priceTiers:[] },
    { id:3,packageType:"UNTIL_EXAM",name:"Sınava Kadar",basePrice:9000,effectivePrice:9000,active:false,durationMonths:null,applicableMonthsRemaining:10,evaluationMeetingsPerMonth:1,weeklyMeetingsPerMonth:4,totalMeetingsPerMonth:5,campaign:null,priceTiers:[{monthsRemaining:10,price:9000}] },
  ] });
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

  it("renders package management, campaign state, and validates price edits", async () => {
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: "Paket Yönetimi" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Erken Kayıt")).toBeInTheDocument();
    const input = screen.getByLabelText("1 Aylık temel fiyatı");
    fireEvent.change(input, { target: { value: "-1" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Fiyatı Kaydet" })[0]);
    expect(screen.getByText("Fiyat sıfırdan büyük olmalıdır.")).toBeInTheDocument();
    expect(mocks.updatePackage).not.toHaveBeenCalled();
  });

  it("renders and updates the YKS exam settings beside the flexible tier table", async () => {
    mocks.setExamSettings.mockResolvedValue(undefined);
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: "YKS Sınav Ayarları" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Sınava Kadar Fiyatlandırma" })).toBeInTheDocument();
    expect(screen.getByText("10 ay kala")).toBeInTheDocument();
    expect(screen.getByLabelText("Aktif YKS yılı")).toHaveValue(2027);
    expect(screen.getByLabelText("Sınav tarihi")).toHaveValue("2027-06-20");
    expect(screen.getByLabelText("Aktif YKS sınavı")).toBeChecked();
    fireEvent.change(screen.getByLabelText("Aktif YKS yılı"), { target: { value: "2028" } });
    fireEvent.change(screen.getByLabelText("Sınav tarihi"), { target: { value: "2028-06-18" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet/Güncelle" }));
    await waitFor(() => expect(mocks.setExamSettings).toHaveBeenCalledWith({ examYear: 2028, examDate: "2028-06-18", active: true }));
  });

  it("keeps Kullanıcılar limited to the real student directory", async () => {
    mocks.users.mockResolvedValue(page([{ id: 2, name: "Selin Öğrenci", email: "selin@example.com", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, anonymized: false, createdAt: "2026-02-01T10:00:00Z" }]));
    render(<AdminUsersPage />);
    expect(await screen.findByText("Selin Öğrenci")).toBeInTheDocument();
    expect(mocks.users).toHaveBeenCalledWith("STUDENT", "", undefined, 0);
    expect(screen.queryByText("Admin hesapları")).not.toBeInTheDocument();
  });

  it("reuses admin media moderation when removing a student profile photo", async () => {
    const user = { id: 2, name: "Selin Öğrenci", email: "selin@example.com", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, anonymized: false, createdAt: "2026-02-01T10:00:00Z" };
    mocks.users.mockResolvedValue(page([user]));
    mocks.user.mockResolvedValue({ user, profileImageUrl: "/api/v1/media/public/test", profileImageAssetId: 44 });
    mocks.removeProfileImage.mockResolvedValue(undefined);
    render(<AdminUsersPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Detay" }));
    expect(await screen.findByAltText("Selin Öğrenci profil fotoğrafı")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Fotoğrafı Kaldır" }));
    fireEvent.change(screen.getByLabelText("Moderasyon gerekçesi"), { target: { value: "Uygunsuz içerik" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaldırmayı Onayla" }));
    await waitFor(() => expect(mocks.removeProfileImage).toHaveBeenCalledWith(44, "Uygunsuz içerik"));
    expect(await screen.findByText("Profil fotoğrafı yok")).toBeInTheDocument();
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

  it("creates a coach without an application and refreshes the directory", async () => {
    mocks.createCoach.mockResolvedValue({ userId: 80, coachProfileId: 8, fullName: "Yeni Koç", email: "yeni@example.com", accountStatus: "ACTIVE", profileStatus: "PENDING" });
    render(<AdminCoachesPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Koç Ekle" }));
    fireEvent.change(screen.getByLabelText("Ad Soyad"), { target: { value: "Yeni Koç" } });
    fireEvent.change(screen.getByLabelText("E-posta"), { target: { value: " Yeni@Example.com " } });
    fireEvent.click(screen.getByRole("button", { name: "Koç Hesabı Oluştur" }));
    await waitFor(() => expect(mocks.createCoach).toHaveBeenCalledWith({ fullName: "Yeni Koç", email: "Yeni@Example.com" }));
    expect(await screen.findByText(/Parola belirleme bağlantısı koça e-posta ile gönderildi/)).toBeInTheDocument();
    await waitFor(() => expect(mocks.coaches.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it("shows malformed manual-coach email directly below its input", async () => {
    render(<AdminCoachesPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Koç Ekle" }));
    fireEvent.change(screen.getByLabelText("Ad Soyad"), { target: { value: "Yeni Koç" } });
    fireEvent.change(screen.getByLabelText("E-posta"), { target: { value: "@gmail.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Koç Hesabı Oluştur" }));

    expect(screen.getByText("Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com", { selector: ".admin-field-error" })).toBeInTheDocument();
    expect(screen.getByLabelText("E-posta")).toHaveAttribute("aria-invalid", "true");
    expect(mocks.createCoach).not.toHaveBeenCalled();
  });

  it("approves a pending coach profile and refreshes the directory", async () => {
    mocks.coaches.mockResolvedValue(page([pendingCoach])); mocks.coach.mockResolvedValue(pendingCoach);
    mocks.coachStudents.mockResolvedValue(page([])); mocks.approveCoachProfile.mockResolvedValue({});
    render(<AdminCoachesPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Detay" }));
    fireEvent.click(await screen.findByRole("button", { name: "Profili Onayla" }));
    fireEvent.click(screen.getByRole("button", { name: "Profil Onayını Doğrula" }));
    await waitFor(() => expect(mocks.approveCoachProfile).toHaveBeenCalledWith(8));
    expect(await screen.findByText("Koç profili onaylandı.")).toBeInTheDocument();
  });

  it("requires a reason when rejecting a pending coach profile", async () => {
    mocks.coaches.mockResolvedValue(page([pendingCoach])); mocks.coach.mockResolvedValue(pendingCoach);
    mocks.coachStudents.mockResolvedValue(page([])); mocks.rejectCoachProfile.mockResolvedValue({});
    render(<AdminCoachesPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Detay" }));
    fireEvent.click(await screen.findByRole("button", { name: "Profili Reddet" }));
    const confirm = screen.getAllByRole("button", { name: "Profili Reddet" })[1];
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Profil red gerekçesi"), { target: { value: "Profil bilgileri eksik" } });
    fireEvent.click(confirm);
    await waitFor(() => expect(mocks.rejectCoachProfile).toHaveBeenCalledWith(8, "Profil bilgileri eksik"));
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
      { id: 1, subscriptionId: 2, studentEmail: "a@b.com", studentFullName: "A Öğrenci", coachFullName: "B Koç", packageName: "Paket", type: "CHARGE", amount: 1000, status: "SUCCESS", providerReference: "p1", createdAt: "2026-08-01T10:00:00Z", succeededAt: "2026-08-01T10:00:00Z", refundedAmount: 0, remainingRefundableAmount: 1000, refundEligible: false, refundDeadline: "2026-08-08T10:00:00Z", refundIneligibleReason: "İade süresi doldu. Ödeme tarihinden itibaren ilk 7 gün içinde iade talebi oluşturabilirsiniz." },
      { id: 2, subscriptionId: 3, studentEmail: "c@d.com", studentFullName: "C Öğrenci", coachFullName: "D Koç", packageName: "Paket", type: "CHARGE", amount: 800, status: "SUCCESS", providerReference: "p2", createdAt: "2026-08-22T10:00:00Z", succeededAt: "2026-08-22T10:00:00Z", refundedAmount: 0, remainingRefundableAmount: 800, refundEligible: true, refundDeadline: "2026-08-29T10:00:00Z", refundIneligibleReason: null },
    ]));
    render(<AdminFinancePage />);
    expect(await screen.findByText("İade süresi doldu. Ödeme tarihinden itibaren ilk 7 gün içinde iade talebi oluşturabilirsiniz.")).toBeInTheDocument();
    const refundButtons = screen.getAllByRole("button", { name: "İade Et" });
    expect(refundButtons).toHaveLength(2);
    expect(refundButtons[0]).toBeDisabled();
    const refundButton = refundButtons[1];
    fireEvent.click(refundButton); fireEvent.click(screen.getByRole("button", { name: "İadeyi Gerçekleştir" }));
    await waitFor(() => expect(mocks.refund).toHaveBeenCalledWith(2, 800, "Admin paneli - uygun iade işlemi"));
  });

  it("shows automatic student refunds as audit history without approval actions", async () => {
    mocks.refundAudit.mockResolvedValue(page([{ id: 4, status: "REFUNDED", requestedAt: "2026-08-28T10:00:00Z", studentName: "Can Öğrenci", coachName: "Derya Koç", packageName: "Aylık Paket", subscriptionId: 12, originalPaymentId: 21, refundPaymentId: 22, amount: 1000, refundedAmount: 1000, currency: "TRY" }]));
    render(<AdminFinancePage />);

    expect(await screen.findByText("Öğrenci İade Geçmişi")).toBeInTheDocument();
    expect(screen.getByText("Can Öğrenci")).toBeInTheDocument();
    expect(screen.getByText("Ödeme #21 → #22")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /iadeyi onayla|iade talebini reddet/i })).not.toBeInTheDocument();
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
