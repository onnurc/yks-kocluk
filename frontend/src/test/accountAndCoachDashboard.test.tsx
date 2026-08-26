import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../auth/authTypes";
import { AppLayout } from "../components/AppLayout";
import { AccountPage } from "../pages/AccountPage";
import { DashboardPage } from "../pages/DashboardPage";
import { TestAuthProvider } from "./TestAuthProvider";
import { ApiError } from "../api/ApiError";

const mocks = vi.hoisted(() => ({
  getStudentProfile: vi.fn(), updateStudentProfile: vi.fn(),
  getCoachProfile: vi.fn(), updateCoachEducation: vi.fn(), uploadProfileImage: vi.fn(), deleteMedia: vi.fn(),
  getSummary: vi.fn(), getUpcomingSessions: vi.fn(), getAvailability: vi.fn(),
  listConversations: vi.fn(), useNotificationSocket: vi.fn(),
}));

vi.mock("../account/accountApi", () => ({ accountApi: {
  getStudentProfile: mocks.getStudentProfile, updateStudentProfile: mocks.updateStudentProfile,
  getCoachProfile: mocks.getCoachProfile, updateCoachEducation: mocks.updateCoachEducation,
  uploadProfileImage: mocks.uploadProfileImage, deleteMedia: mocks.deleteMedia,
} }));
vi.mock("../coachDashboard/coachDashboardApi", () => ({ coachDashboardApi: {
  getSummary: mocks.getSummary, getUpcomingSessions: mocks.getUpcomingSessions, getAvailability: mocks.getAvailability,
} }));
vi.mock("../messaging/messagingApi", () => ({ messagingApi: { listConversations: mocks.listConversations } }));
vi.mock("../messaging/useNotificationSocket", () => ({ useNotificationSocket: mocks.useNotificationSocket, MESSAGE_NOTIFICATION_EVENT: "message-notification" }));
vi.mock("../legal/useLegalDocuments", () => ({ useLegalDocuments: () => ({ documents: {}, loading: false, error: null, reload: vi.fn() }) }));

const student: CurrentUser = { id: 7, email: "selin@uniform.test", fullName: "Selin Erdem", role: "STUDENT", status: "ACTIVE", emailVerified: true, legalOnboardingCompleted: true, hasLocalPassword: true };
const coach: CurrentUser = { ...student, id: 9, email: "emre@uniform.test", fullName: "Emre Kaya", role: "COACH" };

const studentProfile = { id: 3, userId: 7, fullName: student.fullName, email: student.email, gradeLevel: "12. Sınıf", city: "İzmir", examYear: 2030, yksScoreType: "NUMERICAL" as const, examSession: "AYT" as const, targetUniversity: "Ankara Üniversitesi", targetDepartment: "Tıp", profileImageUrl: null };
const coachProfile = { id: 4, userId: 9, fullName: coach.fullName, email: coach.email, headline: "YKS mentoru", bio: "Gerçek profil", universityId: 11, universityName: "İstanbul Teknik Üniversitesi", department: "Endüstri Mühendisliği", graduationYear: 2024, yksRanking: 2870, status: "APPROVED" as const, rejectionReason: null, tracks: ["NUMERICAL" as const], activeStudentCount: 5, maxStudentCapacity: 10, payoutAccountReady: true, profileImageUrl: null, profileImageAssetId: null, introVideoEmbedUrl: null };

function renderRoute(path: string, user: CurrentUser, element: React.ReactNode) {
  return render(
    <TestAuthProvider value={{ user, isAuthenticated: true }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route element={<Outlet />}>
              <Route element={<Outlet />}>
                <Route path={path} element={element} />
              </Route>
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listConversations.mockResolvedValue([]);
  mocks.getStudentProfile.mockResolvedValue(studentProfile);
  mocks.updateStudentProfile.mockImplementation(async (request) => ({ ...studentProfile, ...request }));
  mocks.getCoachProfile.mockResolvedValue(coachProfile);
  mocks.updateCoachEducation.mockImplementation(async (request) => ({ ...coachProfile, ...request, universityName: request.university }));
  mocks.uploadProfileImage.mockResolvedValue({ id: 42, url: null });
  mocks.deleteMedia.mockResolvedValue(undefined);
  mocks.getSummary.mockResolvedValue({ activeStudentCount: 5, completedSessionsThisMonth: 8, upcomingSessionCount: 2, unreadMessageCount: 6, availabilityConfigured: true, nextSession: null, pendingTrialConsultationCount: 1 });
  mocks.getUpcomingSessions.mockResolvedValue({ content: [{ id: 71, coachProfileId: 4, coachName: coach.fullName, studentName: "Gerçek Öğrenci", availabilityId: 40, status: "PLANNED", startTime: "2030-09-15T11:00:00Z", endTime: "2030-09-15T12:00:00Z", meetLink: "https://meet.example/real" }], page: 0, size: 4, totalElements: 1, totalPages: 1, last: true });
  mocks.getAvailability.mockResolvedValue([{ id: 81, coachProfileId: 4, startTime: "2030-09-16T15:00:00Z", endTime: "2030-09-16T16:00:00Z", booked: false }]);
});

describe("shared role-aware account", () => {
  it("renders the complete student education form with exact product choices and saves supported fields", async () => {
    const { container } = renderRoute("/account", student, <AccountPage />);
    expect(await screen.findByRole("heading", { name: "Eğitim Hedefleri" })).toBeInTheDocument();
    expect(screen.queryByLabelText("YKS Sıralaması")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Şehir")).not.toBeInTheDocument();
    const grade = screen.getByLabelText("Sınıf Düzeyi");
    expect(grade.tagName).toBe("SELECT");
    expect(within(grade).getAllByRole("option").map((option) => option.textContent)).toEqual(["Seçiniz", "9. Sınıf", "10. Sınıf", "11. Sınıf", "12. Sınıf"]);
    await waitFor(() => expect(screen.getByLabelText("Sınava Gireceğin Yıl")).toHaveValue(2030));
    const scoreType = screen.getByLabelText("YKS Puan Türü");
    expect(within(scoreType).getAllByRole("option").map((option) => option.textContent)).toEqual(["Seçiniz", "Eşit Ağırlık (EA)", "Sayısal (SAY)", "Sözel (SÖZ)", "Yabancı Dil (DİL)"]);
    const examSession = screen.getByLabelText("Sınav Oturumu");
    expect(within(examSession).getAllByRole("option").map((option) => option.textContent)).toEqual(["Seçiniz", "TYT (Temel Yeterlilik Testi)", "AYT (Alan Yeterlilik Testi)", "YDT (Yabancı Dil Testi)"]);
    expect(screen.getByDisplayValue(student.fullName)).toHaveAttribute("readonly");
    expect(screen.getByDisplayValue(student.email)).toHaveAttribute("readonly");
    expect(screen.getByDisplayValue(student.fullName).closest("label")).toHaveClass("account-page__readonly-field");
    expect(screen.getByDisplayValue(student.email).closest("label")).toHaveClass("account-page__readonly-field");
    expect(container.querySelector(".account-page__card")).toBeInTheDocument();
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(within(screen.getByRole("navigation", { name: "Ürün navigasyonu" })).getByRole("link", { name: "Hesabım" })).toHaveClass("is-active");
    fireEvent.change(screen.getByLabelText("Sınıf Düzeyi"), { target: { value: "11. Sınıf" } });
    fireEvent.change(screen.getByLabelText("Sınava Gireceğin Yıl"), { target: { value: "2031" } });
    fireEvent.change(screen.getByLabelText("YKS Puan Türü"), { target: { value: "EQUAL_WEIGHT" } });
    fireEvent.change(screen.getByLabelText("Sınav Oturumu"), { target: { value: "TYT" } });
    fireEvent.change(screen.getByLabelText("Hedef Üniversite"), { target: { value: "Boğaziçi Üniversitesi" } });
    fireEvent.change(screen.getByLabelText("Hedef Bölüm"), { target: { value: "Psikoloji" } });
    fireEvent.click(screen.getByRole("button", { name: "Bilgileri Kaydet" }));
    await waitFor(() => expect(mocks.updateStudentProfile).toHaveBeenCalledWith({ gradeLevel: "11. Sınıf", city: "İzmir", examYear: 2031, yksScoreType: "EQUAL_WEIGHT", examSession: "TYT", targetUniversity: "Boğaziçi Üniversitesi", targetDepartment: "Psikoloji" }));
    expect(screen.queryByText("İzmir")).not.toBeInTheDocument();
  });

  it("restores the last server-backed student education values", async () => {
    renderRoute("/account", student, <AccountPage />);
    const targetUniversity = await screen.findByLabelText("Hedef Üniversite");
    fireEvent.change(targetUniversity, { target: { value: "Değiştirildi" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri Geri Al" }));
    expect(targetUniversity).toHaveValue("Ankara Üniversitesi");
  });

  it("refreshes the shared account profile after upload so the shell uses the saved image", async () => {
    mocks.getStudentProfile
      .mockResolvedValueOnce(studentProfile)
      .mockResolvedValueOnce({ ...studentProfile, profileImageUrl: "/api/v1/public/media/42", profileImageAssetId: 42 });
    const { container } = renderRoute("/account", student, <AccountPage />);
    await screen.findByRole("heading", { name: "Eğitim Hedefleri" });

    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(["profile"], "profile.png", { type: "image/png" });
    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => expect(mocks.uploadProfileImage).toHaveBeenCalledWith(file));
    await waitFor(() => expect(container.querySelector('.app-layout__identity img[src="/api/v1/public/media/42"]')).toBeInTheDocument());
    expect(mocks.getStudentProfile).toHaveBeenCalledTimes(2);
  });

  it("accepts backend-compatible profile images up to 5 MB and rejects larger files locally", async () => {
    const { container } = renderRoute("/account", student, <AccountPage />);
    await screen.findByRole("heading", { name: "Eğitim Hedefleri" });
    expect(screen.getByText(/En fazla 5 MB/)).toBeInTheDocument();
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]')!;

    const valid = new File([new Uint8Array(3 * 1024 * 1024)], "profile.webp", { type: "image/webp" });
    fireEvent.change(fileInput, { target: { files: [valid] } });
    await waitFor(() => expect(mocks.uploadProfileImage).toHaveBeenCalledWith(valid));

    mocks.uploadProfileImage.mockClear();
    const oversized = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "large.png", { type: "image/png" });
    fireEvent.change(fileInput, { target: { files: [oversized] } });
    expect(await screen.findByRole("alert")).toHaveTextContent("5 MB’ı geçmemeli");
    expect(mocks.uploadProfileImage).not.toHaveBeenCalled();
  });

  it("maps backend media error codes to safe contextual Turkish feedback", async () => {
    mocks.uploadProfileImage.mockRejectedValueOnce(
      new ApiError(400, "Bad Request", "internal detail", "MEDIA_TYPE_NOT_ALLOWED"),
    );
    const { container } = renderRoute("/account", coach, <AccountPage />);
    await screen.findByRole("heading", { name: "Eğitim Bilgilerim" });
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    fireEvent.change(fileInput, { target: { files: [new File(["image"], "profile.png", { type: "image/png" })] } });

    expect(await screen.findByRole("alert")).toHaveTextContent("Bu dosya türü desteklenmiyor");
    expect(screen.queryByText("internal detail")).not.toBeInTheDocument();
  });

  it("opens an in-app photo removal confirmation and cancel leaves the saved image untouched", async () => {
    mocks.getStudentProfile.mockResolvedValue({ ...studentProfile, profileImageUrl: "/api/v1/public/media/42", profileImageAssetId: 42 });
    const nativeConfirm = vi.spyOn(window, "confirm");
    renderRoute("/account", student, <AccountPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Fotoğrafı Kaldır" }));
    const dialog = screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" });
    expect(within(dialog).getByText("Profil fotoğrafınızı kaldırmak istediğinize emin misiniz?")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "İptal" })).toHaveFocus();
    expect(nativeConfirm).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole("button", { name: "İptal" }));
    expect(screen.queryByRole("dialog", { name: "Profil Fotoğrafını Kaldır" })).not.toBeInTheDocument();
    expect(mocks.deleteMedia).not.toHaveBeenCalled();
  });

  it("confirms removal once, closes the modal, and returns the shell to initials", async () => {
    mocks.getStudentProfile.mockResolvedValue({ ...studentProfile, profileImageUrl: "/api/v1/public/media/42", profileImageAssetId: 42 });
    let finishRemoval: (() => void) | undefined;
    mocks.deleteMedia.mockImplementationOnce(() => new Promise<void>((resolve) => { finishRemoval = resolve; }));
    const { container } = renderRoute("/account", student, <AccountPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Fotoğrafı Kaldır" }));
    const dialog = screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" });
    const confirm = within(dialog).getByRole("button", { name: "Fotoğrafı Kaldır" });
    fireEvent.click(confirm);
    await waitFor(() => expect(confirm).toBeDisabled());
    fireEvent.click(confirm);
    expect(mocks.deleteMedia).toHaveBeenCalledTimes(1);
    expect(mocks.deleteMedia).toHaveBeenCalledWith(42);

    act(() => finishRemoval?.());
    await waitFor(() => expect(mocks.deleteMedia).toHaveBeenCalledWith(42));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Profil Fotoğrafını Kaldır" })).not.toBeInTheDocument());
    expect(container.querySelector(".app-layout__identity img")).not.toBeInTheDocument();
    expect(container.querySelector(".app-layout__identity .app-layout__avatar")).toHaveTextContent("SE");
  });

  it("falls back to initials when a stale stored image URL cannot load", async () => {
    mocks.getStudentProfile.mockResolvedValue({ ...studentProfile, profileImageUrl: "https://stub-storage.invalid/profile.png", profileImageAssetId: 42 });
    const { container } = renderRoute("/account", student, <AccountPage />);

    const accountImage = await screen.findByAltText("Profil fotoğrafı");
    fireEvent.error(accountImage);

    expect(container.querySelector(".account-page__avatar img")).not.toBeInTheDocument();
    expect(container.querySelector(".account-page__avatar")).toHaveTextContent("SE");
  });

  it("does not offer coach photo removal without a removable saved asset", async () => {
    renderRoute("/account", coach, <AccountPage />);

    expect(await screen.findByRole("heading", { name: "Eğitim Bilgilerim" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Fotoğraf Yükle" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Fotoğrafı Kaldır" })).not.toBeInTheDocument();
  });

  it("renders the saved coach image and opens the shared removal modal without deleting on cancel", async () => {
    mocks.getCoachProfile.mockResolvedValue({ ...coachProfile, profileImageUrl: "/api/v1/public/media/61", profileImageAssetId: 61 });
    const { container } = renderRoute("/account", coach, <AccountPage />);

    expect(await screen.findByAltText("Profil fotoğrafı")).toHaveAttribute("src", "/api/v1/public/media/61");
    expect(container.querySelector('.app-layout__identity img[src="/api/v1/public/media/61"]')).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Fotoğrafı Kaldır" }));
    const dialog = screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" });
    expect(within(dialog).getByText("Profil fotoğrafınızı kaldırmak istediğinize emin misiniz?")).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("button", { name: "İptal" }));
    expect(screen.queryByRole("dialog", { name: "Profil Fotoğrafını Kaldır" })).not.toBeInTheDocument();
    expect(mocks.deleteMedia).not.toHaveBeenCalled();
    expect(container.querySelector('.account-page__avatar img[src="/api/v1/public/media/61"]')).toBeInTheDocument();
  });

  it("removes the coach image through the existing media flow and keeps reload state image-free", async () => {
    mocks.getCoachProfile.mockResolvedValue({ ...coachProfile, profileImageUrl: "/api/v1/public/media/61", profileImageAssetId: 61 });
    const view = renderRoute("/account", coach, <AccountPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Fotoğrafı Kaldır" }));
    const dialog = screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Fotoğrafı Kaldır" }));

    await waitFor(() => expect(mocks.deleteMedia).toHaveBeenCalledWith(61));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Profil Fotoğrafını Kaldır" })).not.toBeInTheDocument());
    expect(view.container.querySelector(".account-page__avatar img")).not.toBeInTheDocument();
    expect(view.container.querySelector(".account-page__avatar")).toHaveTextContent("EK");
    expect(view.container.querySelector(".app-layout__identity img")).not.toBeInTheDocument();
    expect(view.container.querySelector(".app-layout__identity .app-layout__avatar")).toHaveTextContent("EK");
    expect(screen.queryByRole("button", { name: "Fotoğrafı Kaldır" })).not.toBeInTheDocument();

    view.unmount();
    mocks.getCoachProfile.mockResolvedValue({ ...coachProfile, profileImageUrl: null, profileImageAssetId: null });
    renderRoute("/account", coach, <AccountPage />);
    expect(await screen.findByRole("heading", { name: "Eğitim Bilgilerim" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Fotoğrafı Kaldır" })).not.toBeInTheDocument();
    expect(screen.queryByAltText("Profil fotoğrafı")).not.toBeInTheDocument();
  });

  it("keeps the coach removal modal and its error visible when deletion fails", async () => {
    mocks.getCoachProfile.mockResolvedValue({ ...coachProfile, profileImageUrl: "/api/v1/public/media/61", profileImageAssetId: 61 });
    mocks.deleteMedia.mockRejectedValueOnce(new Error("storage unavailable"));
    renderRoute("/account", coach, <AccountPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Fotoğrafı Kaldır" }));
    const dialog = screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Fotoğrafı Kaldır" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Profil fotoğrafı kaldırılamadı. Lütfen yeniden deneyin.");
    expect(screen.getByRole("dialog", { name: "Profil Fotoğrafını Kaldır" })).toBeInTheDocument();
    expect(screen.getByAltText("Profil fotoğrafı")).toHaveAttribute("src", "/api/v1/public/media/61");
  });

  it("renders an editable coach university text field, saves it through the real client, and resets server values", async () => {
    renderRoute("/account", coach, <AccountPage />);
    expect(await screen.findByRole("heading", { name: "Eğitim Bilgilerim" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Eğitim Hedefleri" })).not.toBeInTheDocument();
    const coachNavigation = within(screen.getByRole("navigation", { name: "Ürün navigasyonu" }));
    expect(coachNavigation.getByRole("link", { name: "Hesabım" })).toHaveAttribute("href", "/account");
    expect(coachNavigation.queryByRole("link", { name: "Koçlar" })).not.toBeInTheDocument();
    expect(coachNavigation.queryByRole("link", { name: "Görüşmeler" })).not.toBeInTheDocument();
    const university = screen.getByLabelText("Üniversite");
    expect(university.tagName).toBe("INPUT");
    expect(university).toHaveAttribute("type", "text");
    await waitFor(() => expect(university).toHaveValue("İstanbul Teknik Üniversitesi"));
    expect(screen.getByLabelText("Bölüm")).toHaveValue("Endüstri Mühendisliği");
    const ranking = screen.getByLabelText("YKS Sıralaması");
    expect(ranking).toHaveValue(2870);
    fireEvent.change(university, { target: { value: "Boğaziçi Üniversitesi" } });
    fireEvent.change(ranking, { target: { value: "3200" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri Geri Al" }));
    expect(university).toHaveValue("İstanbul Teknik Üniversitesi");
    expect(ranking).toHaveValue(2870);
    fireEvent.change(university, { target: { value: "Orta Doğu Teknik Üniversitesi" } });
    fireEvent.change(ranking, { target: { value: "3100" } });
    fireEvent.click(screen.getByRole("button", { name: "Bilgileri Kaydet" }));
    await waitFor(() => expect(mocks.updateCoachEducation).toHaveBeenCalledWith({ university: "Orta Doğu Teknik Üniversitesi", department: "Endüstri Mühendisliği", yksRanking: 3100 }));
  });

  it("loads a newly approved coach's real pending profile shell instead of failing the account", async () => {
    mocks.getCoachProfile.mockResolvedValue({ ...coachProfile, headline: null, bio: null, universityId: null, universityName: null, department: null, graduationYear: null, yksRanking: null, status: "PENDING", tracks: [] });
    renderRoute("/account", coach, <AccountPage />);
    expect(await screen.findByRole("heading", { name: "Eğitim Bilgilerim" })).toBeInTheDocument();
    expect(screen.getByLabelText("Üniversite")).toHaveValue("");
    expect(screen.getByLabelText("Bölüm")).toHaveValue("");
    expect(screen.getByLabelText("YKS Sıralaması")).toHaveValue(null);
    expect(screen.queryByText("Hesap bilgileriniz şu anda alınamadı.")).not.toBeInTheDocument();
  });

  it("shows a safe loading failure instead of raw API details", async () => {
    mocks.getStudentProfile.mockRejectedValue(new Error("database stack trace"));
    renderRoute("/account", student, <AccountPage />);
    expect(await screen.findByText("Hesap bilgileriniz şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.queryByText(/database stack trace/i)).not.toBeInTheDocument();
  });
});

describe("real coach dashboard", () => {
  it("uses the existing coach dashboard, session, availability and message contracts", async () => {
    const { container } = renderRoute("/dashboard", coach, <DashboardPage />);
    expect(await screen.findByRole("heading", { name: "Merhaba, Emre!" })).toBeInTheDocument();
    expect(mocks.getSummary).toHaveBeenCalledTimes(1);
    expect(mocks.getUpcomingSessions).toHaveBeenCalledTimes(1);
    expect(mocks.getAvailability).toHaveBeenCalledTimes(1);
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("Gerçek Öğrenci")).toBeInTheDocument();
    expect(screen.getByText(/16 Eyl/i)).toBeInTheDocument();
    expect(container.querySelector(".coach-dashboard__messages")).toHaveAttribute("href", "/messages");
    expect(screen.getByRole("link", { name: "Gerçek Öğrenci ile görüşmeye katıl" })).toHaveAttribute("href", "https://meet.example/real");
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(container.querySelector(".coach-dashboard__messages")).toBeInTheDocument();
  });

  it("does not render a join action without a real meeting link", async () => {
    mocks.getUpcomingSessions.mockResolvedValue({ content: [{ id: 72, coachProfileId: 4, coachName: coach.fullName, studentName: "Bağlantısız Öğrenci", availabilityId: 41, status: "PLANNED", startTime: "2030-09-17T11:00:00Z", endTime: "2030-09-17T12:00:00Z", meetLink: null }], page: 0, size: 4, totalElements: 1, totalPages: 1, last: true });
    renderRoute("/dashboard", coach, <DashboardPage />);
    expect(await screen.findByText("Bağlantısız Öğrenci")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /görüşmeye katıl/ })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Görüşme bağlantısı henüz hazır değil")).toBeInTheDocument();
  });

  it("renders legitimate zero metrics and successful empty collections without failure UI", async () => {
    mocks.getSummary.mockResolvedValue({ activeStudentCount: 0, completedSessionsThisMonth: 0, upcomingSessionCount: 0, unreadMessageCount: 0, availabilityConfigured: false, nextSession: null, pendingTrialConsultationCount: 0 });
    mocks.getUpcomingSessions.mockResolvedValue({ content: [], page: 0, size: 4, totalElements: 0, totalPages: 0, last: true });
    mocks.getAvailability.mockResolvedValue([]);
    renderRoute("/dashboard", coach, <DashboardPage />);
    expect(await screen.findByText("Yaklaşan görüşmeniz yok.")).toBeInTheDocument();
    expect(screen.getByText("Henüz müsaitlik saati eklenmemiş.")).toBeInTheDocument();
    expect(screen.getAllByText("0").length).toBeGreaterThanOrEqual(4);
    expect(screen.queryByText("Panel özeti alınamadı.")).not.toBeInTheDocument();
    expect(screen.queryByText("Görüşmeler şu anda alınamadı.")).not.toBeInTheDocument();
    expect(screen.queryByText("Müsaitlik saatleri alınamadı.")).not.toBeInTheDocument();
  });

  it("keeps genuine request failures distinct and updates unread from the existing notification event", async () => {
    const view = renderRoute("/dashboard", coach, <DashboardPage />);
    expect(await screen.findByText("Gerçek Öğrenci")).toBeInTheDocument();
    act(() => window.dispatchEvent(new CustomEvent("message-notification", { detail: { unreadTotal: 9 } })));
    expect(view.container.querySelector(".coach-dashboard__messages strong")).toHaveTextContent("9");
    view.unmount();

    mocks.getSummary.mockRejectedValue(new Error("summary stack"));
    mocks.getUpcomingSessions.mockRejectedValue(new Error("session stack"));
    mocks.getAvailability.mockRejectedValue(new Error("availability stack"));
    renderRoute("/dashboard", coach, <DashboardPage />);
    expect(await screen.findByText(/Panel özeti alınamadı/)).toBeInTheDocument();
    expect(screen.getByText("Görüşmeler şu anda alınamadı.")).toBeInTheDocument();
    expect(screen.getByText("Müsaitlik saatleri alınamadı.")).toBeInTheDocument();
    expect(screen.queryByText(/stack/)).not.toBeInTheDocument();
  });

  it("contains no Stitch demo values, fake course navigation, yellow shell, or bottom bar", () => {
    const dashboardSource = readFileSync(resolve(process.cwd(), "src/pages/CoachDashboardPage.tsx"), "utf8");
    const layoutSource = readFileSync(resolve(process.cwd(), "src/components/AppLayout.tsx"), "utf8");
    expect(dashboardSource).not.toMatch(/24 active|24 Aktif|Kariyer Planlama|Üniversite Hazırlık - Matematik|Can Yılmaz|Zeynep Kaya/i);
    expect(layoutSource).not.toMatch(/Courses|Grades|bottom.navigation/i);
    expect(layoutSource).not.toMatch(/yellow|#ffff00|#ffd700/i);
  });
});
